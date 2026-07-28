# Edge Functions — tầng API nghiệp vụ

App **không** gọi thẳng PostgREST (`/rest/v1/*`). Mọi dữ liệu nghiệp vụ đi qua các
function ở đây, nên app không biết gì về cấu trúc bảng và mọi kiểm tra quan trọng
chạy ở server.

```
App ──Retrofit──> /functions/v1/sync-download ──┐
                  /functions/v1/sync-upload   ──┴──> Postgres (service_role)

App ──supabase-kt──> /auth/v1/*      (đăng nhập, JWT)
                     /storage/v1/*   (upload ảnh)
```

## Endpoint

| Method | Path | Việc |
|---|---|---|
| POST | `/functions/v1/sync-download` | Kéo master data thay đổi kể từ mốc version client giữ |
| POST | `/functions/v1/sync-upload` | Đẩy batch giao dịch offline lên, idempotent |

## Vì sao Auth và Storage vẫn đi thẳng Supabase

- **Auth** — tự viết lại login/refresh token chỉ để "có API riêng" là việc vô nghĩa và
  dễ sai về bảo mật. Supabase Auth cấp JWT, Edge Function verify JWT đó.
- **Storage** — đẩy file nhị phân qua Edge Function vừa chậm vừa chạm giới hạn payload.
  Ảnh đi thẳng lên bucket, bảo vệ bằng Storage policy.

## Ba tính chất của `sync-upload`

1. **Idempotent.** `session_id` là PRIMARY KEY của `sync_sessions`. Client retry vì mất
   mạng giữa chừng sẽ dính lỗi trùng khoá `23505`, và server trả về kết quả đã xử lý
   lần trước thay vì ghi lại. Không có cơ chế này thì mỗi lần rớt mạng là một đơn trùng.

2. **Không tin số liệu client.** Function tra lại `price_lists` và so với giá client gửi.
   Lệch là từ chối. Nếu bỏ qua bước này, chỉ cần sửa APK là đặt được hàng giá 0 đồng.

3. **`salesperson_id` lấy từ JWT, không lấy từ payload.** Client không thể ghi đơn mang
   tên nhân viên khác dù có sửa request.

## Deploy

Cần [Supabase CLI](https://supabase.com/docs/guides/cli).

```bash
npm install -g supabase          # hoặc: scoop install supabase

supabase login
supabase link --project-ref kvlzyuhvhwzmdvocyhnr

supabase functions deploy sync-download
supabase functions deploy sync-upload
```

`SUPABASE_URL` và `SUPABASE_SERVICE_ROLE_KEY` được Supabase tự cấp cho function khi chạy
— **không** cần khai báo, và **không bao giờ** đặt service_role key vào app Android.

## Test bằng curl

```bash
REF=kvlzyuhvhwzmdvocyhnr
PUB=<publishable-key>

# 1. Lấy access token
TOKEN=$(curl -s -X POST "https://$REF.supabase.co/auth/v1/token?grant_type=password" \
  -H "apikey: $PUB" -H "Content-Type: application/json" \
  -d '{"email":"sales01@demo.local","password":"<mat-khau>"}' | jq -r .access_token)

# 2. Sync lần đầu — versions rỗng nghĩa là tải toàn bộ
curl -s -X POST "https://$REF.supabase.co/functions/v1/sync-download" \
  -H "apikey: $PUB" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"session_id":"'$(uuidgen)'","versions":{},"page_size":100}' | jq 'keys, .tables | keys'

# 3. Sync lần hai với mốc version đã nhận -> phải trả về rất ít hoặc rỗng
```

## Chạy local (tuỳ chọn)

```bash
supabase start                       # cần Docker
supabase functions serve sync-download --env-file supabase/.env.local
```

## Lỗi hay gặp

| Triệu chứng | Nguyên nhân |
|---|---|
| 401 `MISSING_TOKEN` | Thiếu header `Authorization: Bearer` |
| 403 `NO_SALESPERSON` | User chưa nối với `salespersons.user_id` — xem `supabase/README.md` |
| `tables` trả về rỗng | Chưa chạy `seed.sql`, hoặc nhân viên chưa có tuyến nào |
| 401 trước cả khi function chạy | Thiếu header `apikey` |
