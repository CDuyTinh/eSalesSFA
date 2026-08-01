-- =============================================================================
-- Ảnh chụp tại cửa hàng lúc check-in
-- Chạy SAU 0005_fix_name_search.sql
--
-- ⚠️ Phải chạy TRƯỚC khi phát hành bản app có chức năng này: sync-upload upsert
--    thẳng vào bảng visits, gửi một cột chưa tồn tại sẽ làm hỏng CẢ batch giao
--    dịch chứ không riêng bản ghi đó.
-- =============================================================================

-- Đường dẫn ảnh trong bucket, dạng {salesperson_id}/{visit_id}.jpg.
--
-- Đường dẫn này suy ra được từ hai id, nhưng cột vẫn cần: NULL nghĩa là ảnh
-- CHƯA lên tới Storage. Đó là thứ không suy ra được, và cũng chính là thứ cần
-- khi đối soát nhân viên có thật sự tới cửa hàng.
ALTER TABLE visits ADD COLUMN IF NOT EXISTS check_in_photo_url text;

-- Không lưu đường dẫn file trong máy: nó chỉ có ý nghĩa trên đúng thiết bị đó.

-- -----------------------------------------------------------------------------
-- Bucket ảnh check-in
-- -----------------------------------------------------------------------------
-- Tách bucket riêng với survey-photos: hai loại ảnh có vòng đời và quyền xem
-- khác nhau — ảnh check-in dùng để đối soát chấm công, ảnh khảo sát là dữ liệu
-- trưng bày. Chung bucket thì mọi thay đổi policy đều đụng cả hai.
--
-- public = false: ảnh chỉ đọc được khi có token hợp lệ.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('visit-photos', 'visit-photos', false, 2097152, ARRAY['image/jpeg','image/png'])
ON CONFLICT (id) DO NOTHING;

-- Thư mục cấp một của đường dẫn chính là id nhân viên — dùng nó để phân quyền.
CREATE POLICY visit_photos_insert_own ON storage.objects
  FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'visit-photos'
    AND (storage.foldername(name))[1] = current_salesperson_id()::text
  );

CREATE POLICY visit_photos_read_own ON storage.objects
  FOR SELECT TO authenticated
  USING (
    bucket_id = 'visit-photos'
    AND (storage.foldername(name))[1] = current_salesperson_id()::text
  );

-- Cho phép ghi đè khi worker retry upload cùng một ảnh.
CREATE POLICY visit_photos_update_own ON storage.objects
  FOR UPDATE TO authenticated
  USING (
    bucket_id = 'visit-photos'
    AND (storage.foldername(name))[1] = current_salesperson_id()::text
  );
