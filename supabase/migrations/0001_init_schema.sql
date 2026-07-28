-- =============================================================================
-- eSalesSFA — Schema khởi tạo (24 bảng MVP)
-- Chạy: paste toàn bộ file vào Supabase Dashboard > SQL Editor > Run
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. HẠ TẦNG DELTA SYNC
-- -----------------------------------------------------------------------------
-- Một SEQUENCE dùng chung cho TOÀN BỘ database. Mỗi lần bất kỳ bảng master nào
-- được INSERT/UPDATE, row_version của dòng đó nhận giá trị kế tiếp.
-- => version tăng đơn điệu toàn cục, client chỉ cần lưu 1 mốc cho mỗi bảng và
--    hỏi "cho tôi các dòng có row_version > mốc của tôi".
CREATE SEQUENCE IF NOT EXISTS global_version_seq;

CREATE OR REPLACE FUNCTION fn_set_row_version() RETURNS trigger AS $$
BEGIN
  NEW.row_version := nextval('global_version_seq');
  NEW.updated_at  := now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Helper gắn trigger + index versioning cho 1 bảng master, đỡ lặp code
CREATE OR REPLACE FUNCTION fn_setup_sync_table(p_table text) RETURNS void AS $$
BEGIN
  EXECUTE format(
    'CREATE TRIGGER trg_version_%1$s BEFORE INSERT OR UPDATE ON %1$I
     FOR EACH ROW EXECUTE FUNCTION fn_set_row_version()', p_table);
  EXECUTE format(
    'CREATE INDEX idx_%1$s_version ON %1$I (row_version)', p_table);
END;
$$ LANGUAGE plpgsql;


-- -----------------------------------------------------------------------------
-- 1. CẤU HÌNH & DANH MỤC
-- -----------------------------------------------------------------------------

CREATE TABLE app_configs (
  code        text PRIMARY KEY,
  value       text        NOT NULL,
  data_type   text        NOT NULL DEFAULT 'STRING'
              CHECK (data_type IN ('STRING','INT','BOOL','DECIMAL')),
  description text,
  row_version bigint      NOT NULL,
  is_deleted  boolean     NOT NULL DEFAULT false,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE uoms (
  code        text PRIMARY KEY,
  name        text        NOT NULL,
  row_version bigint      NOT NULL,
  is_deleted  boolean     NOT NULL DEFAULT false,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE branches (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code        text UNIQUE NOT NULL,
  name        text        NOT NULL,
  address     text,
  latitude    double precision,
  longitude   double precision,
  row_version bigint      NOT NULL,
  is_deleted  boolean     NOT NULL DEFAULT false,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE channels (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code        text UNIQUE NOT NULL,
  name        text        NOT NULL,
  row_version bigint      NOT NULL,
  is_deleted  boolean     NOT NULL DEFAULT false,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE price_groups (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code        text UNIQUE NOT NULL,
  name        text        NOT NULL,
  row_version bigint      NOT NULL,
  is_deleted  boolean     NOT NULL DEFAULT false,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE reason_codes (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code        text        NOT NULL,
  name        text        NOT NULL,
  apply_for   text        NOT NULL
              CHECK (apply_for IN ('CHECKIN_OVER_DISTANCE','NO_ORDER','CANCEL_ORDER','OUT_OF_ROUTE')),
  sort_order  int         NOT NULL DEFAULT 0,
  row_version bigint      NOT NULL,
  is_deleted  boolean     NOT NULL DEFAULT false,
  updated_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (code, apply_for)
);

CREATE TABLE salespersons (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id         uuid UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
  code            text UNIQUE NOT NULL,
  full_name       text        NOT NULL,
  phone           text,
  email           text,
  branch_id       uuid        REFERENCES branches(id),
  role            text        NOT NULL DEFAULT 'SALES'
                  CHECK (role IN ('SALES','SUPERVISOR','ADMIN')),
  device_id       text,
  device_bound_at timestamptz,
  is_active       boolean     NOT NULL DEFAULT true,
  row_version     bigint      NOT NULL,
  is_deleted      boolean     NOT NULL DEFAULT false,
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_salespersons_user ON salespersons (user_id);


-- -----------------------------------------------------------------------------
-- 2. KHÁCH HÀNG & TUYẾN
-- -----------------------------------------------------------------------------

CREATE TABLE customers (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code           text UNIQUE NOT NULL,
  name           text        NOT NULL,
  name_search    text,                     -- tên bỏ dấu, lowercase => search nhanh phía client
  phone          text,
  address        text,
  latitude       double precision,
  longitude      double precision,
  channel_id     uuid        REFERENCES channels(id),
  price_group_id uuid        NOT NULL REFERENCES price_groups(id),
  branch_id      uuid        NOT NULL REFERENCES branches(id),
  salesperson_id uuid        REFERENCES salespersons(id),
  credit_limit   bigint      NOT NULL DEFAULT 0,
  debt_amount    bigint      NOT NULL DEFAULT 0,
  image_url      text,
  is_active      boolean     NOT NULL DEFAULT true,
  row_version    bigint      NOT NULL,
  is_deleted     boolean     NOT NULL DEFAULT false,
  updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_customers_sales ON customers (salesperson_id);

CREATE TABLE sales_routes (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code           text        NOT NULL,
  name           text        NOT NULL,
  salesperson_id uuid        NOT NULL REFERENCES salespersons(id),
  day_of_week    int         NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
  week_pattern   text        NOT NULL DEFAULT 'ALL'
                 CHECK (week_pattern IN ('ALL','ODD','EVEN')),
  row_version    bigint      NOT NULL,
  is_deleted     boolean     NOT NULL DEFAULT false,
  updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_routes_sales ON sales_routes (salesperson_id, day_of_week);

CREATE TABLE sales_route_details (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  route_id    uuid        NOT NULL REFERENCES sales_routes(id) ON DELETE CASCADE,
  customer_id uuid        NOT NULL REFERENCES customers(id),
  sort_order  int         NOT NULL DEFAULT 0,
  row_version bigint      NOT NULL,
  is_deleted  boolean     NOT NULL DEFAULT false,
  updated_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (route_id, customer_id)
);


-- -----------------------------------------------------------------------------
-- 3. SẢN PHẨM & GIÁ
-- -----------------------------------------------------------------------------

CREATE TABLE product_categories (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code        text UNIQUE NOT NULL,
  name        text        NOT NULL,
  parent_id   uuid        REFERENCES product_categories(id),
  sort_order  int         NOT NULL DEFAULT 0,
  row_version bigint      NOT NULL,
  is_deleted  boolean     NOT NULL DEFAULT false,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE products (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code           text UNIQUE NOT NULL,
  name           text         NOT NULL,
  name_search    text,
  barcode        text,
  category_id    uuid         REFERENCES product_categories(id),
  base_uom       text         NOT NULL REFERENCES uoms(code),
  vat_rate       numeric(7,4) NOT NULL DEFAULT 0.10,
  image_url      text,
  is_track_stock boolean      NOT NULL DEFAULT true,
  is_active      boolean      NOT NULL DEFAULT true,
  row_version    bigint       NOT NULL,
  is_deleted     boolean      NOT NULL DEFAULT false,
  updated_at     timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_products_barcode ON products (barcode);

-- ⚠️ Bảng gốc của mọi phép tính tiền: 1 Thùng = 24 Lẻ.
-- conversion_rate luôn quy về base_uom của product.
CREATE TABLE product_uoms (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id      uuid          NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  uom_code        text          NOT NULL REFERENCES uoms(code),
  conversion_rate numeric(18,6) NOT NULL CHECK (conversion_rate > 0),
  is_default_sale boolean       NOT NULL DEFAULT false,
  sort_order      int           NOT NULL DEFAULT 0,
  row_version     bigint        NOT NULL,
  is_deleted      boolean       NOT NULL DEFAULT false,
  updated_at      timestamptz   NOT NULL DEFAULT now(),
  UNIQUE (product_id, uom_code)
);

CREATE TABLE price_lists (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  product_id     uuid        NOT NULL REFERENCES products(id),
  price_group_id uuid        NOT NULL REFERENCES price_groups(id),
  uom_code       text        NOT NULL REFERENCES uoms(code),
  price          bigint      NOT NULL CHECK (price >= 0),   -- VND, không thập phân
  from_date      date        NOT NULL,
  to_date        date        NOT NULL DEFAULT '2099-12-31',
  row_version    bigint      NOT NULL,
  is_deleted     boolean     NOT NULL DEFAULT false,
  updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_price_lookup
  ON price_lists (price_group_id, product_id, uom_code, from_date, to_date);


-- -----------------------------------------------------------------------------
-- 4. KHUYẾN MÃI
-- -----------------------------------------------------------------------------

CREATE TABLE promotion_programs (
  id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code                  text UNIQUE NOT NULL,
  name                  text        NOT NULL,
  promo_type            text        NOT NULL
    CHECK (promo_type IN ('QTY_TIER','AMOUNT_TIER','FREE_ITEM','COMBO_BUNDLE','MANUAL')),
  apply_level           text        NOT NULL DEFAULT 'LINE'
    CHECK (apply_level IN ('LINE','GROUP','DOCUMENT')),
  discount_kind         text        NOT NULL DEFAULT 'PERCENT'
    CHECK (discount_kind IN ('PERCENT','AMOUNT','FREE_ITEM')),
  is_auto_apply         boolean     NOT NULL DEFAULT true,
  is_multi_level        boolean     NOT NULL DEFAULT false,
  priority              int         NOT NULL DEFAULT 0,
  from_date             date        NOT NULL,
  to_date               date        NOT NULL,
  budget_amount         bigint,
  used_amount           bigint      NOT NULL DEFAULT 0,
  exclude_program_codes text,
  row_version           bigint      NOT NULL,
  is_deleted            boolean     NOT NULL DEFAULT false,
  updated_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_promo_active ON promotion_programs (from_date, to_date, priority);

CREATE TABLE promotion_breaks (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  program_id      uuid          NOT NULL REFERENCES promotion_programs(id) ON DELETE CASCADE,
  break_level     int           NOT NULL,
  min_qty         numeric(18,3),
  min_amount      bigint,
  discount_pct    numeric(7,4)  NOT NULL DEFAULT 0,
  discount_amount bigint        NOT NULL DEFAULT 0,
  free_qty        numeric(18,3) NOT NULL DEFAULT 0,
  max_apply_times int,
  row_version     bigint        NOT NULL,
  is_deleted      boolean       NOT NULL DEFAULT false,
  updated_at      timestamptz   NOT NULL DEFAULT now(),
  UNIQUE (program_id, break_level),
  -- Một bậc phải có ít nhất một điều kiện, nếu không engine sẽ áp vô điều kiện
  CONSTRAINT chk_break_has_condition CHECK (min_qty IS NOT NULL OR min_amount IS NOT NULL)
);

CREATE TABLE promotion_items (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  program_id     uuid          NOT NULL REFERENCES promotion_programs(id) ON DELETE CASCADE,
  break_id       uuid          REFERENCES promotion_breaks(id) ON DELETE CASCADE,
  product_id     uuid          NOT NULL REFERENCES products(id),
  item_role      text          NOT NULL CHECK (item_role IN ('BUY','FREE')),
  bundle_group   text,
  required_qty   numeric(18,3) NOT NULL DEFAULT 0,
  uom_code       text          REFERENCES uoms(code),
  free_stock_qty numeric(18,3),
  row_version    bigint        NOT NULL,
  is_deleted     boolean       NOT NULL DEFAULT false,
  updated_at     timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_promo_items ON promotion_items (program_id, item_role);


-- -----------------------------------------------------------------------------
-- 5. GIAO DỊCH (client sinh id, đẩy lên server)
-- -----------------------------------------------------------------------------
-- ⚠️ KHÔNG có DEFAULT gen_random_uuid() trên PK — id phải do client sinh khi
--    đang offline. Đây là điều kiện để sync idempotent hoạt động.

CREATE TABLE visits (
  id                 uuid PRIMARY KEY,
  customer_id        uuid        NOT NULL REFERENCES customers(id),
  salesperson_id     uuid        NOT NULL REFERENCES salespersons(id),
  visit_date         date        NOT NULL,
  is_in_route        boolean     NOT NULL DEFAULT true,
  check_in_at        timestamptz NOT NULL,
  check_in_lat       double precision,
  check_in_lng       double precision,
  check_in_accuracy  real,
  check_in_distance  real,
  check_out_at       timestamptz,
  check_out_lat      double precision,
  check_out_lng      double precision,
  check_out_distance real,
  duration_minutes   int,
  reason_code        text,
  note               text,
  is_mock_location   boolean     NOT NULL DEFAULT false,
  battery_pct        int,
  device_id          text,
  session_id         uuid        NOT NULL,
  client_created_at  timestamptz NOT NULL,
  created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_visits_lookup ON visits (salesperson_id, visit_date);

CREATE TABLE orders (
  id                uuid PRIMARY KEY,
  order_no          text UNIQUE NOT NULL,
  customer_id       uuid        NOT NULL REFERENCES customers(id),
  salesperson_id    uuid        NOT NULL REFERENCES salespersons(id),
  visit_id          uuid        REFERENCES visits(id),
  branch_id         uuid        NOT NULL REFERENCES branches(id),
  order_date        date        NOT NULL,
  delivery_date     date,
  status            text        NOT NULL DEFAULT 'NEW'
                    CHECK (status IN ('NEW','CONFIRMED','CANCELLED')),
  sub_total         bigint      NOT NULL DEFAULT 0,
  discount_amount   bigint      NOT NULL DEFAULT 0,
  manual_discount   bigint      NOT NULL DEFAULT 0,
  net_amount        bigint      NOT NULL DEFAULT 0,
  vat_amount        bigint      NOT NULL DEFAULT 0,
  total_amount      bigint      NOT NULL DEFAULT 0,
  note              text,
  reason_code       text,
  session_id        uuid        NOT NULL,
  client_created_at timestamptz NOT NULL,
  created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_orders_lookup ON orders (salesperson_id, order_date);
CREATE INDEX idx_orders_customer ON orders (customer_id, order_date);

CREATE TABLE order_details (
  id              uuid PRIMARY KEY,
  order_id        uuid          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  line_no         int           NOT NULL,
  product_id      uuid          NOT NULL REFERENCES products(id),
  uom_code        text          NOT NULL REFERENCES uoms(code),
  qty             numeric(18,3) NOT NULL,
  -- Snapshot tại thời điểm đặt. KHÔNG join sang price_lists để hiển thị lại đơn cũ,
  -- vì giá đổi là toàn bộ lịch sử đơn hàng sẽ sai.
  conversion_rate numeric(18,6) NOT NULL,
  base_qty        numeric(18,3) NOT NULL,
  price           bigint        NOT NULL,
  gross_amount    bigint        NOT NULL,
  discount_amount bigint        NOT NULL DEFAULT 0,
  net_amount      bigint        NOT NULL,
  vat_rate        numeric(7,4)  NOT NULL DEFAULT 0,
  vat_amount      bigint        NOT NULL DEFAULT 0,
  line_amount     bigint        NOT NULL,
  is_free_item    boolean       NOT NULL DEFAULT false,
  promotion_id    uuid          REFERENCES promotion_programs(id),
  UNIQUE (order_id, line_no)
);
CREATE INDEX idx_order_details_order ON order_details (order_id);

CREATE TABLE order_promotions (
  id              uuid PRIMARY KEY,
  order_id        uuid          NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  order_detail_id uuid          REFERENCES order_details(id) ON DELETE CASCADE,
  program_id      uuid          NOT NULL REFERENCES promotion_programs(id),
  break_id        uuid          REFERENCES promotion_breaks(id),
  apply_times     numeric(18,3) NOT NULL DEFAULT 1,
  discount_amount bigint        NOT NULL DEFAULT 0,
  free_qty        numeric(18,3) NOT NULL DEFAULT 0,
  is_manual       boolean       NOT NULL DEFAULT false
);
CREATE INDEX idx_order_promos ON order_promotions (order_id);


-- -----------------------------------------------------------------------------
-- 6. HẠ TẦNG SYNC (server-side)
-- -----------------------------------------------------------------------------
-- Bảng làm nên tính idempotent: client gửi lại cùng session_id thì server nhận ra
-- và không tạo bản ghi trùng.
CREATE TABLE sync_sessions (
  session_id      uuid PRIMARY KEY,
  salesperson_id  uuid        NOT NULL REFERENCES salespersons(id),
  sync_type       text        NOT NULL CHECK (sync_type IN ('DOWNLOAD','UPLOAD')),
  status          text        NOT NULL DEFAULT 'PROCESSING'
                  CHECK (status IN ('PROCESSING','SUCCESS','FAILED')),
  payload_summary jsonb,
  error_message   text,
  started_at      timestamptz NOT NULL DEFAULT now(),
  finished_at     timestamptz
);
CREATE INDEX idx_sync_sessions_sales ON sync_sessions (salesperson_id, started_at DESC);


-- -----------------------------------------------------------------------------
-- 7. GẮN TRIGGER VERSIONING CHO MỌI BẢNG MASTER
-- -----------------------------------------------------------------------------
-- Bảng giao dịch KHÔNG cần: client không tải chúng xuống, chỉ đẩy lên.
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'app_configs','uoms','branches','channels','price_groups','reason_codes',
    'salespersons','customers','sales_routes','sales_route_details',
    'product_categories','products','product_uoms','price_lists',
    'promotion_programs','promotion_breaks','promotion_items'
  ] LOOP
    PERFORM fn_setup_sync_table(t);
  END LOOP;
END $$;
