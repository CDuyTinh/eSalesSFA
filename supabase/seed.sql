-- =============================================================================
-- eSalesSFA — Seed data
-- Chạy SAU 0001 và 0002.
--
-- Sinh ra:
--   200 khách hàng có toạ độ thật rải quanh TP.HCM (để test bán kính check-in)
--   300 sản phẩm × 3 đơn vị tính × 4 nhóm giá
--   15 chương trình khuyến mãi đủ 5 loại
--   ~60 ngày lịch sử đơn hàng (để dashboard có dữ liệu khi demo)
--
-- ⚠️ Demo mà dữ liệu rỗng thì công sức 10 tuần mất phần lớn giá trị.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. CẤU HÌNH
-- -----------------------------------------------------------------------------
INSERT INTO app_configs (code, value, data_type, description) VALUES
  ('CHECKIN_RADIUS_M',          '100',  'INT',     'Bán kính cho phép check-in (mét)'),
  ('GPS_MAX_ACCURACY_M',        '50',   'INT',     'Accuracy lớn hơn giá trị này thì chặn check-in'),
  ('CHECKIN_VALIDATE_TYPE',     '1',    'INT',     '0=tắt, 1=accuracy+distance, 2=chỉ accuracy, 3=chỉ distance'),
  ('MIN_VISIT_MINUTES',         '5',    'INT',     'Thời gian tối thiểu tại điểm bán trước khi cho check-out'),
  ('REQUIRE_STOCK_BEFORE_ORDER','false','BOOL',    'Bắt buộc kiểm kê tồn trước khi đặt hàng'),
  ('TRACE_INTERVAL_SECONDS',    '300',  'INT',     'Chu kỳ ghi điểm GPS tracking'),
  ('MAX_MANUAL_DISCOUNT_PCT',   '0.05', 'DECIMAL', 'Trần chiết khấu tay nhân viên được nhập');

-- -----------------------------------------------------------------------------
-- 2. DANH MỤC
-- -----------------------------------------------------------------------------
INSERT INTO uoms (code, name) VALUES
  ('LE',    'Lẻ'),
  ('LOC',   'Lốc'),
  ('THUNG', 'Thùng');

INSERT INTO branches (code, name, address, latitude, longitude) VALUES
  ('CN01', 'Chi nhánh Hồ Chí Minh', '123 Nguyễn Huệ, Quận 1', 10.774100, 106.703700),
  ('CN02', 'Chi nhánh Bình Dương',  '45 Đại lộ Bình Dương',   11.005600, 106.657300);

INSERT INTO channels (code, name) VALUES
  ('GT',     'General Trade'),
  ('MT',     'Modern Trade'),
  ('HORECA', 'Horeca');

INSERT INTO price_groups (code, name) VALUES
  ('PG_SI',    'Giá sỉ'),
  ('PG_LE',    'Giá lẻ'),
  ('PG_MT',    'Giá siêu thị'),
  ('PG_HORECA','Giá Horeca');

INSERT INTO reason_codes (code, name, apply_for, sort_order) VALUES
  ('OD01', 'Khách hàng dời địa điểm',       'CHECKIN_OVER_DISTANCE', 1),
  ('OD02', 'GPS không chính xác',            'CHECKIN_OVER_DISTANCE', 2),
  ('OD03', 'Gặp khách ngoài cửa hàng',       'CHECKIN_OVER_DISTANCE', 3),
  ('NO01', 'Cửa hàng đóng cửa',              'NO_ORDER',              1),
  ('NO02', 'Còn hàng tồn nhiều',             'NO_ORDER',              2),
  ('NO03', 'Chủ cửa hàng vắng mặt',          'NO_ORDER',              3),
  ('CA01', 'Khách đổi ý',                    'CANCEL_ORDER',          1),
  ('CA02', 'Nhập sai thông tin',             'CANCEL_ORDER',          2),
  ('OR01', 'Khách hàng yêu cầu ghé đột xuất','OUT_OF_ROUTE',          1);

-- -----------------------------------------------------------------------------
-- 3. NHÂN VIÊN
-- -----------------------------------------------------------------------------
-- user_id để NULL. Sau khi tạo user trong Authentication > Users, chạy:
--   UPDATE salespersons SET user_id = '<uuid-cua-user>' WHERE code = 'NV001';
INSERT INTO salespersons (code, full_name, phone, email, branch_id, role)
SELECT 'NV001', 'Nguyễn Văn Sales', '0901000001', 'sales01@demo.local', b.id, 'SALES'
FROM branches b WHERE b.code = 'CN01';

INSERT INTO salespersons (code, full_name, phone, email, branch_id, role)
SELECT 'SUP001', 'Trần Thị Giám Sát', '0901000002', 'sup01@demo.local', b.id, 'SUPERVISOR'
FROM branches b WHERE b.code = 'CN01';

-- -----------------------------------------------------------------------------
-- 4. NGÀNH HÀNG & 300 SẢN PHẨM
-- -----------------------------------------------------------------------------
INSERT INTO product_categories (code, name, sort_order) VALUES
  ('CAT01', 'Nước giải khát', 1),
  ('CAT02', 'Bánh kẹo',       2),
  ('CAT03', 'Sữa & chế phẩm', 3),
  ('CAT04', 'Hoá mỹ phẩm',    4),
  ('CAT05', 'Gia vị',         5);

INSERT INTO products (code, name, name_search, barcode, category_id, base_uom, vat_rate)
SELECT
  'SP' || lpad(i::text, 4, '0'),
  c.name || ' loại ' || i,
  lower(c.name || ' loai ' || i),
  '893' || lpad(i::text, 10, '0'),
  c.id,
  'LE',
  CASE WHEN c.code = 'CAT04' THEN 0.10 ELSE 0.08 END
FROM generate_series(1, 300) AS i
CROSS JOIN LATERAL (
  SELECT id, code, name FROM product_categories
  ORDER BY sort_order OFFSET (i % 5) LIMIT 1
) c;

-- Mỗi SP có 3 đơn vị: Lẻ (1) < Lốc (6) < Thùng (24)
INSERT INTO product_uoms (product_id, uom_code, conversion_rate, is_default_sale, sort_order)
SELECT p.id, u.code, u.rate, u.is_default, u.ord
FROM products p
CROSS JOIN (VALUES
  ('LE',    1.0,  false, 1),
  ('LOC',   6.0,  false, 2),
  ('THUNG', 24.0, true,  3)
) AS u(code, rate, is_default, ord);

-- Giá: mỗi nhóm giá có hệ số riêng. Giá gốc 5.000-25.000đ/lẻ, nhân theo quy đổi.
INSERT INTO price_lists (product_id, price_group_id, uom_code, price, from_date, to_date)
SELECT
  pu.product_id,
  pg.id,
  pu.uom_code,
  round(
    (5000 + (('x' || substr(md5(pu.product_id::text), 1, 8))::bit(32)::bigint % 20000))
    * pu.conversion_rate * pg.factor
  )::bigint,
  current_date - 365,
  '2099-12-31'
FROM product_uoms pu
CROSS JOIN (
  SELECT id, CASE code
    WHEN 'PG_SI'     THEN 0.85
    WHEN 'PG_LE'     THEN 1.00
    WHEN 'PG_MT'     THEN 0.92
    WHEN 'PG_HORECA' THEN 1.08
  END AS factor
  FROM price_groups
) pg;

-- -----------------------------------------------------------------------------
-- 5. 200 KHÁCH HÀNG QUANH TP.HCM
-- -----------------------------------------------------------------------------
-- Toạ độ rải trong bán kính ~12km quanh trung tâm (10.7769, 106.7009).
-- Có toạ độ thật mới test được logic bán kính check-in.
INSERT INTO customers (
  code, name, name_search, phone, address,
  latitude, longitude, channel_id, price_group_id, branch_id, salesperson_id, credit_limit
)
SELECT
  'KH' || lpad(i::text, 4, '0'),
  'Cửa hàng ' || (ARRAY['Minh Anh','Thành Đạt','Hồng Phúc','Tân Tiến','Bình Minh',
                        'Phương Nam','Đại Lợi','Kim Ngân','Thịnh Vượng','An Khang'])[1 + (i % 10)]
    || ' ' || i,
  lower('cua hang ' || i),
  '090' || lpad(i::text, 7, '0'),
  i || ' Đường số ' || (1 + i % 50) || ', Quận ' || (1 + i % 12) || ', TP.HCM',
  10.7769 + (random() - 0.5) * 0.20,
  106.7009 + (random() - 0.5) * 0.20,
  ch.id,
  pg.id,
  b.id,
  sp.id,
  (1 + i % 5) * 10000000
FROM generate_series(1, 200) AS i
CROSS JOIN LATERAL (SELECT id FROM channels     ORDER BY code OFFSET (i % 3) LIMIT 1) ch
CROSS JOIN LATERAL (SELECT id FROM price_groups ORDER BY code OFFSET (i % 4) LIMIT 1) pg
CROSS JOIN LATERAL (SELECT id FROM branches     WHERE code = 'CN01' LIMIT 1) b
CROSS JOIN LATERAL (SELECT id FROM salespersons WHERE code = 'NV001' LIMIT 1) sp;

-- -----------------------------------------------------------------------------
-- 6. TUYẾN VIẾNG THĂM — Thứ 2..Thứ 7, mỗi ngày ~30 khách
-- -----------------------------------------------------------------------------
INSERT INTO sales_routes (code, name, salesperson_id, day_of_week, week_pattern)
SELECT
  'RT' || d,
  'Tuyến thứ ' || (d + 1),
  sp.id,
  d,
  'ALL'
FROM generate_series(2, 7) AS d
CROSS JOIN LATERAL (SELECT id FROM salespersons WHERE code = 'NV001' LIMIT 1) sp;

-- Chia 200 KH vào 6 tuyến theo modulo
INSERT INTO sales_route_details (route_id, customer_id, sort_order)
SELECT r.id, c.id, c.rn
FROM (
  SELECT id, code, row_number() OVER (ORDER BY code) AS rn FROM customers
) c
JOIN sales_routes r
  ON r.day_of_week = 2 + (c.rn % 6);

-- -----------------------------------------------------------------------------
-- 7. 15 CHƯƠNG TRÌNH KHUYẾN MÃI — đủ 5 loại
-- -----------------------------------------------------------------------------
INSERT INTO promotion_programs
  (code, name, promo_type, apply_level, discount_kind, is_auto_apply, is_multi_level,
   priority, from_date, to_date, budget_amount)
VALUES
  -- Chiết khấu bậc theo số lượng
  ('KM01','Mua nhiều giảm sâu - Nước giải khát','QTY_TIER','LINE','PERCENT',true,false,10,current_date-90,current_date+90,NULL),
  ('KM02','Bậc số lượng - Bánh kẹo',            'QTY_TIER','LINE','PERCENT',true,false,10,current_date-60,current_date+60,NULL),
  ('KM03','Bậc số lượng - Sữa',                 'QTY_TIER','LINE','PERCENT',true,false,10,current_date-30,current_date+120,NULL),
  -- Chiết khấu bậc theo giá trị (toàn đơn)
  ('KM04','Đơn từ 5 triệu giảm 3%',             'AMOUNT_TIER','DOCUMENT','PERCENT',true,false,20,current_date-90,current_date+90,500000000),
  ('KM05','Đơn lớn cuối tháng',                 'AMOUNT_TIER','DOCUMENT','PERCENT',true,false,20,current_date-15,current_date+15,200000000),
  -- Hàng tặng
  ('KM06','Mua 10 tặng 1 - Nước ngọt',          'FREE_ITEM','LINE','FREE_ITEM',true,false,5,current_date-90,current_date+90,NULL),
  ('KM07','Mua 20 tặng 3 - Bánh',               'FREE_ITEM','LINE','FREE_ITEM',true,false,5,current_date-45,current_date+45,NULL),
  ('KM08','Tặng quà theo suất - Sữa',           'FREE_ITEM','LINE','FREE_ITEM',false,false,5,current_date-30,current_date+60,NULL),
  -- Combo bộ
  ('KM09','Combo 3 món giảm 10%',               'COMBO_BUNDLE','GROUP','PERCENT',true,false,15,current_date-60,current_date+60,NULL),
  ('KM10','Combo gia đình',                     'COMBO_BUNDLE','GROUP','AMOUNT', true,false,15,current_date-30,current_date+90,100000000),
  -- Cộng dồn nhiều bậc
  ('KM11','Tích luỹ bậc thang',                 'QTY_TIER','LINE','PERCENT',true,true,12,current_date-90,current_date+90,NULL),
  ('KM12','Ưu đãi Horeca',                      'AMOUNT_TIER','DOCUMENT','PERCENT',true,false,25,current_date-90,current_date+90,NULL),
  ('KM13','Khuyến mãi hoá mỹ phẩm',             'QTY_TIER','LINE','AMOUNT',true,false,10,current_date-20,current_date+40,50000000),
  ('KM14','Gia vị mua 12 tặng 2',               'FREE_ITEM','LINE','FREE_ITEM',true,false,5,current_date-10,current_date+80,NULL),
  -- Chiết khấu tay
  ('KM15','Chiết khấu tay (nhân viên nhập)',    'MANUAL','DOCUMENT','PERCENT',false,false,99,current_date-365,'2099-12-31',NULL);

-- Bậc cho các chương trình theo số lượng: 10 / 20 / 50 -> 3% / 5% / 8%
INSERT INTO promotion_breaks (program_id, break_level, min_qty, discount_pct)
SELECT p.id, b.lvl, b.qty, b.pct
FROM promotion_programs p
CROSS JOIN (VALUES (1,10,0.03),(2,20,0.05),(3,50,0.08)) AS b(lvl, qty, pct)
WHERE p.promo_type = 'QTY_TIER' AND p.discount_kind = 'PERCENT';

-- Bậc theo tiền: 5tr / 10tr / 20tr -> 3% / 5% / 7%
INSERT INTO promotion_breaks (program_id, break_level, min_amount, discount_pct)
SELECT p.id, b.lvl, b.amt, b.pct
FROM promotion_programs p
CROSS JOIN (VALUES (1,5000000,0.03),(2,10000000,0.05),(3,20000000,0.07)) AS b(lvl, amt, pct)
WHERE p.promo_type = 'AMOUNT_TIER';

-- Bậc hàng tặng: mua 10 tặng 1, mua 20 tặng 3
INSERT INTO promotion_breaks (program_id, break_level, min_qty, free_qty)
SELECT p.id, b.lvl, b.buy, b.free
FROM promotion_programs p
CROSS JOIN (VALUES (1,10,1),(2,20,3)) AS b(lvl, buy, free)
WHERE p.promo_type = 'FREE_ITEM';

-- Bậc combo: đủ bộ thì giảm
INSERT INTO promotion_breaks (program_id, break_level, min_qty, discount_pct, discount_amount)
SELECT p.id, 1, 1,
       CASE WHEN p.discount_kind = 'PERCENT' THEN 0.10 ELSE 0 END,
       CASE WHEN p.discount_kind = 'AMOUNT'  THEN 50000 ELSE 0 END
FROM promotion_programs p
WHERE p.promo_type = 'COMBO_BUNDLE';

-- Bậc chiết khấu tay: trần 5%
INSERT INTO promotion_breaks (program_id, break_level, min_amount, discount_pct)
SELECT id, 1, 0, 0.05 FROM promotion_programs WHERE promo_type = 'MANUAL';

-- Bậc KM theo số tiền x số lượng
INSERT INTO promotion_breaks (program_id, break_level, min_qty, discount_amount)
SELECT p.id, b.lvl, b.qty, b.amt
FROM promotion_programs p
CROSS JOIN (VALUES (1,10,20000),(2,30,80000)) AS b(lvl, qty, amt)
WHERE p.promo_type = 'QTY_TIER' AND p.discount_kind = 'AMOUNT';

-- Sản phẩm tham gia: mỗi CTKM gắn 20 SP mua
INSERT INTO promotion_items (program_id, product_id, item_role, required_qty, uom_code)
SELECT p.id, pr.id, 'BUY', 0, 'LE'
FROM promotion_programs p
CROSS JOIN LATERAL (
  SELECT id FROM products ORDER BY code OFFSET (abs(hashtext(p.code)) % 200) LIMIT 20
) pr;

-- Sản phẩm tặng cho các CTKM hàng tặng
INSERT INTO promotion_items (program_id, product_id, item_role, required_qty, uom_code, free_stock_qty)
SELECT p.id, pr.id, 'FREE', 0, 'LE', 500
FROM promotion_programs p
CROSS JOIN LATERAL (
  SELECT id FROM products ORDER BY code OFFSET (abs(hashtext(p.code || 'F')) % 280) LIMIT 3
) pr
WHERE p.promo_type = 'FREE_ITEM';

-- Combo bộ: 3 nhóm A/B/C, mỗi nhóm 2 SP, mỗi bộ cần 1 cái
INSERT INTO promotion_items (program_id, product_id, item_role, bundle_group, required_qty, uom_code)
SELECT p.id, pr.id, 'BUY', g.grp, 1, 'LE'
FROM promotion_programs p
CROSS JOIN (VALUES ('A',0),('B',2),('C',4)) AS g(grp, off)
CROSS JOIN LATERAL (
  SELECT id FROM products ORDER BY code OFFSET (abs(hashtext(p.code)) % 200) + g.off LIMIT 2
) pr
WHERE p.promo_type = 'COMBO_BUNDLE';

-- -----------------------------------------------------------------------------
-- 8. LỊCH SỬ 60 NGÀY — visit + đơn hàng, để dashboard/report có dữ liệu
-- -----------------------------------------------------------------------------
-- Mỗi ngày ~12 lượt ghé có phát sinh đơn.
WITH sp AS (SELECT id, branch_id FROM salespersons WHERE code = 'NV001' LIMIT 1),
gen AS (
  SELECT
    gen_random_uuid()                                     AS visit_id,
    gen_random_uuid()                                     AS order_id,
    (current_date - d)                                    AS vdate,
    c.id                                                  AS customer_id,
    d, n
  FROM generate_series(0, 59) AS d
  CROSS JOIN generate_series(1, 12) AS n
  CROSS JOIN LATERAL (
    SELECT id FROM customers ORDER BY code OFFSET ((d * 12 + n) % 200) LIMIT 1
  ) c
)
, ins_visit AS (
  INSERT INTO visits (
    id, customer_id, salesperson_id, visit_date, check_in_at, check_out_at,
    check_in_lat, check_in_lng, check_in_accuracy, check_in_distance,
    duration_minutes, battery_pct, session_id, client_created_at
  )
  SELECT
    g.visit_id, g.customer_id, sp.id, g.vdate,
    g.vdate + time '07:30' + (g.n * interval '35 minutes'),
    g.vdate + time '07:50' + (g.n * interval '35 minutes'),
    10.7769 + (random()-0.5)*0.2, 106.7009 + (random()-0.5)*0.2,
    5 + random()*20, random()*80,
    15 + (random()*20)::int, 40 + (random()*60)::int,
    gen_random_uuid(), g.vdate + time '07:30'
  FROM gen g CROSS JOIN sp
  RETURNING id
)
INSERT INTO orders (
  id, order_no, customer_id, salesperson_id, visit_id, branch_id, order_date,
  status, sub_total, discount_amount, net_amount, vat_amount, total_amount,
  session_id, client_created_at
)
SELECT
  g.order_id,
  'DH' || to_char(g.vdate, 'YYYYMMDD') || lpad(g.n::text, 3, '0'),
  g.customer_id, sp.id, g.visit_id, sp.branch_id, g.vdate,
  'CONFIRMED',
  s.sub,
  round(s.sub * 0.05)::bigint,
  s.sub - round(s.sub * 0.05)::bigint,
  round((s.sub - round(s.sub * 0.05)) * 0.08)::bigint,
  s.sub - round(s.sub * 0.05)::bigint + round((s.sub - round(s.sub * 0.05)) * 0.08)::bigint,
  gen_random_uuid(),
  g.vdate + time '08:00'
FROM gen g
CROSS JOIN sp
CROSS JOIN LATERAL (
  SELECT (2000000 + (random() * 18000000))::bigint AS sub
) s;

-- Dòng chi tiết: mỗi đơn 5 dòng
INSERT INTO order_details (
  id, order_id, line_no, product_id, uom_code, qty, conversion_rate, base_qty,
  price, gross_amount, discount_amount, net_amount, vat_rate, vat_amount, line_amount
)
SELECT
  gen_random_uuid(), o.id, ln,
  p.id, 'THUNG', q.qty, 24.0, q.qty * 24,
  pl.price,
  (q.qty * pl.price)::bigint,
  round(q.qty * pl.price * 0.05)::bigint,
  (q.qty * pl.price - round(q.qty * pl.price * 0.05))::bigint,
  0.08,
  round((q.qty * pl.price - round(q.qty * pl.price * 0.05)) * 0.08)::bigint,
  round((q.qty * pl.price - round(q.qty * pl.price * 0.05)) * 1.08)::bigint
FROM orders o
CROSS JOIN generate_series(1, 5) AS ln
CROSS JOIN LATERAL (
  SELECT id FROM products ORDER BY code OFFSET (abs(hashtext(o.order_no || ln::text)) % 295) LIMIT 1
) p
CROSS JOIN LATERAL (
  SELECT price FROM price_lists
  WHERE product_id = p.id AND uom_code = 'THUNG' LIMIT 1
) pl
CROSS JOIN LATERAL (
  SELECT (1 + (abs(hashtext(o.order_no || ln::text)) % 20)) AS qty
) q;

-- -----------------------------------------------------------------------------
-- KIỂM TRA
-- -----------------------------------------------------------------------------
SELECT 'customers'      AS bang, count(*) FROM customers
UNION ALL SELECT 'products',           count(*) FROM products
UNION ALL SELECT 'product_uoms',       count(*) FROM product_uoms
UNION ALL SELECT 'price_lists',        count(*) FROM price_lists
UNION ALL SELECT 'sales_route_details',count(*) FROM sales_route_details
UNION ALL SELECT 'promotion_programs', count(*) FROM promotion_programs
UNION ALL SELECT 'promotion_breaks',   count(*) FROM promotion_breaks
UNION ALL SELECT 'promotion_items',    count(*) FROM promotion_items
UNION ALL SELECT 'visits',             count(*) FROM visits
UNION ALL SELECT 'orders',             count(*) FROM orders
UNION ALL SELECT 'order_details',      count(*) FROM order_details;
