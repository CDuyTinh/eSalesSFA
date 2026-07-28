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
type Scope = 'ALL' | 'BY_SALESPERSON' | 'BY_ROUTE'

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
  salespersons: 'BY_SALESPERSON',
  customers: 'BY_ROUTE',
  sales_routes: 'BY_SALESPERSON',
  sales_route_details: 'BY_ROUTE',
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

    const pageSize = Math.min(body.page_size ?? 1000, 5000)
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
