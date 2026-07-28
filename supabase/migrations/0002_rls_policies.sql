-- =============================================================================
-- eSalesSFA — Row Level Security
-- Chạy SAU 0001_init_schema.sql
-- =============================================================================
-- Publishable key nằm công khai trong APK. Bảo mật thật sự nằm ở đây:
-- Postgres tự chặn ở tầng dòng dữ liệu, client không thể lách bằng cách sửa query.

-- Lấy salesperson_id của user đang đăng nhập.
-- SECURITY DEFINER để hàm đọc được bảng salespersons bất kể RLS của bảng đó.
CREATE OR REPLACE FUNCTION current_salesperson_id() RETURNS uuid AS $$
  SELECT id FROM salespersons WHERE user_id = auth.uid() AND is_deleted = false
$$ LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public;


-- -----------------------------------------------------------------------------
-- 1. DANH MỤC DÙNG CHUNG — ai đăng nhập cũng đọc được, không ai ghi được
-- -----------------------------------------------------------------------------
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'app_configs','uoms','branches','channels','price_groups','reason_codes',
    'product_categories','products','product_uoms','price_lists',
    'promotion_programs','promotion_breaks','promotion_items'
  ] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format(
      'CREATE POLICY %1$s_read ON %1$I FOR SELECT TO authenticated USING (true)', t);
  END LOOP;
END $$;


-- -----------------------------------------------------------------------------
-- 2. NHÂN VIÊN — chỉ thấy hồ sơ của chính mình
-- -----------------------------------------------------------------------------
ALTER TABLE salespersons ENABLE ROW LEVEL SECURITY;

CREATE POLICY salespersons_read_own ON salespersons
  FOR SELECT TO authenticated
  USING (user_id = auth.uid());

-- Cho phép cập nhật device_id khi đăng nhập lần đầu (device binding)
CREATE POLICY salespersons_update_own ON salespersons
  FOR UPDATE TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());


-- -----------------------------------------------------------------------------
-- 3. KHÁCH HÀNG — chỉ thấy KH mình phụ trách hoặc có trong tuyến của mình
-- -----------------------------------------------------------------------------
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;

CREATE POLICY customers_read ON customers
  FOR SELECT TO authenticated
  USING (
    salesperson_id = current_salesperson_id()
    OR EXISTS (
      SELECT 1
      FROM sales_route_details d
      JOIN sales_routes r ON r.id = d.route_id
      WHERE d.customer_id = customers.id
        AND r.salesperson_id = current_salesperson_id()
    )
  );


-- -----------------------------------------------------------------------------
-- 4. TUYẾN — chỉ tuyến của mình
-- -----------------------------------------------------------------------------
ALTER TABLE sales_routes ENABLE ROW LEVEL SECURITY;
CREATE POLICY sales_routes_read ON sales_routes
  FOR SELECT TO authenticated
  USING (salesperson_id = current_salesperson_id());

ALTER TABLE sales_route_details ENABLE ROW LEVEL SECURITY;
CREATE POLICY sales_route_details_read ON sales_route_details
  FOR SELECT TO authenticated
  USING (EXISTS (
    SELECT 1 FROM sales_routes r
    WHERE r.id = sales_route_details.route_id
      AND r.salesperson_id = current_salesperson_id()
  ));


-- -----------------------------------------------------------------------------
-- 5. GIAO DỊCH — đọc/ghi dữ liệu của chính mình
-- -----------------------------------------------------------------------------
-- WITH CHECK chặn việc client cố ghi đơn hàng mang tên nhân viên khác.
ALTER TABLE visits ENABLE ROW LEVEL SECURITY;
CREATE POLICY visits_own ON visits
  FOR ALL TO authenticated
  USING      (salesperson_id = current_salesperson_id())
  WITH CHECK (salesperson_id = current_salesperson_id());

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY orders_own ON orders
  FOR ALL TO authenticated
  USING      (salesperson_id = current_salesperson_id())
  WITH CHECK (salesperson_id = current_salesperson_id());

-- Bảng con: quyền suy ra từ đơn cha
ALTER TABLE order_details ENABLE ROW LEVEL SECURITY;
CREATE POLICY order_details_own ON order_details
  FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM orders o
    WHERE o.id = order_details.order_id
      AND o.salesperson_id = current_salesperson_id()))
  WITH CHECK (EXISTS (
    SELECT 1 FROM orders o
    WHERE o.id = order_details.order_id
      AND o.salesperson_id = current_salesperson_id()));

ALTER TABLE order_promotions ENABLE ROW LEVEL SECURITY;
CREATE POLICY order_promotions_own ON order_promotions
  FOR ALL TO authenticated
  USING (EXISTS (
    SELECT 1 FROM orders o
    WHERE o.id = order_promotions.order_id
      AND o.salesperson_id = current_salesperson_id()))
  WITH CHECK (EXISTS (
    SELECT 1 FROM orders o
    WHERE o.id = order_promotions.order_id
      AND o.salesperson_id = current_salesperson_id()));

ALTER TABLE sync_sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY sync_sessions_own ON sync_sessions
  FOR ALL TO authenticated
  USING      (salesperson_id = current_salesperson_id())
  WITH CHECK (salesperson_id = current_salesperson_id());
