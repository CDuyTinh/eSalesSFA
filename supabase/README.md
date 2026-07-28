# Supabase — Cách dựng backend

## Chạy lần đầu

Vào **Dashboard → SQL Editor → New query**, paste và Run **theo đúng thứ tự**:

| # | File | Nội dung |
|---|---|---|
| 1 | `migrations/0001_init_schema.sql` | 24 bảng MVP + sequence versioning + trigger |
| 2 | `migrations/0002_rls_policies.sql` | Row Level Security |
| 3 | `seed.sql` | 200 KH · 300 SP · 15 CTKM · 60 ngày lịch sử đơn |

File `seed.sql` kết thúc bằng một câu `SELECT` đếm số dòng — nhìn kết quả để xác nhận
dữ liệu vào đủ.

## Tạo user đăng nhập

RLS chặn mọi thứ dựa trên `auth.uid()`, nên phải nối user với bản ghi `salespersons`:

1. **Authentication → Users → Add user** → tạo user với email `sales01@demo.local` (nhớ mật khẩu).
2. Copy `id` của user vừa tạo.
3. Chạy trong SQL Editor:

```sql
UPDATE salespersons
SET user_id = '<uuid-cua-user-vua-tao>'
WHERE code = 'NV001';
```

Không làm bước này thì `current_salesperson_id()` trả `NULL`, và **mọi query sẽ trả về rỗng**
— đây là lỗi dễ gây hoang mang nhất khi mới dựng RLS.

## Kiểm tra RLS hoạt động

Trong SQL Editor bạn đang chạy với quyền `postgres` nên RLS **bị bỏ qua** — không test được ở đây.
Cách kiểm tra đúng: gọi REST bằng publishable key kèm access token của user.

```bash
# Lấy access token
curl -X POST 'https://<ref>.supabase.co/auth/v1/token?grant_type=password' \
  -H "apikey: <publishable-key>" \
  -H "Content-Type: application/json" \
  -d '{"email":"sales01@demo.local","password":"<mat-khau>"}'

# Query customers -> phải trả về đúng KH thuộc tuyến của NV001, không phải cả 200
curl 'https://<ref>.supabase.co/rest/v1/customers?select=code,name&limit=5' \
  -H "apikey: <publishable-key>" \
  -H "Authorization: Bearer <access-token>"
```

## Làm lại từ đầu

```sql
-- ⚠️ XOÁ TOÀN BỘ DỮ LIỆU. Chỉ dùng khi muốn reset môi trường dev.
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres, anon, authenticated, service_role;
```

Sau đó chạy lại 3 file theo thứ tự trên.

## Ghi chú thiết kế

- **Tiền lưu bằng `bigint`** (đơn vị đồng), không dùng `numeric`/`float`. Xem lý do ở
  `DATABASE_SCHEMA.md` mục "Ba cái bẫy".
- **Bảng master** có `row_version`/`is_deleted`/`updated_at` + trigger tự tăng version.
  **Bảng giao dịch** thì không — client không tải chúng xuống, chỉ đẩy lên.
- **PK bảng giao dịch không có `DEFAULT gen_random_uuid()`** — id do client sinh khi offline.
  Đây là điều kiện để sync idempotent hoạt động.
- **Không bao giờ `DELETE`** bản ghi master, chỉ `is_deleted = true`. Client offline không có
  cách nào biết một dòng đã bị xoá khỏi server.
