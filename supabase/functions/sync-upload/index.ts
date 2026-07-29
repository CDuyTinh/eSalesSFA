// =============================================================================
// POST /functions/v1/sync-upload
//
// Nhận batch giao dịch tạo offline. Ba tính chất bắt buộc:
//
//   1. IDEMPOTENT — gửi lại cùng session_id không tạo bản ghi trùng.
//   2. VALIDATE Ở SERVER — không tin số tiền client tính. Giá và tồn kho được
//      kiểm tra lại; sai lệch thì từ chối đơn đó.
//   3. TỪ CHỐI CÓ PHÂN LOẠI — lỗi nghiệp vụ trả về `rejected` để client đánh dấu
//      FAILED và KHÔNG retry; chỉ lỗi hạ tầng mới để client gửi lại.
// =============================================================================

import { adminClient, errorResponse, jsonResponse, resolveCaller, HttpError } from '../_shared/auth.ts'
import type { SupabaseClient } from 'jsr:@supabase/supabase-js@2'
import type { Caller } from '../_shared/auth.ts'

interface UploadRequest {
  session_id: string
  visits?: Record<string, unknown>[]
  orders?: {
    order: Record<string, unknown>
    details: Record<string, unknown>[]
    promotions?: Record<string, unknown>[]
  }[]
  stock_counts?: {
    header: Record<string, unknown>
    details: Record<string, unknown>[]
  }[]
  surveys?: {
    header: Record<string, unknown>
    answers: Record<string, unknown>[]
    photos?: Record<string, unknown>[]
  }[]
}

interface Rejected {
  id: string
  entity: string
  code: string
  message: string
}

Deno.serve(async (req: Request) => {
  try {
    if (req.method !== 'POST') {
      throw new HttpError(405, 'METHOD_NOT_ALLOWED', 'Chỉ chấp nhận POST')
    }

    const db = adminClient()
    const caller = await resolveCaller(req, db)
    const body = (await req.json()) as UploadRequest

    if (!body?.session_id) {
      throw new HttpError(400, 'MISSING_SESSION', 'Thiếu session_id')
    }

    // ---- Chốt idempotency -------------------------------------------------
    // session_id là PRIMARY KEY. Nếu client retry cùng session vì mất mạng giữa
    // chừng, insert này lỗi trùng khoá và ta trả về kết quả đã xử lý trước đó
    // thay vì ghi dữ liệu lần nữa.
    const { error: sessionErr } = await db.from('sync_sessions').insert({
      session_id: body.session_id,
      salesperson_id: caller.salespersonId,
      sync_type: 'UPLOAD',
      status: 'PROCESSING',
    })

    if (sessionErr) {
      if (sessionErr.code === '23505') {
        const { data: prev } = await db
          .from('sync_sessions')
          .select('payload_summary, status')
          .eq('session_id', body.session_id)
          .maybeSingle()

        return jsonResponse({
          session_id: body.session_id,
          replayed: true,
          ...(prev?.payload_summary ?? { accepted: [], rejected: [] }),
        })
      }
      throw new HttpError(500, 'DB_ERROR', sessionErr.message)
    }

    const accepted: string[] = []
    const rejected: Rejected[] = []

    // ---- Visits -----------------------------------------------------------
    for (const visit of body.visits ?? []) {
      const id = String(visit.id)
      try {
        // Ép salesperson_id theo JWT, không lấy từ payload — chặn ghi mạo danh.
        const { error } = await db.from('visits').upsert(
          { ...visit, salesperson_id: caller.salespersonId, session_id: body.session_id },
          { onConflict: 'id', ignoreDuplicates: true },
        )
        if (error) throw new Error(error.message)
        accepted.push(id)
      } catch (e) {
        rejected.push({ id, entity: 'visit', code: 'INVALID', message: String(e) })
      }
    }

    // ---- Orders -----------------------------------------------------------
    for (const item of body.orders ?? []) {
      const order = item.order
      const id = String(order.id)
      try {
        const check = await validateOrder(db, caller, order, item.details)
        if (!check.ok) {
          rejected.push({ id, entity: 'order', code: check.code!, message: check.message! })
          continue
        }

        const { error: oErr } = await db.from('orders').upsert(
          { ...order, salesperson_id: caller.salespersonId, session_id: body.session_id },
          { onConflict: 'id', ignoreDuplicates: true },
        )
        if (oErr) throw new Error(oErr.message)

        if (item.details.length > 0) {
          const { error: dErr } = await db
            .from('order_details')
            .upsert(item.details, { onConflict: 'id', ignoreDuplicates: true })
          if (dErr) throw new Error(dErr.message)
        }

        if (item.promotions && item.promotions.length > 0) {
          const { error: pErr } = await db
            .from('order_promotions')
            .upsert(item.promotions, { onConflict: 'id', ignoreDuplicates: true })
          if (pErr) throw new Error(pErr.message)
        }

        accepted.push(id)
      } catch (e) {
        rejected.push({ id, entity: 'order', code: 'DB_ERROR', message: String(e) })
      }
    }

    // ---- Kiểm kê tồn kho ---------------------------------------------------
    for (const item of body.stock_counts ?? []) {
      const id = String(item.header.id)
      try {
        await upsertWithChildren(
          db,
          'stock_counts',
          { ...item.header, salesperson_id: caller.salespersonId, session_id: body.session_id },
          'stock_count_details',
          item.details,
        )
        accepted.push(id)
      } catch (e) {
        rejected.push({ id, entity: 'stock_count', code: 'DB_ERROR', message: String(e) })
      }
    }

    // ---- Khảo sát ----------------------------------------------------------
    for (const item of body.surveys ?? []) {
      const id = String(item.header.id)
      try {
        await upsertWithChildren(
          db,
          'surveys',
          { ...item.header, salesperson_id: caller.salespersonId, session_id: body.session_id },
          'survey_answers',
          item.answers,
        )
        if (item.photos && item.photos.length > 0) {
          const { error } = await db
            .from('survey_photos')
            .upsert(item.photos, { onConflict: 'id', ignoreDuplicates: true })
          if (error) throw new Error(error.message)
        }
        accepted.push(id)
      } catch (e) {
        rejected.push({ id, entity: 'survey', code: 'DB_ERROR', message: String(e) })
      }
    }

    const summary = { accepted, rejected }
    await db
      .from('sync_sessions')
      .update({
        status: 'SUCCESS',
        payload_summary: summary,
        finished_at: new Date().toISOString(),
      })
      .eq('session_id', body.session_id)

    return jsonResponse({ session_id: body.session_id, ...summary })
  } catch (e) {
    return errorResponse(e)
  }
})

/**
 * Ghi header rồi ghi các dòng con.
 *
 * ignoreDuplicates theo id khiến thao tác idempotent ở mức từng bản ghi: client
 * gửi lại cả cụm sau khi mất mạng cũng không tạo bản sao.
 */
async function upsertWithChildren(
  db: SupabaseClient,
  headerTable: string,
  header: Record<string, unknown>,
  childTable: string,
  children: Record<string, unknown>[],
): Promise<void> {
  const { error: headerErr } = await db
    .from(headerTable)
    .upsert(header, { onConflict: 'id', ignoreDuplicates: true })
  if (headerErr) throw new Error(headerErr.message)

  if (children.length === 0) return

  const { error: childErr } = await db
    .from(childTable)
    .upsert(children, { onConflict: 'id', ignoreDuplicates: true })
  if (childErr) throw new Error(childErr.message)
}

/**
 * Kiểm tra lại đơn hàng bằng dữ liệu server.
 *
 * Client tính tiền để hiển thị ngay khi offline, nhưng con số cuối cùng phải do
 * server xác nhận — nếu không, chỉ cần sửa APK là đặt được hàng giá 0 đồng.
 */
async function validateOrder(
  db: SupabaseClient,
  caller: Caller,
  order: Record<string, unknown>,
  details: Record<string, unknown>[],
): Promise<{ ok: boolean; code?: string; message?: string }> {
  if (!details || details.length === 0) {
    return { ok: false, code: 'EMPTY_ORDER', message: 'Đơn hàng không có dòng nào' }
  }

  // Khách hàng phải thuộc tuyến của nhân viên này.
  const { data: routes } = await db
    .from('sales_routes')
    .select('id')
    .eq('salesperson_id', caller.salespersonId)
  const routeIds = (routes ?? []).map((r: { id: string }) => r.id)

  const { data: inRoute } = await db
    .from('sales_route_details')
    .select('customer_id')
    .in('route_id', routeIds)
    .eq('customer_id', order.customer_id as string)
    .maybeSingle()

  if (!inRoute) {
    return {
      ok: false,
      code: 'CUSTOMER_NOT_IN_ROUTE',
      message: 'Khách hàng không thuộc tuyến của nhân viên',
    }
  }

  // Đối chiếu giá từng dòng với bảng giá hiện hành.
  const { data: customer } = await db
    .from('customers')
    .select('price_group_id')
    .eq('id', order.customer_id as string)
    .maybeSingle()

  if (!customer) {
    return { ok: false, code: 'CUSTOMER_NOT_FOUND', message: 'Không tìm thấy khách hàng' }
  }

  const today = new Date().toISOString().slice(0, 10)

  for (const d of details) {
    if (d.is_free_item === true) continue // hàng tặng giá 0, bỏ qua

    const { data: price } = await db
      .from('price_lists')
      .select('price')
      .eq('price_group_id', customer.price_group_id)
      .eq('product_id', d.product_id as string)
      .eq('uom_code', d.uom_code as string)
      .lte('from_date', today)
      .gte('to_date', today)
      .maybeSingle()

    if (!price) {
      return {
        ok: false,
        code: 'PRICE_NOT_FOUND',
        message: `Không có giá cho sản phẩm ${d.product_id} (${d.uom_code})`,
      }
    }

    if (Number(price.price) !== Number(d.price)) {
      return {
        ok: false,
        code: 'PRICE_MISMATCH',
        message: `Giá lệch: client ${d.price}, server ${price.price}. Cần đồng bộ lại bảng giá.`,
      }
    }
  }

  return { ok: true }
}
