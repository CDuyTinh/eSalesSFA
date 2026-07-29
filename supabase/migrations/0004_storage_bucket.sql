-- =============================================================================
-- Bucket lưu ảnh minh chứng khảo sát
-- Chạy SAU 0003_inventory_survey.sql
-- =============================================================================

-- public = false: ảnh chỉ đọc được khi có token hợp lệ. Để public thì bất kỳ ai
-- đoán đúng đường dẫn đều xem được ảnh cửa hàng của khách.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('survey-photos', 'survey-photos', false, 2097152, ARRAY['image/jpeg','image/png'])
ON CONFLICT (id) DO NOTHING;

-- Đường dẫn có dạng {salesperson_id}/{survey_id}/{photo_id}.jpg, nên thư mục
-- cấp một chính là id nhân viên — dùng nó để phân quyền.
CREATE POLICY survey_photos_insert_own ON storage.objects
  FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'survey-photos'
    AND (storage.foldername(name))[1] = current_salesperson_id()::text
  );

CREATE POLICY survey_photos_read_own ON storage.objects
  FOR SELECT TO authenticated
  USING (
    bucket_id = 'survey-photos'
    AND (storage.foldername(name))[1] = current_salesperson_id()::text
  );

-- Cho phép ghi đè khi worker retry upload cùng một ảnh.
CREATE POLICY survey_photos_update_own ON storage.objects
  FOR UPDATE TO authenticated
  USING (
    bucket_id = 'survey-photos'
    AND (storage.foldername(name))[1] = current_salesperson_id()::text
  );
