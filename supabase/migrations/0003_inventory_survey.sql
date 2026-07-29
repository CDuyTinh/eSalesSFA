-- =============================================================================
-- eSalesSFA — Kiểm kê tồn kho & Khảo sát trưng bày
-- Chạy SAU 0002_rls_policies.sql
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. KIỂM KÊ TỒN KHO CỬA HÀNG (giao dịch — client sinh id)
-- -----------------------------------------------------------------------------

CREATE TABLE stock_counts (
  id                uuid PRIMARY KEY,
  customer_id       uuid        NOT NULL REFERENCES customers(id),
  salesperson_id    uuid        NOT NULL REFERENCES salespersons(id),
  visit_id          uuid        REFERENCES visits(id),
  count_date        date        NOT NULL,
  note              text,
  session_id        uuid        NOT NULL,
  client_created_at timestamptz NOT NULL,
  created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_stock_counts_lookup ON stock_counts (salesperson_id, count_date);

CREATE TABLE stock_count_details (
  id             uuid PRIMARY KEY,
  stock_count_id uuid          NOT NULL REFERENCES stock_counts(id) ON DELETE CASCADE,
  product_id     uuid          NOT NULL REFERENCES products(id),
  uom_code       text          NOT NULL REFERENCES uoms(code),
  qty            numeric(18,3) NOT NULL DEFAULT 0,
  base_qty       numeric(18,3) NOT NULL DEFAULT 0,
  -- Tồn kỳ trước, do client điền từ lần kiểm kê gần nhất. Lưu lại để báo cáo
  -- so sánh được mà không phải tự đi tra lịch sử.
  prev_base_qty  numeric(18,3) NOT NULL DEFAULT 0,
  suggest_qty    numeric(18,3) NOT NULL DEFAULT 0,
  UNIQUE (stock_count_id, product_id, uom_code)
);
CREATE INDEX idx_stock_count_details ON stock_count_details (stock_count_id);

-- -----------------------------------------------------------------------------
-- 2. KHẢO SÁT — cấu hình (master, tải xuống)
-- -----------------------------------------------------------------------------

CREATE TABLE survey_types (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code        text UNIQUE NOT NULL,
  name        text        NOT NULL,
  pass_score  numeric(7,2) NOT NULL DEFAULT 0,
  row_version bigint      NOT NULL,
  is_deleted  boolean     NOT NULL DEFAULT false,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE survey_question_groups (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  survey_type_id uuid        NOT NULL REFERENCES survey_types(id) ON DELETE CASCADE,
  name           text        NOT NULL,
  sort_order     int         NOT NULL DEFAULT 0,
  row_version    bigint      NOT NULL,
  is_deleted     boolean     NOT NULL DEFAULT false,
  updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE survey_questions (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id     uuid         NOT NULL REFERENCES survey_question_groups(id) ON DELETE CASCADE,
  code         text         NOT NULL,
  content      text         NOT NULL,
  answer_type  text         NOT NULL
               CHECK (answer_type IN ('YES_NO','SINGLE','MULTI','NUMBER','TEXT','PHOTO')),
  is_required  boolean      NOT NULL DEFAULT true,
  score        numeric(7,2) NOT NULL DEFAULT 0,
  min_photo    int          NOT NULL DEFAULT 0,
  sort_order   int          NOT NULL DEFAULT 0,
  row_version  bigint       NOT NULL,
  is_deleted   boolean      NOT NULL DEFAULT false,
  updated_at   timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE survey_question_options (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  question_id uuid         NOT NULL REFERENCES survey_questions(id) ON DELETE CASCADE,
  code        text         NOT NULL,
  content     text         NOT NULL,
  score       numeric(7,2) NOT NULL DEFAULT 0,
  sort_order  int          NOT NULL DEFAULT 0,
  row_version bigint       NOT NULL,
  is_deleted  boolean      NOT NULL DEFAULT false,
  updated_at  timestamptz  NOT NULL DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- 3. KHẢO SÁT — kết quả (giao dịch)
-- -----------------------------------------------------------------------------

CREATE TABLE surveys (
  id                uuid PRIMARY KEY,
  survey_type_id    uuid        NOT NULL REFERENCES survey_types(id),
  customer_id       uuid        NOT NULL REFERENCES customers(id),
  salesperson_id    uuid        NOT NULL REFERENCES salespersons(id),
  visit_id          uuid        REFERENCES visits(id),
  survey_date       date        NOT NULL,
  total_score       numeric(7,2) NOT NULL DEFAULT 0,
  is_passed         boolean     NOT NULL DEFAULT false,
  note              text,
  session_id        uuid        NOT NULL,
  client_created_at timestamptz NOT NULL,
  created_at        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_surveys_lookup ON surveys (salesperson_id, survey_date);

CREATE TABLE survey_answers (
  id           uuid PRIMARY KEY,
  survey_id    uuid NOT NULL REFERENCES surveys(id) ON DELETE CASCADE,
  question_id  uuid NOT NULL REFERENCES survey_questions(id),
  option_id    uuid REFERENCES survey_question_options(id),
  answer_text  text,
  answer_value numeric(18,3),
  answer_bool  boolean,
  score        numeric(7,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_survey_answers ON survey_answers (survey_id);

CREATE TABLE survey_photos (
  id           uuid PRIMARY KEY,
  survey_id    uuid NOT NULL REFERENCES surveys(id) ON DELETE CASCADE,
  question_id  uuid REFERENCES survey_questions(id),
  -- Đường dẫn trong bucket, KHÔNG phải URL đầy đủ: đổi domain hay chuyển CDN
  -- thì mọi bản ghi cũ vẫn dùng được.
  storage_path text NOT NULL,
  latitude     double precision,
  longitude    double precision,
  taken_at     timestamptz NOT NULL,
  file_size    int
);
CREATE INDEX idx_survey_photos ON survey_photos (survey_id);

-- -----------------------------------------------------------------------------
-- 4. VERSIONING CHO BẢNG MASTER MỚI
-- -----------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'survey_types','survey_question_groups','survey_questions','survey_question_options'
  ] LOOP
    PERFORM fn_setup_sync_table(t);
  END LOOP;
END $$;

-- -----------------------------------------------------------------------------
-- 5. LỊCH SỬ ĐƠN HÀNG CHO DASHBOARD
-- -----------------------------------------------------------------------------
-- Đơn hàng vốn là bảng giao dịch chỉ đi lên. Nhưng dashboard và báo cáo cần đọc
-- lịch sử ngay cả khi offline, nên phải tải ngược về máy.
--
-- Thêm cột versioning để dùng chung cơ chế delta sync thay vì viết endpoint
-- riêng. Đơn do chính client tạo sẽ được tải lại — vô hại vì upsert theo id.
ALTER TABLE orders        ADD COLUMN row_version bigint, ADD COLUMN is_deleted boolean NOT NULL DEFAULT false, ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE order_details ADD COLUMN row_version bigint, ADD COLUMN is_deleted boolean NOT NULL DEFAULT false, ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

-- Gán version cho dữ liệu đã có (720 đơn seed) trước khi bật trigger.
UPDATE orders        SET row_version = nextval('global_version_seq');
UPDATE order_details SET row_version = nextval('global_version_seq');

ALTER TABLE orders        ALTER COLUMN row_version SET NOT NULL;
ALTER TABLE order_details ALTER COLUMN row_version SET NOT NULL;

SELECT fn_setup_sync_table('orders');
SELECT fn_setup_sync_table('order_details');

-- -----------------------------------------------------------------------------
-- 6. RLS
-- -----------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
  -- Cấu hình khảo sát: ai đăng nhập cũng đọc được.
  FOREACH t IN ARRAY ARRAY[
    'survey_types','survey_question_groups','survey_questions','survey_question_options'
  ] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %1$s_read ON %1$I FOR SELECT TO authenticated USING (true)', t);
  END LOOP;
END $$;

ALTER TABLE stock_counts ENABLE ROW LEVEL SECURITY;
CREATE POLICY stock_counts_own ON stock_counts
  FOR ALL TO authenticated
  USING      (salesperson_id = current_salesperson_id())
  WITH CHECK (salesperson_id = current_salesperson_id());

ALTER TABLE stock_count_details ENABLE ROW LEVEL SECURITY;
CREATE POLICY stock_count_details_own ON stock_count_details
  FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM stock_counts s
    WHERE s.id = stock_count_details.stock_count_id
      AND s.salesperson_id = current_salesperson_id()))
  WITH CHECK (EXISTS (
    SELECT 1 FROM stock_counts s
    WHERE s.id = stock_count_details.stock_count_id
      AND s.salesperson_id = current_salesperson_id()));

ALTER TABLE surveys ENABLE ROW LEVEL SECURITY;
CREATE POLICY surveys_own ON surveys
  FOR ALL TO authenticated
  USING      (salesperson_id = current_salesperson_id())
  WITH CHECK (salesperson_id = current_salesperson_id());

ALTER TABLE survey_answers ENABLE ROW LEVEL SECURITY;
CREATE POLICY survey_answers_own ON survey_answers
  FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM surveys s
    WHERE s.id = survey_answers.survey_id
      AND s.salesperson_id = current_salesperson_id()))
  WITH CHECK (EXISTS (
    SELECT 1 FROM surveys s
    WHERE s.id = survey_answers.survey_id
      AND s.salesperson_id = current_salesperson_id()));

ALTER TABLE survey_photos ENABLE ROW LEVEL SECURITY;
CREATE POLICY survey_photos_own ON survey_photos
  FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM surveys s
    WHERE s.id = survey_photos.survey_id
      AND s.salesperson_id = current_salesperson_id()))
  WITH CHECK (EXISTS (
    SELECT 1 FROM surveys s
    WHERE s.id = survey_photos.survey_id
      AND s.salesperson_id = current_salesperson_id()));
