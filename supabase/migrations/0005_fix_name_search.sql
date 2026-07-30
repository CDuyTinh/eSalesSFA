-- =============================================================================
-- Sửa cột name_search sinh sai trong seed
-- =============================================================================
-- Seed cũ đặt name_search = lower('cua hang ' || i), BỎ MẤT phần tên riêng
-- ("Minh Anh", "Thành Đạt"). Hệ quả: tìm theo tên khách hàng không ra kết quả.
--
-- Client hiện đã tự sinh chuỗi tìm kiếm nên không còn phụ thuộc cột này, nhưng
-- vẫn nên sửa để dữ liệu server đúng và các hệ thống khác dùng được.

CREATE EXTENSION IF NOT EXISTS unaccent;

UPDATE customers SET name_search = lower(unaccent(name));
UPDATE products  SET name_search = lower(unaccent(name));

-- Giữ cột luôn đúng khi thêm/sửa về sau, thay vì phụ thuộc người viết INSERT
-- nhớ tính tay — đây chính là chỗ seed cũ làm sai.
CREATE OR REPLACE FUNCTION fn_set_name_search() RETURNS trigger AS $$
BEGIN
  NEW.name_search := lower(unaccent(NEW.name));
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_name_search_customers
  BEFORE INSERT OR UPDATE OF name ON customers
  FOR EACH ROW EXECUTE FUNCTION fn_set_name_search();

CREATE TRIGGER trg_name_search_products
  BEFORE INSERT OR UPDATE OF name ON products
  FOR EACH ROW EXECUTE FUNCTION fn_set_name_search();

-- Kiểm tra: phải thấy tên riêng trong chuỗi tìm kiếm
SELECT code, name, name_search FROM customers ORDER BY code LIMIT 5;
