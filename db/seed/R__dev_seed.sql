-- R__dev_seed.sql — dữ liệu mẫu cho môi trường dev. Chỉ nạp ở profile dev
-- (application-dev.yml thêm filesystem:../db/seed vào spring.flyway.locations).
--
-- Migration lặp lại (R__) của Flyway: không chiếm số version, chạy sau mọi migration V
-- và chạy lại mỗi khi nội dung file đổi. Mọi lệnh phải chạy lại được: ON CONFLICT DO NOTHING.
-- Id cố định để tham chiếu chéo ổn định giữa các lần chạy.
-- Không có dữ liệu cá nhân thật. system_setting, badge, prompt_template đã có trong V1/V3.
--

-- ---------------------------------------------------------------------
-- 0. Admin giả làm chủ nội dung seed (question_bank.created_by NOT NULL).
--    Không có mật khẩu nên không đăng nhập được. Trigger trg_auth_user_created (V1) tạo public.users.
-- ---------------------------------------------------------------------
INSERT INTO auth.users (id, email, raw_user_meta_data) VALUES
 ('ad000000-0000-4000-8000-000000000001', 'seed-admin@pela.local', '{"full_name":"PELA Seed Admin"}')
ON CONFLICT DO NOTHING;

UPDATE users SET role = 'admin', status = 'active'
WHERE user_id = 'ad000000-0000-4000-8000-000000000001' AND (role <> 'admin' OR status <> 'active');

-- ---------------------------------------------------------------------
-- 1. Khảo sát định hướng (UC-02) + luật map đáp án -> CEFR (AS-3)
-- ---------------------------------------------------------------------
INSERT INTO survey_question (question_id, position, text, options) VALUES
 ('5e000000-0000-4000-8000-000000000001', 1, 'Bạn đã học tiếng Anh bao lâu?',
  '[{"key":"a","text":"Mới bắt đầu"},{"key":"b","text":"1-3 năm"},{"key":"c","text":"Trên 3 năm"}]'),
 ('5e000000-0000-4000-8000-000000000002', 2, 'Bạn có thể giới thiệu bản thân bằng tiếng Anh không?',
  '[{"key":"a","text":"Chưa"},{"key":"b","text":"Vài câu đơn giản"},{"key":"c","text":"Trôi chảy"}]'),
 ('5e000000-0000-4000-8000-000000000003', 3, 'Bạn đọc được email công việc bằng tiếng Anh không?',
  '[{"key":"a","text":"Không"},{"key":"b","text":"Hiểu ý chính"},{"key":"c","text":"Hiểu gần hết"}]')
ON CONFLICT DO NOTHING;

-- Khớp theo priority tăng dần, luật {} là mặc định
INSERT INTO survey_level_rule (rule_id, answer_pattern, cefr_level, priority) VALUES
 ('5e100000-0000-4000-8000-000000000001', '{"5e000000-0000-4000-8000-000000000001":"a"}', 'A1', 1),
 ('5e100000-0000-4000-8000-000000000002', '{"5e000000-0000-4000-8000-000000000001":"c","5e000000-0000-4000-8000-000000000003":"c"}', 'B1', 2),
 ('5e100000-0000-4000-8000-000000000003', '{}', 'A2', 100)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 2. Lộ trình: 2 topic, mỗi topic 2 lesson, mỗi topic 20 từ (10 từ / lesson)
-- ---------------------------------------------------------------------
INSERT INTO topic (topic_id, cefr_level, name, order_index, status) VALUES
 ('70000000-0000-4000-8000-000000000001', 'A2', 'Travel', 1, 'published'),
 ('70000000-0000-4000-8000-000000000002', 'B1', 'Work',   1, 'published')
ON CONFLICT DO NOTHING;

INSERT INTO lesson (lesson_id, topic_id, order_index, new_word_count, status)
SELECT l.lesson_id::uuid, l.topic_id::uuid, l.order_index, 10, 'published'
FROM (VALUES
 ('1e550000-0000-4000-8000-000000000011', '70000000-0000-4000-8000-000000000001', 1),
 ('1e550000-0000-4000-8000-000000000012', '70000000-0000-4000-8000-000000000001', 2),
 ('1e550000-0000-4000-8000-000000000021', '70000000-0000-4000-8000-000000000002', 1),
 ('1e550000-0000-4000-8000-000000000022', '70000000-0000-4000-8000-000000000002', 2)
) AS l(lesson_id, topic_id, order_index)
WHERE EXISTS (SELECT 1 FROM topic t WHERE t.topic_id = l.topic_id::uuid)
ON CONFLICT DO NOTHING;

-- Từ vựng kho chung (source = 'curated'); cột lesson là lesson_id để gắn ở bước sau
CREATE TEMP TABLE seed_vocab (
    lesson_id uuid, word text, part_of_speech text, meaning text,
    example_sentence text, example_meaning text, topic text, cefr_level text
) ON COMMIT DROP;

INSERT INTO seed_vocab VALUES
 -- Travel A2 - lesson 1
 ('1e550000-0000-4000-8000-000000000011','passport','noun','hộ chiếu','Please show your passport at the gate.','Vui lòng xuất trình hộ chiếu ở cổng.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000011','ticket','noun','vé','I bought a train ticket online.','Tôi đã mua vé tàu trên mạng.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000011','luggage','noun','hành lý','My luggage is too heavy.','Hành lý của tôi quá nặng.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000011','hotel','noun','khách sạn','We stayed at a small hotel near the beach.','Chúng tôi ở một khách sạn nhỏ gần bãi biển.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000011','airport','noun','sân bay','The airport is far from the city centre.','Sân bay ở xa trung tâm thành phố.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000011','map','noun','bản đồ','Can you show me on the map?','Bạn chỉ cho tôi trên bản đồ được không?','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000011','tourist','noun','khách du lịch','The museum is full of tourists in summer.','Bảo tàng đông khách du lịch vào mùa hè.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000011','journey','noun','chuyến đi','The journey takes three hours.','Chuyến đi mất ba tiếng.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000011','reservation','noun','sự đặt chỗ','I have a reservation for two nights.','Tôi đã đặt phòng hai đêm.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000011','platform','noun','sân ga','The train leaves from platform 4.','Tàu khởi hành từ sân ga số 4.','Travel','A2'),
 -- Travel A2 - lesson 2
 ('1e550000-0000-4000-8000-000000000012','departure','noun','sự khởi hành','Check the departure time on the board.','Hãy xem giờ khởi hành trên bảng.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000012','arrival','noun','sự đến nơi','Our arrival was delayed by the rain.','Việc chúng tôi đến nơi bị trễ vì mưa.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000012','souvenir','noun','quà lưu niệm','She bought a souvenir for her mother.','Cô ấy mua quà lưu niệm cho mẹ.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000012','guide','noun','hướng dẫn viên','Our guide speaks three languages.','Hướng dẫn viên của chúng tôi nói ba thứ tiếng.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000012','delay','noun','sự chậm trễ','There is a two-hour delay.','Có sự chậm trễ hai tiếng.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000012','customs','noun','hải quan','We went through customs quickly.','Chúng tôi qua hải quan rất nhanh.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000012','backpack','noun','ba lô','I carry everything in my backpack.','Tôi mang mọi thứ trong ba lô.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000012','sightseeing','noun','tham quan','We went sightseeing in the old town.','Chúng tôi đi tham quan phố cổ.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000012','destination','noun','điểm đến','Paris is a popular destination.','Paris là điểm đến được ưa chuộng.','Travel','A2'),
 ('1e550000-0000-4000-8000-000000000012','boarding pass','noun','thẻ lên máy bay','Keep your boarding pass ready.','Hãy chuẩn bị sẵn thẻ lên máy bay.','Travel','A2'),
 -- Work B1 - lesson 1
 ('1e550000-0000-4000-8000-000000000021','colleague','noun','đồng nghiệp','My colleague helped me finish the report.','Đồng nghiệp đã giúp tôi hoàn thành báo cáo.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000021','deadline','noun','hạn chót','The deadline for this project is Friday.','Hạn chót của dự án này là thứ Sáu.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000021','meeting','noun','cuộc họp','We have a team meeting every Monday.','Nhóm chúng tôi họp vào mỗi thứ Hai.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000021','salary','noun','tiền lương','She asked for a higher salary.','Cô ấy đề nghị mức lương cao hơn.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000021','promotion','noun','sự thăng chức','He got a promotion last year.','Anh ấy được thăng chức năm ngoái.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000021','interview','noun','buổi phỏng vấn','I have a job interview tomorrow.','Ngày mai tôi có buổi phỏng vấn xin việc.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000021','schedule','noun','lịch trình','My schedule is very busy this week.','Lịch trình tuần này của tôi rất bận.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000021','task','noun','nhiệm vụ','This task should take one hour.','Nhiệm vụ này mất khoảng một giờ.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000021','manager','noun','quản lý','Please send the file to your manager.','Hãy gửi tệp cho quản lý của bạn.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000021','experience','noun','kinh nghiệm','She has five years of experience in sales.','Cô ấy có năm năm kinh nghiệm bán hàng.','Work','B1'),
 -- Work B1 - lesson 2
 ('1e550000-0000-4000-8000-000000000022','responsibility','noun','trách nhiệm','Training new staff is my responsibility.','Đào tạo nhân viên mới là trách nhiệm của tôi.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000022','contract','noun','hợp đồng','Read the contract before you sign it.','Hãy đọc hợp đồng trước khi ký.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000022','overtime','noun','làm thêm giờ','We worked overtime to finish on time.','Chúng tôi làm thêm giờ để kịp tiến độ.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000022','department','noun','phòng ban','He works in the marketing department.','Anh ấy làm ở phòng marketing.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000022','report','noun','báo cáo','The monthly report is ready.','Báo cáo tháng đã sẵn sàng.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000022','apply','verb','nộp đơn','I want to apply for this position.','Tôi muốn nộp đơn vào vị trí này.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000022','resign','verb','từ chức','She decided to resign from her job.','Cô ấy quyết định nghỉ việc.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000022','skill','noun','kỹ năng','Communication is an important skill.','Giao tiếp là một kỹ năng quan trọng.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000022','career','noun','sự nghiệp','He started his career as a teacher.','Anh ấy bắt đầu sự nghiệp là giáo viên.','Work','B1'),
 ('1e550000-0000-4000-8000-000000000022','achievement','noun','thành tựu','Winning the award was a great achievement.','Giành giải thưởng là một thành tựu lớn.','Work','B1');

INSERT INTO vocabulary (word, meaning, part_of_speech, example_sentence, example_meaning, topic, cefr_level, source)
SELECT word, meaning, part_of_speech, example_sentence, example_meaning, topic, cefr_level, 'curated'
FROM seed_vocab
ON CONFLICT DO NOTHING;

-- Gắn từ vào lesson theo từ (không theo id) để vẫn đúng nếu từ đã có sẵn trong kho chung
INSERT INTO lesson_vocab (lesson_id, vocab_id, is_new_word)
SELECT s.lesson_id, v.vocab_id, true
FROM seed_vocab s
JOIN lesson l ON l.lesson_id = s.lesson_id
JOIN vocabulary v ON lower(v.word) = lower(s.word)
                 AND coalesce(v.part_of_speech, '') = coalesce(s.part_of_speech, '')
                 AND v.owner_user_id IS NULL
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 3. Listening mẫu dạng văn bản (BR-37). Chưa có audio: audio_url = 'pending://'
--    -> job TTS sinh audio khi có ai.model.tts; app phải coi 'pending://' là chưa sẵn sàng.
--    voice_config để '{}' cho tới khi chọn model/giọng TTS.
-- ---------------------------------------------------------------------
INSERT INTO listening_clip (clip_id, topic, title, cefr_level, order_index, audio_url, transcript, status, created_by)
SELECT c.clip_id::uuid, c.topic, c.title, c.cefr_level, c.order_index, c.audio_url, c.transcript::jsonb, c.status,
       'ad000000-0000-4000-8000-000000000001'::uuid
FROM (VALUES
 ('c1100000-0000-4000-8000-000000000001', 'Travel', 'At the check-in desk', 'A2', 1, 'pending://',
  '[{"speaker":"Agent","en":"Good morning. Can I see your passport, please?","vi":"Chào buổi sáng. Cho tôi xem hộ chiếu của anh được không?"},
    {"speaker":"Traveller","en":"Sure, here you are.","vi":"Vâng, đây ạ."},
    {"speaker":"Agent","en":"Do you have any luggage to check in?","vi":"Anh có hành lý ký gửi không?"},
    {"speaker":"Traveller","en":"Just one bag. Is the flight on time?","vi":"Chỉ một túi thôi. Chuyến bay có đúng giờ không?"},
    {"speaker":"Agent","en":"There is a thirty-minute delay. Boarding starts at gate 12.","vi":"Chuyến bay trễ ba mươi phút. Lên máy bay ở cổng 12."}]', 'published'),
 ('c1100000-0000-4000-8000-000000000002', 'Work', 'Moving the team meeting', 'B1', 1, 'pending://',
  '[{"speaker":"Manager","en":"Hi Linh, can we move tomorrow''s meeting to Thursday?","vi":"Chào Linh, mình dời cuộc họp ngày mai sang thứ Năm được không?"},
    {"speaker":"Linh","en":"Thursday works for me, but the report deadline is Wednesday.","vi":"Thứ Năm thì được, nhưng hạn nộp báo cáo là thứ Tư."},
    {"speaker":"Manager","en":"Then please send me a draft on Wednesday afternoon.","vi":"Vậy chiều thứ Tư em gửi anh bản nháp nhé."},
    {"speaker":"Linh","en":"No problem. I will share it with the whole department.","vi":"Không vấn đề gì. Em sẽ gửi cho cả phòng."}]', 'published')
) AS c(clip_id, topic, title, cefr_level, order_index, audio_url, transcript, status)
ON CONFLICT DO NOTHING;

-- Câu hỏi MCQ của bài nghe (question_purpose = 'listening'; không cần embedding nên để active)
INSERT INTO question_bank (question_id, clip_id, question_text, question_type, question_purpose, payload,
                           topic, cefr_level, status, created_by) VALUES
 ('9b000000-0000-4000-8000-000000000101', 'c1100000-0000-4000-8000-000000000001',
  'What does the agent ask to see first?', 'mcq', 'listening',
  '{"options":[{"key":"a","text":"A ticket"},{"key":"b","text":"A passport"},{"key":"c","text":"A boarding pass"},{"key":"d","text":"A credit card"}],"answer":"b","explanation":"The agent asks to see the passport."}',
  'Travel', 'A2', 'active', 'ad000000-0000-4000-8000-000000000001'),
 ('9b000000-0000-4000-8000-000000000102', 'c1100000-0000-4000-8000-000000000001',
  'How many bags does the traveller check in?', 'mcq', 'listening',
  '{"options":[{"key":"a","text":"None"},{"key":"b","text":"One"},{"key":"c","text":"Two"},{"key":"d","text":"Three"}],"answer":"b","explanation":"The traveller says: Just one bag."}',
  'Travel', 'A2', 'active', 'ad000000-0000-4000-8000-000000000001'),
 ('9b000000-0000-4000-8000-000000000103', 'c1100000-0000-4000-8000-000000000001',
  'How long is the delay?', 'mcq', 'listening',
  '{"options":[{"key":"a","text":"Ten minutes"},{"key":"b","text":"Twenty minutes"},{"key":"c","text":"Thirty minutes"},{"key":"d","text":"One hour"}],"answer":"c","explanation":"The agent says there is a thirty-minute delay."}',
  'Travel', 'A2', 'active', 'ad000000-0000-4000-8000-000000000001'),
 ('9b000000-0000-4000-8000-000000000201', 'c1100000-0000-4000-8000-000000000002',
  'When will the meeting take place now?', 'mcq', 'listening',
  '{"options":[{"key":"a","text":"Tomorrow"},{"key":"b","text":"Wednesday"},{"key":"c","text":"Thursday"},{"key":"d","text":"Friday"}],"answer":"c","explanation":"The manager moves the meeting to Thursday."}',
  'Work', 'B1', 'active', 'ad000000-0000-4000-8000-000000000001'),
 ('9b000000-0000-4000-8000-000000000202', 'c1100000-0000-4000-8000-000000000002',
  'What is due on Wednesday?', 'mcq', 'listening',
  '{"options":[{"key":"a","text":"The report"},{"key":"b","text":"The contract"},{"key":"c","text":"The salary review"},{"key":"d","text":"The schedule"}],"answer":"a","explanation":"Linh says the report deadline is Wednesday."}',
  'Work', 'B1', 'active', 'ad000000-0000-4000-8000-000000000001'),
 ('9b000000-0000-4000-8000-000000000203', 'c1100000-0000-4000-8000-000000000002',
  'Who will Linh share the draft with?', 'mcq', 'listening',
  '{"options":[{"key":"a","text":"Only the manager"},{"key":"b","text":"A client"},{"key":"c","text":"The whole department"},{"key":"d","text":"Nobody"}],"answer":"c","explanation":"Linh will share it with the whole department."}',
  'Work', 'B1', 'active', 'ad000000-0000-4000-8000-000000000001')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- 4. Đề Writing: mỗi cặp topic + level 6 đề. embedding_vector NULL nên status = 'retired'
--    (chk_active_prompt_has_embedding). Job P6 tạo embedding rồi chuyển sang 'active'.
-- ---------------------------------------------------------------------
INSERT INTO question_bank (question_id, question_text, question_type, question_purpose, payload,
                           topic, cefr_level, status, created_by) VALUES
 -- Travel A2 (50-100 từ)
 ('9a000000-0000-4000-8000-000000000101', 'Write an email to a friend about your last holiday. Say where you went, how you travelled and what you liked most.', 'email', 'writing_prompt',
  '{"min_words":50,"max_words":100,"rubric_hint":"Past simple; mention place, transport and one favourite thing"}', 'Travel', 'A2', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 ('9a000000-0000-4000-8000-000000000102', 'Describe a place in your country that tourists should visit. Explain why it is special.', 'paragraph', 'writing_prompt',
  '{"min_words":50,"max_words":100,"rubric_hint":"Describe the place and give at least two reasons"}', 'Travel', 'A2', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 ('9a000000-0000-4000-8000-000000000103', 'Write a message to a hotel to book a room. Include the dates, the number of people and one question.', 'email', 'writing_prompt',
  '{"min_words":50,"max_words":100,"rubric_hint":"Polite request; dates, number of guests, one question"}', 'Travel', 'A2', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 ('9a000000-0000-4000-8000-000000000104', 'Tell the story of a problem you had on a trip, for example a delay or lost luggage. What happened and what did you do?', 'paragraph', 'writing_prompt',
  '{"min_words":50,"max_words":100,"rubric_hint":"Sequence words: first, then, finally"}', 'Travel', 'A2', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 ('9a000000-0000-4000-8000-000000000105', 'Do you prefer travelling by train or by plane? Give reasons for your answer.', 'paragraph', 'writing_prompt',
  '{"min_words":50,"max_words":100,"rubric_hint":"State a preference and compare using because and but"}', 'Travel', 'A2', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 ('9a000000-0000-4000-8000-000000000106', 'Write a short plan for a weekend trip: where you will go, what you will pack and what you will do each day.', 'paragraph', 'writing_prompt',
  '{"min_words":50,"max_words":100,"rubric_hint":"Future with will or going to; list items to pack"}', 'Travel', 'A2', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 -- Work B1 (80-150 từ)
 ('9a000000-0000-4000-8000-000000000201', 'Write an email to your manager asking to move a meeting. Explain why and suggest a new time.', 'email', 'writing_prompt',
  '{"min_words":80,"max_words":150,"rubric_hint":"Formal tone; reason and an alternative time"}', 'Work', 'B1', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 ('9a000000-0000-4000-8000-000000000202', 'Describe your ideal job. What would you do every day and why would you enjoy it?', 'essay', 'writing_prompt',
  '{"min_words":80,"max_words":150,"rubric_hint":"Use would; give reasons and examples"}', 'Work', 'B1', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 ('9a000000-0000-4000-8000-000000000203', 'Some people prefer working from home, others prefer the office. Which do you prefer? Give reasons.', 'essay', 'writing_prompt',
  '{"min_words":80,"max_words":150,"rubric_hint":"Opinion with two supporting reasons and a conclusion"}', 'Work', 'B1', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 ('9a000000-0000-4000-8000-000000000204', 'Write a short report about a project you finished. What was the goal, what went well and what was difficult?', 'paragraph', 'writing_prompt',
  '{"min_words":80,"max_words":150,"rubric_hint":"Past tenses; clear structure: goal, success, difficulty"}', 'Work', 'B1', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 ('9a000000-0000-4000-8000-000000000205', 'Write a cover letter for a part-time job. Describe your skills and experience.', 'email', 'writing_prompt',
  '{"min_words":80,"max_words":150,"rubric_hint":"Formal letter; skills linked to the job"}', 'Work', 'B1', 'retired', 'ad000000-0000-4000-8000-000000000001'),
 ('9a000000-0000-4000-8000-000000000206', 'Give advice to a new colleague on their first week at work. What should they do and avoid?', 'paragraph', 'writing_prompt',
  '{"min_words":80,"max_words":150,"rubric_hint":"Modal verbs: should, should not, it is a good idea to"}', 'Work', 'B1', 'retired', 'ad000000-0000-4000-8000-000000000001')
ON CONFLICT DO NOTHING;
