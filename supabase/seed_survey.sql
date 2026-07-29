-- =============================================================================
-- Seed cấu hình khảo sát Perfect Store
-- Chạy SAU 0003_inventory_survey.sql
-- =============================================================================

INSERT INTO survey_types (code, name, pass_score) VALUES
  ('PS',   'Perfect Store',      70),
  ('POSM', 'Kiểm tra POSM',      50);

-- ── Nhóm câu hỏi cho Perfect Store ───────────────────────────────────────────
INSERT INTO survey_question_groups (survey_type_id, name, sort_order)
SELECT t.id, g.name, g.ord
FROM survey_types t
CROSS JOIN (VALUES
  ('Trưng bày kệ',      1),
  ('Vật phẩm quảng cáo', 2),
  ('Giá bán',            3)
) AS g(name, ord)
WHERE t.code = 'PS';

-- ── Câu hỏi ──────────────────────────────────────────────────────────────────
INSERT INTO survey_questions (group_id, code, content, answer_type, is_required, score, min_photo, sort_order)
SELECT g.id, q.code, q.content, q.answer_type, q.required, q.score, q.min_photo, q.ord
FROM survey_question_groups g
JOIN survey_types t ON t.id = g.survey_type_id AND t.code = 'PS'
CROSS JOIN LATERAL (VALUES
  ('Q1', 'Kệ trưng bày có đủ 3 tầng sản phẩm không?', 'YES_NO', true,  20.0, 0, 1),
  ('Q2', 'Chụp ảnh kệ trưng bày',                      'PHOTO',  true,  0.0,  2, 2),
  ('Q3', 'Số mặt hàng đang trưng bày',                 'NUMBER', true,  15.0, 0, 3),
  ('Q4', 'Vị trí kệ trong cửa hàng',                   'SINGLE', true,  15.0, 0, 4),
  ('Q5', 'Ghi chú thêm',                               'TEXT',   false, 0.0,  0, 5)
) AS q(code, content, answer_type, required, score, min_photo, ord)
WHERE g.name = 'Trưng bày kệ';

INSERT INTO survey_questions (group_id, code, content, answer_type, is_required, score, min_photo, sort_order)
SELECT g.id, q.code, q.content, q.answer_type, q.required, q.score, q.min_photo, q.ord
FROM survey_question_groups g
JOIN survey_types t ON t.id = g.survey_type_id AND t.code = 'PS'
CROSS JOIN LATERAL (VALUES
  ('Q6', 'Có treo poster khuyến mãi hiện hành không?', 'YES_NO', true, 15.0, 0, 1),
  ('Q7', 'Loại vật phẩm đang có tại cửa hàng',         'MULTI',  true, 15.0, 0, 2),
  ('Q8', 'Chụp ảnh vật phẩm quảng cáo',                'PHOTO',  true, 0.0,  1, 3)
) AS q(code, content, answer_type, required, score, min_photo, ord)
WHERE g.name = 'Vật phẩm quảng cáo';

INSERT INTO survey_questions (group_id, code, content, answer_type, is_required, score, min_photo, sort_order)
SELECT g.id, q.code, q.content, q.answer_type, q.required, q.score, q.min_photo, q.ord
FROM survey_question_groups g
JOIN survey_types t ON t.id = g.survey_type_id AND t.code = 'PS'
CROSS JOIN LATERAL (VALUES
  ('Q9',  'Giá niêm yết có đúng bảng giá không?', 'YES_NO', true, 20.0, 0, 1),
  ('Q10', 'Chụp ảnh bảng giá',                    'PHOTO',  false, 0.0, 1, 2)
) AS q(code, content, answer_type, required, score, min_photo, ord)
WHERE g.name = 'Giá bán';

-- ── Đáp án cho câu SINGLE / MULTI ────────────────────────────────────────────
INSERT INTO survey_question_options (question_id, code, content, score, sort_order)
SELECT q.id, o.code, o.content, o.score, o.ord
FROM survey_questions q
CROSS JOIN LATERAL (VALUES
  ('A', 'Ngay quầy thu ngân', 15.0, 1),
  ('B', 'Giữa cửa hàng',      10.0, 2),
  ('C', 'Cuối cửa hàng',       5.0, 3)
) AS o(code, content, score, ord)
WHERE q.code = 'Q4';

INSERT INTO survey_question_options (question_id, code, content, score, sort_order)
SELECT q.id, o.code, o.content, o.score, o.ord
FROM survey_questions q
CROSS JOIN LATERAL (VALUES
  ('P', 'Poster',      5.0, 1),
  ('W', 'Wobbler',     5.0, 2),
  ('S', 'Sticker kệ',  5.0, 3),
  ('B', 'Banner ngoài trời', 5.0, 4)
) AS o(code, content, score, ord)
WHERE q.code = 'Q7';

-- ── Kiểm tra ─────────────────────────────────────────────────────────────────
SELECT 'survey_types' AS bang, count(*) FROM survey_types
UNION ALL SELECT 'survey_question_groups',  count(*) FROM survey_question_groups
UNION ALL SELECT 'survey_questions',        count(*) FROM survey_questions
UNION ALL SELECT 'survey_question_options', count(*) FROM survey_question_options;
