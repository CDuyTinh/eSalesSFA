// =============================================================================
// POST /functions/v1/sync-download
//
// Kéo dữ liệu master thay đổi kể từ mốc version client đang giữ.
// Trả nhiều bảng trong MỘT response để tránh hàng chục round-trip qua 3G.
// =============================================================================

import { adminClient, errorResponse, jsonResponse, resolveCaller, HttpError } from '../_shared/auth.ts'
import type { SupabaseClient } from 'jsr:@supabase/supabase-js@2'
import type { Caller } from '../_shared/auth.ts'

/**
 * Bảng client được phép tải, kèm cách giới hạn dữ liệu theo nhân viên.
 *
 * Danh sách này là whitelist: client gửi tên bảng lạ sẽ bị bỏ qua, không thể
 * dùng endpoint để dò bảng khác trong database.
 */
/**
 * Trần cứng của PostgREST trên Supabase (`db-max-rows`). Không thể vượt bằng
 * `.limit()`; muốn tải hết thì client phải lặp cho tới khi `has_more = false`.
 */
const MAX_ROWS_PER_REQUEST = 1000

/** Số ngày lịch sử đơn hàng tải về máy, đủ cho dashboard tháng và báo cáo. */
const HISTORY_DAYS = 90

function historyFromDate(): string {
  const d = new Date()
  d.setDate(d.getDate() - HISTORY_DAYS)
  return d.toISOString().slice(0, 10)
}

type Scope = 'ALL' | 'BY_SALESPERSON' | 'BY_ROUTE' | 'BY_ORDER'

const SYNC_TABLES: Record<string, Scope> = {
  app_configs: 'ALL',
  uoms: 'ALL',
  branches: 'ALL',
  channels: 'ALL',
  price_groups: 'ALL',
  reason_codes: 'ALL',
  product_categories: 'ALL',
  products: 'ALL',
  product_uoms: 'ALL',
  price_lists: 'ALL',
  promotion_programs: 'ALL',
  promotion_breaks: 'ALL',
  promotion_items: 'ALL',
  survey_types: 'ALL',
  survey_question_groups: 'ALL',
  survey_questions: 'ALL',
  survey_question_options: 'ALL',
  salespersons: 'BY_SALESPERSON',
  customers: 'BY_ROUTE',
  sales_routes: 'BY_SALESPERSON',
  sales_route_details: 'BY_ROUTE',
  // Lịch sử đơn hàng tải ngược về máy để dashboard và báo cáo đọc được khi
  // offline. Đơn do chính client tạo cũng quay về — vô hại vì upsert theo id.
  orders: 'BY_SALESPERSON',
  order_details: 'BY_ORDER',
}

interface DownloadRequest {
  session_id: string
  versions: Record<string, number>
  page_size?: number
}

Deno.serve(async (req: Request) => {
  try {
    if (req.method !== 'POST') {
      throw new HttpError(405, 'METHOD_NOT_ALLOWED', 'Chỉ chấp nhận POST')
    }

    const db = adminClient()
    const caller = await resolveCaller(req, db)
    const body = (await req.json()) as DownloadRequest

    if (!body?.session_id) {
      throw new HttpError(400, 'MISSING_SESSION', 'Thiếu session_id')
    }

    // PostgREST của Supabase chặn cứng 1000 dòng mỗi request (db-max-rows), bất
    // kể .limit() đặt bao nhiêu. Nếu để pageSize > 1000 thì phép kiểm tra
    // `rows.length >= pageSize` bên dưới không bao giờ đúng, và server sẽ báo
    // has_more = false trong khi dữ liệu đã bị cắt cụt — client mất dữ liệu mà
    // không hề biết. Cắt trần đúng bằng giới hạn thật của PostgREST.
    const pageSize = Math.min(body.page_size ?? MAX_ROWS_PER_REQUEST, MAX_ROWS_PER_REQUEST)
    const versions = body.versions ?? {}

    await db.from('sync_sessions').upsert({
      session_id: body.session_id,
      salesperson_id: caller.salespersonId,
      sync_type: 'DOWNLOAD',
      status: 'PROCESSING',
    })

    const tables: Record<string, unknown> = {}
    let hasMore = false

    // Các bảng độc lập nhau nên tải song song.
    await Promise.all(
      Object.entries(SYNC_TABLES).map(async ([table, scope]) => {
        const since = versions[table] ?? 0
        const rows = await fetchChanges(db, table, scope, caller, since, pageSize)

        // Không có thay đổi thì bỏ hẳn khỏi response, đỡ tốn băng thông.
        if (rows.length === 0) return

        const maxVersion = rows.reduce(
          (max: number, r: Record<string, unknown>) => Math.max(max, Number(r.row_version)),
          since,
        )
        // Lấy đủ một trang nghĩa là rất có thể còn nữa -> client gọi lại với
        // max_version mới. Cùng lắm thừa một lượt gọi trả về rỗng.
        if (rows.length >= pageSize) hasMore = true

        tables[table] = {
          rows: rows.filter((r: Record<string, unknown>) => !r.is_deleted),
          deleted_ids: rows
            .filter((r: Record<string, unknown>) => r.is_deleted)
            .map((r: Record<string, unknown>) => String(r.id ?? r.code)),
          max_version: maxVersion,
        }
      }),
    )

    await db
      .from('sync_sessions')
      .update({ status: 'SUCCESS', finished_at: new Date().toISOString() })
      .eq('session_id', body.session_id)

    return jsonResponse({
      session_id: body.session_id,
      tables,
      has_more: hasMore,
      server_time: new Date().toISOString(),
    })
  } catch (e) {
    return errorResponse(e)
  }
})

async function fetchChanges(
  db: SupabaseClient,
  table: string,
  scope: Scope,
  caller: Caller,
  since: number,
  limit: number,
): Promise<Record<string, unknown>[]> {
  let q = db
    .from(table)
    .select('*')
    .gt('row_version', since)
    .order('row_version', { ascending: true })
    .limit(limit)

  if (scope === 'BY_SALESPERSON') {
    q = table === 'salespersons'
      ? q.eq('id', caller.salespersonId)
      : q.eq('salesperson_id', caller.salespersonId)

    // Lịch sử đơn chỉ lấy trong cửa sổ báo cáo; tải toàn bộ lịch sử nhiều năm
    // vào SQLite của điện thoại là vô nghĩa.
    if (table === 'orders') q = q.gte('order_date', historyFromDate())
  } else if (scope === 'BY_ORDER') {
    // Chi tiết đơn theo các đơn của nhân viên này. Giới hạn theo cùng cửa sổ
    // thời gian với đơn để không kéo về hàng chục nghìn dòng lịch sử cũ.
    const { data: orders } = await db
      .from('orders')
      .select('id')
      .eq('salesperson_id', caller.salespersonId)
      .gte('order_date', historyFromDate())
    const orderIds = (orders ?? []).map((o: { id: string }) => o.id)
    if (orderIds.length === 0) return []
    q = q.in('order_id', orderIds)
  } else if (scope === 'BY_ROUTE') {
    // Chỉ khách hàng và tuyến thuộc nhân viên này.
    const { data: routes } = await db
      .from('sales_routes')
      .select('id')
      .eq('salesperson_id', caller.salespersonId)
    const routeIds = (routes ?? []).map((r: { id: string }) => r.id)

    if (routeIds.length === 0) return []

    if (table === 'sales_route_details') {
      q = q.in('route_id', routeIds)
    } else if (table === 'customers') {
      const { data: dets } = await db
        .from('sales_route_details')
        .select('customer_id')
        .in('route_id', routeIds)
      const customerIds = [...new Set((dets ?? []).map((d: { customer_id: string }) => d.customer_id))]
      if (customerIds.length === 0) return []
      q = q.in('id', customerIds)
    }
  }

  const { data, error } = await q
  if (error) throw new HttpError(500, 'DB_ERROR', `${table}: ${error.message}`)
  return (data ?? []) as Record<string, unknown>[]
}
