import { createClient, SupabaseClient } from 'jsr:@supabase/supabase-js@2'

/**
 * Client dùng service_role — bỏ qua RLS.
 *
 * An toàn vì key này chỉ tồn tại trong biến môi trường của Edge Function, không
 * bao giờ rời khỏi server. Mọi kiểm tra quyền phải tự làm thủ công ở tầng này.
 */
export function adminClient(): SupabaseClient {
  return createClient(
    Deno.env.get('SUPABASE_URL')!,
    Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,
    { auth: { persistSession: false } },
  )
}

export interface Caller {
  userId: string
  salespersonId: string
  branchId: string | null
  role: string
}

/**
 * Xác định nhân viên đang gọi từ JWT.
 *
 * Supabase đã verify chữ ký JWT trước khi function chạy, nên ở đây chỉ cần đọc
 * `sub` rồi tra ngược sang bảng salespersons. Ném lỗi nếu user chưa được nối với
 * hồ sơ nhân viên — đây là lỗi cấu hình hay gặp nhất khi mới dựng hệ thống.
 */
export async function resolveCaller(req: Request, db: SupabaseClient): Promise<Caller> {
  const authHeader = req.headers.get('Authorization')
  if (!authHeader?.startsWith('Bearer ')) {
    throw new HttpError(401, 'MISSING_TOKEN', 'Thiếu Authorization header')
  }

  const token = authHeader.slice('Bearer '.length)
  const { data: userData, error: userErr } = await db.auth.getUser(token)
  if (userErr || !userData.user) {
    throw new HttpError(401, 'INVALID_TOKEN', 'Token không hợp lệ hoặc đã hết hạn')
  }

  const { data: sp, error: spErr } = await db
    .from('salespersons')
    .select('id, branch_id, role')
    .eq('user_id', userData.user.id)
    .eq('is_deleted', false)
    .maybeSingle()

  if (spErr) throw new HttpError(500, 'DB_ERROR', spErr.message)
  if (!sp) {
    throw new HttpError(
      403,
      'NO_SALESPERSON',
      'User chưa được nối với hồ sơ nhân viên. Chạy: ' +
        `UPDATE salespersons SET user_id = '${userData.user.id}' WHERE code = 'NV001';`,
    )
  }

  return {
    userId: userData.user.id,
    salespersonId: sp.id,
    branchId: sp.branch_id,
    role: sp.role,
  }
}

export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message)
  }
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function errorResponse(e: unknown): Response {
  if (e instanceof HttpError) {
    return jsonResponse({ code: e.code, message: e.message }, e.status)
  }
  console.error('Unhandled error', e)
  return jsonResponse({ code: 'INTERNAL', message: String(e) }, 500)
}
