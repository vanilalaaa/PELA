-- =====================================================================
-- PELA - Personalized English Learning Application
-- Database schema v1.3  (PostgreSQL 15+ trên Supabase: Auth + pgvector)
-- v1.3 (13/10/2026): tích hợp Luồng Admin Portal 12/10/2026 - xem [ADMIN] trong chú thích.
--   RAG chỉ còn ở Writing ("Để AI tạo đề mới"); Challenge sinh theo luật từ Vocabulary;
--   Question Bank chỉ Listening + Writing, lưu là active ngay (bỏ duyệt 2 người - BR-34 cũ);
--   thêm import_job, rag_cache_serve, users.suspended_until/suspend_reason;
--   support_ticket -> app_report (Cài đặt > Báo cáo sự cố + "Lắc máy để báo cáo"; Admin Report tab App - A-13).
--
-- Nguồn: SRS Release 3.0 (14/09/2026) - mục 4.1 Logical Data Model,
--        bộ giao diện Figma (08/10/2026) và tài liệu API Endpoints.
-- Quy ước chú thích:
--   [SRS]      bảng/cột đúng như Data Dictionary trong SRS
--   [ADD: ...] bổ sung cần thiết để thoả một yêu cầu SRS cụ thể (ghi mã yêu cầu)
--   [UI: ...]  bổ sung để phục vụ màn hình trong bộ giao diện (ngoài SRS, đã được chọn giữ cho R1.0)
--   [D-n]      theo phương án số n đã chốt ngày 08/10/2026 trong PELA-Phuong-an-can-chot.md
-- Mọi con số hiển thị trên UI (số câu, số sao, ngưỡng huy hiệu, thời gian chờ...) chỉ là minh hoạ:
-- giá trị thật nằm ở system_setting / bảng badge để chỉnh mà không sửa code (MNT-3).
-- Lưu ý chung:
--   * PK dạng UUID (COM-2). Dùng varchar + CHECK thay cho ENUM của PostgreSQL
--     để thêm giá trị mới không cần migration phá vỡ (COM-1).
--   * Mật khẩu do Supabase Auth giữ trong auth.users (CO-3, BR-2) nên
--     public.users KHÔNG có cột password_hash.
--   * Embedding: Gemini gemini-embedding-001 gọi với outputDimensionality = 768 (text-embedding-004 đã bị ngừng). Nếu đổi model,
--     đổi số chiều và re-embed (xem question_bank.embedding_model).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS vector;       -- pgvector (SI-2.2)

-- ---------------------------------------------------------------------
-- 0. Hàm dùng chung
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END $$;

-- ---------------------------------------------------------------------
-- 1. ĐỊNH DANH & PHÂN QUYỀN
--    Release 1.0: một vai trò Admin phẳng (UC-17, Admin.AdminAccounts.FutureTiering).
--    Phân quyền = cột users.role, kiểm tra phía server trên mọi request (SEC-3).
-- ---------------------------------------------------------------------
CREATE TABLE users (                                              -- [SRS] USER
    user_id         uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name       varchar(100) NOT NULL,
    username        varchar(30)  UNIQUE,                          -- [ADD: Friend.Search.Query "unique username"]
    email           varchar(150) NOT NULL UNIQUE,                 -- BR-1 (bản sao của auth.users.email)
    avatar_url      varchar(255),
    role            varchar(16)  NOT NULL DEFAULT 'learner'
                    CHECK (role IN ('learner', 'admin')),         -- 'super_admin' = tương lai: chỉ cần sửa CHECK này
    status          varchar(16)  NOT NULL DEFAULT 'unverified'
                    CHECK (status IN ('unverified', 'active', 'suspended', 'pending_deletion')),
                                                                  -- [ADD: Auth.Register.Verify, Admin.Accounts.Suspend]
                                                                  -- 'pending_deletion' [UI: Xoá tài khoản; D-12, DI-3]
    full_name_changed_at timestamptz,                             -- [UI: "đổi lại sau 10 ngày"; cooldown ở system_setting]
    username_changed_at  timestamptz,                             -- [UI: "còn 12 ngày nữa mới đổi được"]
    deletion_requested_at timestamptz,                            -- [UI/D-12: thời điểm yêu cầu xoá; job xoá hẳn sau N ngày]
    native_language varchar(50),
    cefr_level      varchar(2) CHECK (cefr_level IN ('A1','A2','B1','B2','C1','C2')),
    timezone        varchar(50)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
                                                                  -- [ADD: BR-5, BR-11, BR-12 tính theo múi giờ learner]
    failed_login_count int NOT NULL DEFAULT 0,                    -- [ADD: Auth.Login.Lockout, SEC-5 - 5 lần sai / 15 phút]
    locked_until    timestamptz,                                  -- [ADD: khoá 15 phút; do backend đặt]
    suspended_until timestamptz,                                  -- [ADMIN C/G] NULL + status='suspended' = khoá vĩnh viễn
    suspend_reason  varchar(255),                                 -- [ADMIN C/G] lý do hiển thị cho learner khi đăng nhập
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CHECK (status = 'suspended' OR (suspended_until IS NULL AND suspend_reason IS NULL))
);
CREATE TRIGGER trg_users_updated BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE INDEX idx_users_admin_dir ON users(full_name) WHERE role = 'admin';   -- UC-17
CREATE INDEX idx_users_suspended_until ON users(suspended_until) WHERE status = 'suspended';   -- job tự mở khoá khi hết hạn

-- Tạo hồ sơ khi Supabase Auth tạo tài khoản. Vai trò LUÔN là 'learner':
-- tuyệt đối không đọc role từ raw_user_meta_data (client có thể tự điền).
-- Tài khoản admin được cấp thủ công (UC-15): UPDATE users SET role='admin' bằng service role.
CREATE OR REPLACE FUNCTION handle_new_auth_user() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  INSERT INTO public.users (user_id, full_name, email)
  VALUES (NEW.id,
          COALESCE(NULLIF(NEW.raw_user_meta_data->>'full_name', ''), split_part(NEW.email, '@', 1)),
          NEW.email);
  RETURN NEW;
END $$;
CREATE TRIGGER trg_auth_user_created AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION handle_new_auth_user();

CREATE OR REPLACE FUNCTION is_admin() RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (SELECT 1 FROM public.users
                 WHERE user_id = auth.uid() AND role = 'admin' AND status = 'active')
$$;

CREATE TABLE user_device (                                        -- [ADD: SI-4, CI-2 push FCM/APNs; SYNC_LOG.device_id]
    device_id   varchar(100) PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    platform    varchar(10) NOT NULL CHECK (platform IN ('android', 'ios')),
    push_token  varchar(512),
    last_seen_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_device_user ON user_device(user_id);

CREATE TABLE learner_profile (                                    -- [UI: khảo sát 5 câu; dùng để cá nhân hoá]
    user_id             uuid PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    occupation          varchar(20) CHECK (occupation IN ('high_school', 'university', 'working', 'other')),
    occupation_other    varchar(100),                             -- khi chọn "Khác"
    learning_goals      text[] NOT NULL DEFAULT '{}',             -- tối đa 3: school, certificate, daily_communication, travel, work
    interests           text[] NOT NULL DEFAULT '{}',             -- family, school, travel, food, sport, music, movie, tech, animal, health
                                                                  --  -> đưa vào ngữ cảnh prompt RAG/Writing để câu hỏi gần sở thích (D-15)
    daily_goal_minutes  int CHECK (daily_goal_minutes IN (5, 10, 20, 30)),
    self_assessed_level varchar(2) CHECK (self_assessed_level IN ('A1','A2','B1','B2','C1','C2')),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CHECK (cardinality(learning_goals) <= 3)
);

-- ---------------------------------------------------------------------
-- 2. ONBOARDING & PLACEMENT (UC-02, UC-03)
-- ---------------------------------------------------------------------
CREATE TABLE level_progress (                                     -- [SRS] LEVEL_PROGRESS
    user_id     uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    cefr_level  varchar(2) NOT NULL CHECK (cefr_level IN ('A1','A2','B1','B2','C1','C2')),
    status      varchar(16) NOT NULL CHECK (status IN ('locked', 'unlocked', 'completed')),
    PRIMARY KEY (user_id, cefr_level)
);

CREATE TABLE survey_question (                                    -- [ADD: Onboarding.Survey.Present, Admin.Roadmap.SurveyMapping]
    question_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    position    int NOT NULL UNIQUE,
    text        text NOT NULL,
    options     jsonb NOT NULL,                                   -- [{"key":"a","text":"..."}, ...]
    is_active   boolean NOT NULL DEFAULT true
);

CREATE TABLE survey_level_rule (                                  -- [ADD: AS-3 bảng map đáp án -> CEFR, Admin cấu hình được]
    rule_id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    answer_pattern jsonb NOT NULL,                                -- {"<question_id>":"<option key>"}; {} = luật mặc định
    cefr_level     varchar(2) NOT NULL CHECK (cefr_level IN ('A1','A2','B1','B2','C1','C2')),
    priority       int NOT NULL UNIQUE,                           -- khớp theo thứ tự ưu tiên -> đúng 1 kết quả
    is_active      boolean NOT NULL DEFAULT true
);

CREATE TABLE survey_response (                                    -- [ADD: UC-02 / sơ đồ A.1 - lưu kết quả định hướng]
    response_id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,
    answers        jsonb NOT NULL,
    oriented_level varchar(2) NOT NULL CHECK (oriented_level IN ('A1','A2','B1','B2','C1','C2')),
    created_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE placement_test_attempt (                             -- [SRS] PLACEMENT_TEST_ATTEMPT
    attempt_id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid NOT NULL UNIQUE REFERENCES users(user_id) ON DELETE CASCADE,  -- BR-19: chỉ 1 lần
    tested_level   varchar(2) NOT NULL CHECK (tested_level IN ('A1','A2','B1','B2','C1','C2')),
    score          int NOT NULL CHECK (score BETWEEN 0 AND 100),
    passed         boolean NOT NULL,
    oriented_level varchar(2),                                    -- [ADD: truy vết BR-19]
    placed_level   varchar(2),                                    -- [ADD: level cuối cùng được xếp]
    attempted_at   timestamptz NOT NULL DEFAULT now()
);

-- [UI: "Đổi cấp bậc" - bài kiểm tra lên cấp; D-5]
-- Đề cố định độ dài, rút phân tầng theo Topic + kỹ năng của cấp đích (không làm CAT/IRT).
-- Thời gian chờ giữa 2 lần thi và số câu nằm trong system_setting.
CREATE TABLE level_up_attempt (
    attempt_id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    from_level    varchar(2) NOT NULL CHECK (from_level IN ('A1','A2','B1','B2','C1','C2')),
    target_level  varchar(2) NOT NULL CHECK (target_level IN ('A1','A2','B1','B2','C1','C2')),
    question_ids  uuid[] NOT NULL,                                -- đề đã phát (chấm lại phía server, BR-23)
    status        varchar(12) NOT NULL DEFAULT 'in_progress'
                  CHECK (status IN ('in_progress', 'submitted', 'abandoned', 'expired')),
    score         int CHECK (score BETWEEN 0 AND 100),
    passed        boolean,
    started_at    timestamptz NOT NULL DEFAULT now(),
    deadline_at   timestamptz NOT NULL,                           -- started_at + thời gian làm bài
    submitted_at  timestamptz,
    CHECK (target_level > from_level)                             -- chỉ lên cấp; xuống cấp không cần thi (D-5)
);
CREATE INDEX idx_level_up_user_time ON level_up_attempt(user_id, started_at DESC);   -- kiểm tra thời gian chờ
CREATE UNIQUE INDEX uq_level_up_one_open ON level_up_attempt(user_id) WHERE status = 'in_progress';

-- ---------------------------------------------------------------------
-- 3. VOCABULARY ROADMAP (UC-06, UC-07, UC-18)
-- ---------------------------------------------------------------------
CREATE TABLE topic (                                              -- [SRS] TOPIC
    topic_id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    cefr_level  varchar(2) NOT NULL CHECK (cefr_level IN ('A1','A2','B1','B2','C1','C2')),
    name        varchar(100) NOT NULL,
    order_index int NOT NULL,
    status      varchar(16) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (cefr_level, order_index)
);

CREATE TABLE lesson (                                             -- [SRS] LESSON
    lesson_id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id       uuid NOT NULL REFERENCES topic(topic_id) ON DELETE CASCADE,
    order_index    int NOT NULL,
    new_word_count int NOT NULL CHECK (new_word_count >= 1),
    status         varchar(16) NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
    UNIQUE (topic_id, order_index)
);

CREATE TABLE vocabulary (                                         -- [SRS] VOCABULARY
    vocab_id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    word             varchar(100) NOT NULL,
    meaning          text NOT NULL,
    part_of_speech   varchar(20),                                 -- [UI: flashcard "danh từ"]
    ipa              varchar(100),
    example_sentence text,
    example_meaning  text,                                        -- [UI: dịch câu ví dụ trên flashcard]
    audio_url        varchar(255),
    image_url        varchar(255),                                -- [UI: hình minh hoạ flashcard]
    topic            varchar(100),                                -- thẻ chủ đề (tag), không phải FK
    cefr_level       varchar(2) CHECK (cefr_level IN ('A1','A2','B1','B2','C1','C2')),   -- [ADD: lọc "từ đáng học" ở Writing]
    difficulty_level int NOT NULL,
    -- [D-3] Từ do learner thêm từ bài Writing: thuộc riêng learner đó cho tới khi Admin duyệt vào kho chung
    source           varchar(10) NOT NULL DEFAULT 'curated' CHECK (source IN ('curated', 'writing')),
    owner_user_id    uuid REFERENCES users(user_id) ON DELETE CASCADE,   -- NULL = kho chung
    CHECK ((source = 'curated') = (owner_user_id IS NULL))
);
CREATE INDEX idx_vocabulary_word ON vocabulary(lower(word));
-- Một từ chỉ có 1 bản trong kho chung; mỗi learner tối đa 1 bản riêng cho cùng (từ, từ loại)
CREATE UNIQUE INDEX uq_vocab_curated ON vocabulary(lower(word), coalesce(part_of_speech, ''))
    WHERE owner_user_id IS NULL;
CREATE UNIQUE INDEX uq_vocab_private ON vocabulary(owner_user_id, lower(word), coalesce(part_of_speech, ''))
    WHERE owner_user_id IS NOT NULL;

CREATE TABLE lesson_vocab (                                       -- [SRS] LESSON_VOCAB
    lesson_id   uuid NOT NULL REFERENCES lesson(lesson_id) ON DELETE CASCADE,
    vocab_id    uuid NOT NULL REFERENCES vocabulary(vocab_id) ON DELETE RESTRICT,
    is_new_word boolean NOT NULL,
    PRIMARY KEY (lesson_id, vocab_id)
);
CREATE INDEX idx_lesson_vocab_vocab ON lesson_vocab(vocab_id);

CREATE TABLE lesson_progress (                                    -- [SRS] LESSON_PROGRESS
    progress_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    lesson_id    uuid NOT NULL REFERENCES lesson(lesson_id) ON DELETE CASCADE,
    status       varchar(16) NOT NULL CHECK (status IN ('not_started', 'in_progress', 'completed')),
    stars_earned int CHECK (stars_earned BETWEEN 0 AND 5),
    correct_count int,                                            -- [UI: "12/15 câu đúng", "80%"]
    total_count  int,
    resume_state jsonb,                                           -- [UI/API: thoát giữa chừng rồi học tiếp (ROB-1)]
    completed_at timestamptz,
    UNIQUE (user_id, lesson_id)
);

CREATE TABLE topic_progress (                                     -- [SRS] TOPIC_PROGRESS
    user_id                uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    topic_id               uuid NOT NULL REFERENCES topic(topic_id) ON DELETE CASCADE,
    status                 varchar(16) NOT NULL
                           CHECK (status IN ('available', 'in_progress', 'completed', 'locked')),
    stars_earned_since_lock int NOT NULL DEFAULT 0 CHECK (stars_earned_since_lock >= 0),
    unlock_cycle           int NOT NULL DEFAULT 1,                -- [ADD: BR-20 chu kỳ mở khoá; xem mục "điểm cần làm rõ"]
    locked_at              timestamptz,
    unlocked_at            timestamptz,
    completed_at           timestamptz,
    PRIMARY KEY (user_id, topic_id)
);

CREATE TABLE final_test_attempt (                                 -- [SRS] FINAL_TEST_ATTEMPT (SRS in trùng cột attempt_id, chỉ giữ 1)
    attempt_id     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    topic_id       uuid NOT NULL REFERENCES topic(topic_id) ON DELETE CASCADE,
    unlock_cycle   int NOT NULL DEFAULT 1,                        -- [ADD: BR-20]
    attempt_number int NOT NULL CHECK (attempt_number BETWEEN 1 AND 3),
    score          int NOT NULL CHECK (score BETWEEN 0 AND 100),
    passed         boolean NOT NULL,
    attempted_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, topic_id, unlock_cycle, attempt_number)
);                                                                -- giữ suốt đời tài khoản (DI-5)

-- ---------------------------------------------------------------------
-- 4. QUESTION BANK + VECTOR STORE CHO RAG
-- ---------------------------------------------------------------------
-- [ADMIN E/F] Mọi lần nhập nội dung (CSV lộ trình, audio Listening, file đề Writing) là 1 job bất đồng bộ:
--   upload file lên Supabase Storage -> worker (STT / LLM trích xuất) -> preview cho Admin sửa -> Lưu.
--   Job chỉ giữ bản xem trước; lưu xong mới ghi vào bảng nội dung thật (topic/lesson/vocabulary, listening_clip, question_bank).
CREATE TABLE import_job (
    job_id       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    kind         varchar(16) NOT NULL CHECK (kind IN ('roadmap_csv', 'listening_audio', 'writing_file')),
    files        jsonb NOT NULL,                                  -- [{"path":"<Supabase Storage, bucket riêng tư>","bytes":..}]
                                                                  -- Listening được upload nhiều audio một lần; Writing 1 file nhiều đề
    total_bytes  int NOT NULL CHECK (total_bytes > 0),            -- giới hạn từng file ở system_setting import.max_*_mb
    attempts     int NOT NULL DEFAULT 0,                          -- [ADMIN F] "Xử lý lại" = đưa job về queued, attempts + 1
    -- Listening xem trước 2 bước (P-3): transcript_ready (Admin sửa transcript) -> sinh câu hỏi -> preview_ready
    status       varchar(16) NOT NULL DEFAULT 'queued'
                 CHECK (status IN ('queued', 'processing', 'transcript_ready', 'preview_ready', 'saved', 'failed', 'cancelled')),
    preview      jsonb,                                           -- kết quả STT/LLM/parse CSV, Admin sửa trực tiếp trên đây
    warnings     jsonb,                                           -- dòng CSV lỗi, đề trùng (pgvector), câu thiếu đáp án...
    error        text,
    created_by   uuid NOT NULL REFERENCES users(user_id),
    created_at   timestamptz NOT NULL DEFAULT now(),
    finished_at  timestamptz
);
CREATE INDEX idx_import_job_admin ON import_job(created_by, created_at DESC);
CREATE INDEX idx_import_job_queue ON import_job(created_at) WHERE status IN ('queued', 'processing');
CREATE INDEX idx_import_job_open  ON import_job(kind, created_at DESC) WHERE status IN ('transcript_ready', 'preview_ready');

-- [UI: Luyện nghe theo chủ đề -> "bài nghe" (1 đoạn audio, nhiều câu hỏi, có transcript, tốc độ 0.5x-1.5x)]
-- SRS chỉ có LISTENING_SESSION/ATTEMPT gắn thẳng câu hỏi; thêm bảng clip để gom câu hỏi theo bài nghe.
-- [ADMIN F] Admin upload audio -> STT ra transcript -> LLM sinh MCQ -> preview/sửa -> Lưu là published ngay.
CREATE TABLE listening_clip (
    clip_id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    topic        varchar(100) NOT NULL,                           -- Daily life, Family, ...
    title        varchar(150) NOT NULL,
    cefr_level   varchar(2) NOT NULL CHECK (cefr_level IN ('A1','A2','B1','B2','C1','C2')),
    difficulty_level int NOT NULL DEFAULT 1,
    order_index  int NOT NULL,
    audio_url    varchar(255) NOT NULL,
    duration_sec int,
    transcript   jsonb NOT NULL,                                  -- [{"speaker":"Linh","en":"...","vi":"..."}] - learner xem sau khi trả lời
    status       varchar(16) NOT NULL DEFAULT 'published' CHECK (status IN ('published', 'retired')),
    import_job_id uuid REFERENCES import_job(job_id) ON DELETE SET NULL,
    created_by   uuid REFERENCES users(user_id),
    created_at   timestamptz NOT NULL DEFAULT now(),
    retired_at   timestamptz,
    UNIQUE (cefr_level, topic, order_index)
);

CREATE TABLE question_bank (                                      -- [SRS] QUESTION_BANK
    question_id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clip_id          uuid REFERENCES listening_clip(clip_id) ON DELETE CASCADE,   -- [UI] câu hỏi thuộc bài nghe nào
    question_text    text NOT NULL,
    question_type    varchar(20) NOT NULL,                        -- listening: mcq | writing_prompt: essay/email/paragraph...
    -- [ADMIN F] Bỏ trường "Mục đích sử dụng" trên màn Admin: hệ thống tự gán theo màn nhập.
    --   writing_prompt : đề Writing. Vừa là đề phát thẳng cho learner, vừa là nguồn RAG tìm "đề tương tự" (BR-8)
    --   listening      : câu hỏi của 1 listening_clip
    --   placement / final_test / level_up : nhập qua CSV lộ trình (màn E), không nằm trong màn Question Bank
    --   ('rag_template' và 'static_fallback' của v1.2 bị bỏ: RAG không còn sinh câu cho Challenge)
    question_purpose varchar(20) NOT NULL
                     CHECK (question_purpose IN ('writing_prompt', 'listening', 'placement', 'final_test', 'level_up')),
    payload          jsonb NOT NULL DEFAULT '{}'::jsonb,          -- listening: options[4], answer, explanation
                                                                  -- writing: min_words, max_words, rubric_hint (gợi ý cho tiêu chí task)
    embedding_vector vector(768),                                 -- chỉ writing_prompt cần (RAG + phát hiện đề trùng)
    embedding_model  varchar(60),                                 -- [ADD: biết khi nào cần re-embed]
    topic            varchar(100) NOT NULL,
    cefr_level       varchar(2)   NOT NULL CHECK (cefr_level IN ('A1','A2','B1','B2','C1','C2')),
    difficulty_level int NOT NULL,
    -- [ADMIN F] Không còn pending_approval: bản xem trước (import_job.preview) chính là bước kiểm tra; Lưu = active
    status           varchar(10) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'retired')),
    import_job_id    uuid REFERENCES import_job(job_id) ON DELETE SET NULL,
    created_by       uuid NOT NULL REFERENCES users(user_id),
    retired_at       timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CHECK (question_purpose <> 'listening' OR clip_id IS NOT NULL),
    -- Đề Writing đang dùng phải có embedding thì RAG mới tìm thấy (embed ngay khi Lưu, cùng transaction)
    CONSTRAINT chk_active_prompt_has_embedding CHECK (
        NOT (status = 'active' AND question_purpose = 'writing_prompt' AND embedding_vector IS NULL))
);
-- Chỉ index đề Writing đang dùng: đề retired không làm phình index, Top-K chỉ quét phần cần thiết
CREATE INDEX idx_qb_embedding_hnsw ON question_bank
    USING hnsw (embedding_vector vector_cosine_ops)
    WHERE status = 'active' AND question_purpose = 'writing_prompt';
CREATE INDEX idx_qb_filter  ON question_bank(question_purpose, status, cefr_level, topic, difficulty_level);
CREATE INDEX idx_qb_clip    ON question_bank(clip_id) WHERE clip_id IS NOT NULL;

-- [P-13] R1.0 sinh Final Test theo luật từ Vocabulary của Topic -> bảng này KHÔNG dùng; giữ để mở rộng sau.
CREATE TABLE topic_final_test_pool (                              -- [ADD: Admin.Roadmap.FinalTest cấu hình pool 20 câu ngẫu nhiên]
    topic_id    uuid NOT NULL REFERENCES topic(topic_id) ON DELETE CASCADE,
    question_id uuid NOT NULL REFERENCES question_bank(question_id) ON DELETE RESTRICT,
    PRIMARY KEY (topic_id, question_id)
);

-- [ADMIN] Đề Writing do AI sinh CHỈ nằm ở đây (TTL 30 ngày), KHÔNG bao giờ ghi vào question_bank.
-- Khoá tra cache = (topic, cefr_level, question_type) + độ tương đồng embedding truy vấn; v1.2 dùng vocab_id (bỏ).
CREATE TABLE rag_question_cache (                                 -- [SRS] RAG_QUESTION_CACHE (BR-24, BR-25)
    cache_id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    query_embedding    vector(768) NOT NULL,                      -- embedding của truy vấn (chủ đề + cấp độ + sở thích learner)
    topic              varchar(100) NOT NULL,
    cefr_level         varchar(2)   NOT NULL,
    difficulty_level   int NOT NULL,
    question_type      varchar(20) NOT NULL DEFAULT 'writing_prompt',
    generated_question jsonb NOT NULL,                            -- đề đã qua bộ lọc BR-15: prompt, min_words, max_words
    source_template_id uuid REFERENCES question_bank(question_id),-- Top-1 template (BR-25: retire template -> expire cache)
    hit_count          int NOT NULL DEFAULT 0 CHECK (hit_count >= 0),
    created_at         timestamptz NOT NULL DEFAULT now(),
    last_hit_at        timestamptz,
    expires_at         timestamptz NOT NULL,                      -- [ADD: created_at + TTL (AS-9, mặc định 30 ngày)]
    status             varchar(10) NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired'))
);
-- Entry 'expired' vẫn giữ để audit/báo cáo cache-hit-rate nhưng không nằm trong index -> giữ index nhỏ (COM-3, PER-9)
CREATE INDEX idx_rag_cache_embedding_hnsw ON rag_question_cache
    USING hnsw (query_embedding vector_cosine_ops) WHERE status = 'active';
CREATE INDEX idx_rag_cache_key      ON rag_question_cache(topic, cefr_level, question_type) WHERE status = 'active';
CREATE INDEX idx_rag_cache_template ON rag_question_cache(source_template_id);
CREATE INDEX idx_rag_cache_expiry   ON rag_question_cache(expires_at) WHERE status = 'active';

-- BR-24 "không phát lại cùng một đề cho cùng learner trong 14 ngày": ghi mỗi lần phát đề từ cache/AI.
-- (v1.2 dựa vào challenge_item.rag_cache_id; Challenge không còn dùng RAG nên tách bảng riêng)
CREATE TABLE rag_cache_serve (
    cache_id  uuid NOT NULL REFERENCES rag_question_cache(cache_id) ON DELETE CASCADE,
    user_id   uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    served_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (cache_id, user_id, served_at)
);
CREATE INDEX idx_rag_serve_user ON rag_cache_serve(user_id, served_at DESC);

-- ---------------------------------------------------------------------
-- 5. SM-2 REVIEW POOL (UC-07 graduation, UC-08/09, UC-21/22)
-- ---------------------------------------------------------------------
CREATE TABLE review_card (                                        -- [SRS] REVIEW_CARD
    card_id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                    uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    vocab_id                   uuid NOT NULL REFERENCES vocabulary(vocab_id) ON DELETE CASCADE,
    easiness_factor            numeric(4,2) NOT NULL DEFAULT 2.50 CHECK (easiness_factor >= 1.30),   -- BR-4
    interval_days              int NOT NULL DEFAULT 0 CHECK (interval_days >= 0),
    repetition_count           int NOT NULL DEFAULT 0 CHECK (repetition_count >= 0),
    error_count                int NOT NULL DEFAULT 0 CHECK (error_count >= 0),
    next_review_date           date NOT NULL DEFAULT current_date,
    last_reviewed_at           timestamptz,
    status                     varchar(10) NOT NULL DEFAULT 'new'
                               CHECK (status IN ('new', 'learning', 'review', 'lapsed', 'mastered')),
    consecutive_incorrect_count int NOT NULL DEFAULT 0 CHECK (consecutive_incorrect_count >= 0),
    needs_reinforcement        boolean NOT NULL DEFAULT false,    -- BR-26
    origin                     varchar(10) NOT NULL DEFAULT 'topic'
                               CHECK (origin IN ('topic', 'writing')),   -- [ADD: BR-9 thẻ đến từ bài Writing; báo cáo]
    in_pool                    boolean NOT NULL DEFAULT false,    -- [ADD: error_count tích luỹ ở Lesson Practice TRƯỚC khi thẻ vào pool
                                                                  --  (BR-21, BR-20); chỉ thẻ in_pool=true mới được xếp lịch/“đến hạn”]
    created_at                 timestamptz NOT NULL DEFAULT now(),
    updated_at                 timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, vocab_id)
);
CREATE TRIGGER trg_review_card_updated BEFORE UPDATE ON review_card
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
-- Phục vụ endpoint "thẻ đến hạn hôm nay" (GET /v1/review-cards/due)
CREATE INDEX idx_review_card_due ON review_card(user_id, next_review_date) WHERE in_pool;
CREATE INDEX idx_review_card_reinforce ON review_card(user_id) WHERE in_pool AND needs_reinforcement;

-- ---------------------------------------------------------------------
-- 6. CHALLENGE / LISTENING / WRITING
-- ---------------------------------------------------------------------
CREATE TABLE challenge_session (                                  -- [SRS] CHALLENGE_SESSION
    challenge_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    challenge_type varchar(10) NOT NULL CHECK (challenge_type IN ('daily', 'weekly')),
    challenge_date date NOT NULL,                                 -- ngày theo múi giờ learner (BR-5)
    status         varchar(16) NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'completed')),
    stars_earned   int,                                           -- NULL tới khi hoàn thành
    generated_at   timestamptz NOT NULL DEFAULT now(),            -- [ADD]
    completed_at   timestamptz,                                   -- [ADD]
    UNIQUE (user_id, challenge_date),                             -- BR-5: mỗi learner tối đa 1 challenge/ngày
    CHECK (stars_earned IS NULL OR
           (challenge_type = 'daily'  AND stars_earned BETWEEN 0 AND 5) OR
           (challenge_type = 'weekly' AND stars_earned BETWEEN 0 AND 6))
);

CREATE TABLE challenge_item (                                     -- [SRS] CHALLENGE_ITEM
    item_id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_id   uuid NOT NULL REFERENCES challenge_session(challenge_id) ON DELETE CASCADE,
    vocab_id       uuid NOT NULL REFERENCES vocabulary(vocab_id),
    -- [ADMIN] Challenge sinh THEO LUẬT từ dữ liệu Vocabulary (word, meaning, example, audio) - không gọi AI/RAG,
    -- nên sinh được ngay trên máy khi offline và chấm tất định trên server.
    question_type  varchar(20) NOT NULL
                   CHECK (question_type IN ('mcq', 'fill_blank', 'word_order', 'spelling', 'listen_choose')),
                                                                  -- bỏ pronunciation (DE-3), sentence_build (cần AI chấm), drag_drop (= word_order)
    is_correct     boolean,                                       -- NULL tới khi trả lời
    position       int NOT NULL,                                  -- [ADD: thứ tự hiển thị]
    source         varchar(12) NOT NULL CHECK (source IN ('due_review', 'weak', 'new_vocab')),
                                                                  -- thẻ đến hạn (SM2.DueList) / thẻ yếu (BR-6, BR-26) / từ mới
    question_payload jsonb NOT NULL,                              -- [ADD: snapshot câu hỏi đã phát cho learner (ổn định, dùng offline)]
    user_answer    jsonb,                                         -- [ADD]
    answered_at    timestamptz,                                   -- [ADD]
    UNIQUE (challenge_id, position)
);
CREATE INDEX idx_challenge_item_vocab ON challenge_item(vocab_id);

CREATE TABLE writing_submission (                                 -- [SRS] WRITING_SUBMISSION
    submission_id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    prompt_question_id uuid REFERENCES question_bank(question_id),-- [ADD: Writing.Prompt.Select lấy đề từ Question Bank]
    prompt_source    varchar(12) NOT NULL DEFAULT 'bank'
                     CHECK (prompt_source IN ('bank', 'ai_generated', 'freeform')),   -- [ADMIN] đề từ kho / AI tạo (RAG) / tự do
    rag_cache_id     uuid REFERENCES rag_question_cache(cache_id) ON DELETE SET NULL, -- đề AI tạo nằm ở cache nào
    prompt_text      text,                                        -- snapshot đề (cache hết hạn vẫn đọc lại được); NULL nếu tự do
    submitted_text   text NOT NULL,
    word_count       int NOT NULL,
    -- Vòng đời: draft -> pending -> graded | retry_queued -> graded | failed
    --   draft        [UI: "Lưu nháp", và khi hết lượt chấm bài vẫn được lưu nháp]
    --   pending      đã nộp, đang chấm (UI: "Chấm sau, mình làm bài khác")
    --   retry_queued UC-23 E1: timeout/JSON sai 2 lần -> job chấm lại
    --   failed       hết số lần thử -> không trừ lượt chấm (D-9)
    grading_status   varchar(12) NOT NULL DEFAULT 'pending'
                     CHECK (grading_status IN ('draft', 'pending', 'graded', 'retry_queued', 'failed')),
    score            int CHECK (score BETWEEN 0 AND 100),         -- tổng 0-100 (BR-18). UI hiển thị /10 = score/10
    criteria_scores  jsonb,                                       -- {"task":..,"coherence":..,"vocabulary":..,"grammar":..} mỗi mục 0-25
    stars_earned     int CHECK (stars_earned BETWEEN 0 AND 5),
    ai_feedback      jsonb,                                       -- toàn bộ JSON AI trả về (đã kiểm tra + lọc BR-15): overall_comment,
                                                                  --  corrected_text, rewrites[]... Lỗi chi tiết tách ra writing_issue
    prompt_version   int,                                         -- [ADD: MNT-4 bài được chấm bằng prompt version nào]
    model            varchar(60),
    grading_attempts int NOT NULL DEFAULT 0,                      -- [ADD]
    next_retry_at    timestamptz,                                 -- [ADD]
    created_at       timestamptz NOT NULL DEFAULT now(),
    submitted_at     timestamptz,                                 -- NULL khi còn là nháp
    graded_at        timestamptz,
    CHECK (grading_status = 'draft' OR (word_count BETWEEN 20 AND 300 AND submitted_at IS NOT NULL)),
    CHECK ((prompt_source = 'bank') = (prompt_question_id IS NOT NULL)),
    CHECK (prompt_source <> 'freeform' OR prompt_text IS NULL)
                                                                  -- Writing.Submit.Limit; đề có thể hẹp hơn (payload.min_words/max_words)
);
CREATE INDEX idx_writing_user_time ON writing_submission(user_id, submitted_at DESC);   -- lịch sử + đếm lượt chấm/ngày
CREATE INDEX idx_writing_retry ON writing_submission(next_retry_at) WHERE grading_status = 'retry_queued';
-- DI-1: giữ 12 tháng -> job định kỳ xoá WHERE submitted_at < now() - interval '12 months' (cascade sang 2 bảng dưới)

-- Mỗi lỗi / gợi ý là 1 dòng: tô màu trên bài gốc, thống kê "lỗi hay gặp" theo thời gian (D-8)
CREATE TABLE writing_issue (                                      -- [ADD: UC-23 "per-sentence errors" dạng chuẩn hoá]
    issue_id       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id  uuid NOT NULL REFERENCES writing_submission(submission_id) ON DELETE CASCADE,
    sentence_index int NOT NULL,                                  -- câu thứ mấy (0-based) - server tự tách câu
    start_offset   int NOT NULL,                                  -- vị trí ký tự trong submitted_text, do SERVER tính
    end_offset     int NOT NULL,                                  --  (tìm chuỗi original), không tin offset của LLM
    original       text NOT NULL,
    suggestion     text NOT NULL,                                 -- rỗng = nên xoá đoạn này
    category       varchar(16) NOT NULL CHECK (category IN (
                       'grammar',      -- sai ngữ pháp: thì, chia động từ, mạo từ, số ít/nhiều...
                       'spelling',     -- sai chính tả
                       'word_choice',  -- dùng từ chưa phù hợp / sai collocation / sai nghĩa
                       'repetition',   -- lặp từ (phát hiện bằng luật + AI đề xuất từ thay thế)
                       'punctuation',
                       'style')),      -- câu đúng nhưng chưa tự nhiên -> gợi ý (không trừ điểm)
    severity       varchar(10) NOT NULL CHECK (severity IN ('error', 'suggestion')),
    explanation_vi text NOT NULL,
    detected_by    varchar(6) NOT NULL DEFAULT 'llm' CHECK (detected_by IN ('llm', 'rule')),
    CHECK (end_offset > start_offset),
    CHECK (category <> 'style' OR severity = 'suggestion')
);
CREATE INDEX idx_writing_issue_sub ON writing_issue(submission_id, start_offset);

-- "Từ đáng học" AI rút ra từ bài viết (tối đa N từ/bài, N trong system_setting) - D-3
CREATE TABLE writing_vocab_suggestion (
    suggestion_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id  uuid NOT NULL REFERENCES writing_submission(submission_id) ON DELETE CASCADE,
    user_id        uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    issue_id       uuid REFERENCES writing_issue(issue_id) ON DELETE SET NULL,  -- lỗi đã sinh ra gợi ý này
    learner_form   varchar(100) NOT NULL,                         -- learner đã viết: "advices", "make a photo"
    word           varchar(100) NOT NULL,                         -- dạng đúng/đáng học: "advice", "take a photo"
    reason         varchar(16) NOT NULL
                   CHECK (reason IN ('misspelled', 'misused', 'wrong_form', 'better_alternative')),
    meaning_vi     text NOT NULL,
    example_sentence text,
    cefr_level     varchar(2),
    vocab_id       uuid REFERENCES vocabulary(vocab_id),          -- khớp được kho chung/kho riêng
    status         varchar(10) NOT NULL DEFAULT 'suggested'
                   CHECK (status IN ('suggested', 'added', 'dismissed', 'auto_added')),
                                                                  -- auto_added: từ đã có trong kho -> BR-9 tự đẩy vào SM-2
    review_card_id uuid REFERENCES review_card(card_id) ON DELETE SET NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (submission_id, word)
);
CREATE INDEX idx_wvs_user ON writing_vocab_suggestion(user_id, status);

CREATE TABLE listening_session (                                  -- [SRS] LISTENING_SESSION
    session_id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    clip_id         uuid NOT NULL REFERENCES listening_clip(clip_id),   -- [UI: mỗi phiên = 1 bài nghe]
    total_questions int NOT NULL CHECK (total_questions >= 1),
    correct_count   int NOT NULL DEFAULT 0 CHECK (correct_count >= 0),
    stars_earned    int CHECK (stars_earned BETWEEN 0 AND 5),
    started_at      timestamptz NOT NULL DEFAULT now(),
    completed_at    timestamptz,
    CHECK (correct_count <= total_questions)
);
CREATE INDEX idx_listening_user_clip ON listening_session(user_id, clip_id);   -- "2/8 đã nghe" 

CREATE TABLE listening_attempt (                                  -- [SRS] LISTENING_ATTEMPT
    attempt_id      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      uuid NOT NULL REFERENCES listening_session(session_id) ON DELETE CASCADE,
    question_id     uuid NOT NULL REFERENCES question_bank(question_id),
    selected_answer varchar(255) NOT NULL,
    is_correct      boolean NOT NULL,
    attempted_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE answer_log (                                         -- [ADD: BR-23/Offline.Sync.AntiFraud - server tính lại từ bản ghi đáp án thô]
    answer_id       uuid PRIMARY KEY,                             -- do client sinh (idempotent khi sync lại)
    user_id         uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    context_type    varchar(16) NOT NULL CHECK (context_type IN ('lesson', 'final_test', 'placement', 'level_up')),
    context_id      uuid NOT NULL,                                -- lesson_id | topic_id | placement / level_up attempt
    vocab_id        uuid REFERENCES vocabulary(vocab_id),
    question_id     uuid REFERENCES question_bank(question_id),
    attempt_no      int NOT NULL DEFAULT 1,                       -- lần thứ n trên cùng item (BR-21: 3 lần sai liên tiếp)
    is_correct      boolean NOT NULL,
    answered_at     timestamptz NOT NULL,                         -- giờ thiết bị
    received_at     timestamptz NOT NULL DEFAULT now()            -- giờ server (last-write-wins)
);
CREATE INDEX idx_answer_log_user_ctx ON answer_log(user_id, context_type, context_id);

-- ---------------------------------------------------------------------
-- 7. GAMIFICATION & SOCIAL (UC-04, 05, 12, 13)
-- ---------------------------------------------------------------------
CREATE TABLE streak (                                             -- [SRS] STREAK
    user_id            uuid PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    current_streak     int NOT NULL DEFAULT 0 CHECK (current_streak >= 0),
    longest_streak     int NOT NULL DEFAULT 0 CHECK (longest_streak >= 0),
    -- [D-2] Thay Streak Freeze của SRS bằng "Khôi phục streak" như UI: N lượt/tháng (system_setting),
    -- chỉ khôi phục được khi lỡ ĐÚNG 1 ngày và phải bấm trước 23:59 ngày hôm sau.
    restores_used      int NOT NULL DEFAULT 0 CHECK (restores_used >= 0),
    restores_month     date,                                      -- ngày 01 của tháng đang đếm (local)
    broken_streak      int,                                       -- độ dài chuỗi vừa đứt, chờ khôi phục
    broken_on          date,                                      -- ngày bị lỡ
    last_active_date   date
);

CREATE TABLE streak_day (                                         -- [UI: lịch tuần T2..CN, /streak/history]
    user_id    uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    local_date date NOT NULL,
    kind       varchar(8) NOT NULL CHECK (kind IN ('active', 'restored')),
    PRIMARY KEY (user_id, local_date)
);

CREATE TABLE badge (                                              -- [SRS] BADGE (+ phân nhóm/cấp theo UI)
    badge_id    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(100) NOT NULL UNIQUE,
    description varchar(255) NOT NULL,
    icon_url    varchar(255) NOT NULL,
    criteria    varchar(255) NOT NULL,                            -- [SRS] mô tả đọc được, sinh từ metric+threshold
    category    varchar(16) NOT NULL CHECK (category IN
                ('first_steps', 'streak', 'vocabulary', 'topic', 'writing', 'listening', 'social')),   -- [UI: 7 tab]
    tier        int NOT NULL CHECK (tier BETWEEN 1 AND 4),        -- [UI: Cấp 1-4]
    metric      varchar(32) NOT NULL,                             -- tên cột trong user_stats
    threshold   int NOT NULL CHECK (threshold > 0),
    is_active   boolean NOT NULL DEFAULT true,
    UNIQUE (category, tier)
);

CREATE TABLE user_badge (                                         -- [SRS] USER_BADGE
    user_id      uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    badge_id     uuid NOT NULL REFERENCES badge(badge_id) ON DELETE CASCADE,
    is_displayed boolean NOT NULL DEFAULT false,
    earned_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, badge_id)
);
-- BR-22: tối đa 3 huy hiệu hiển thị
CREATE OR REPLACE FUNCTION enforce_displayed_badge_limit() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.is_displayed AND (SELECT count(*) FROM user_badge
        WHERE user_id = NEW.user_id AND is_displayed
          AND badge_id <> NEW.badge_id) >= 3 THEN
    RAISE EXCEPTION 'A learner may display at most 3 badges (BR-22)' USING ERRCODE = '23514';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER trg_user_badge_limit BEFORE INSERT OR UPDATE OF is_displayed ON user_badge
    FOR EACH ROW EXECUTE FUNCTION enforce_displayed_badge_limit();

-- Bộ đếm dồn cho huy hiệu + hồ sơ: cập nhật trong cùng transaction với hành động, tránh COUNT(*) mỗi lần mở app
CREATE TABLE user_stats (                                         -- [UI: "Đã đạt 3/4", hồ sơ; Gamify.Badges.Criteria]
    user_id               uuid PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    lessons_completed     int NOT NULL DEFAULT 0,
    words_mastered        int NOT NULL DEFAULT 0,                 -- [D-7] "thuộc" = review_card.status IN ('review','mastered')
    final_tests_passed    int NOT NULL DEFAULT 0,
    writings_submitted    int NOT NULL DEFAULT 0,                 -- chỉ bài đã chấm xong
    listening_completed   int NOT NULL DEFAULT 0,
    daily_challenges_done int NOT NULL DEFAULT 0,
    friends_count         int NOT NULL DEFAULT 0,
    star_total            int NOT NULL DEFAULT 0,                 -- tổng sao tích luỹ (không tiêu được - D-1)
    updated_at            timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE friendship (                                         -- [SRS] FRIENDSHIP
    friendship_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    friend_id     uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    status        varchar(16) NOT NULL CHECK (status IN ('pending', 'accepted', 'rejected')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    CHECK (user_id <> friend_id)
);
-- Friend.Request.Duplicate: chặn yêu cầu thứ 2 giữa cùng một cặp, kể cả chiều ngược lại
CREATE UNIQUE INDEX uq_friendship_pair ON friendship (LEAST(user_id, friend_id), GREATEST(user_id, friend_id))
    WHERE status IN ('pending', 'accepted');
CREATE INDEX idx_friendship_friend ON friendship(friend_id, status);

-- [UI: Chặn / Báo cáo người dùng] - ngoài SRS, đã chọn giữ (D-6)
CREATE TABLE user_block (
    blocker_id uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    blocked_id uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_user_block_blocked ON user_block(blocked_id);
-- Chặn = huỷ kết bạn + ẩn khỏi tìm kiếm, gợi ý, bảng xếp hạng Bạn bè, hồ sơ (cả 2 chiều). Làm ở Application layer.

CREATE TABLE user_report (
    report_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id uuid REFERENCES users(user_id) ON DELETE SET NULL,
    target_id   uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    reason      varchar(24) NOT NULL CHECK (reason IN
                ('spam', 'offensive_name', 'impersonation', 'inappropriate_avatar', 'other')),   -- [UI: 5 lý do]
    note        varchar(200),                                     -- [UI: "Khác" 0/200]
    status      varchar(12) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'actioned', 'dismissed')),
    -- [ADMIN G] Ban N ngày / Ban vĩnh viễn (=> users.status='suspended') hoặc Bỏ qua (=> dismissed)
    action      varchar(14) CHECK (action IN ('ban_temporary', 'ban_permanent')),
    ban_days    int CHECK (ban_days > 0),
    resolved_by uuid REFERENCES users(user_id),
    resolved_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (reason <> 'other' OR note IS NOT NULL),
    CHECK ((status = 'actioned') = (action IS NOT NULL)),
    CHECK ((action = 'ban_temporary') = (ban_days IS NOT NULL))
);
CREATE INDEX idx_user_report_open ON user_report(status, created_at) WHERE status = 'open';
CREATE UNIQUE INDEX uq_user_report_open_pair ON user_report(reporter_id, target_id) WHERE status = 'open';

CREATE TABLE star_event (                                         -- [ADD: sổ cái Star - BR-10/12/20, báo cáo PELA-RPT-1 "Stars theo nguồn"]
    event_id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    source          varchar(10) NOT NULL CHECK (source IN ('lesson', 'daily', 'weekly', 'writing', 'listening')),
    source_id       uuid NOT NULL,                                -- id phiên/bài nộp tương ứng
    stars           int NOT NULL CHECK (stars BETWEEN 1 AND 6),
    earned_at       timestamptz NOT NULL DEFAULT now(),
    local_date      date NOT NULL,                                -- ngày theo múi giờ learner
    local_week_start date NOT NULL,                               -- thứ Hai 00:00 local (BR-12)
    UNIQUE (source, source_id)                                    -- BR-10: mỗi phiên chấm Star đúng 1 lần
);
CREATE INDEX idx_star_event_week ON star_event(local_week_start, user_id);
CREATE INDEX idx_star_event_user_time ON star_event(user_id, earned_at DESC);

CREATE TABLE leaderboard_entry (                                  -- [SRS] LEADERBOARD_ENTRY (bảng vật chất hoá, tính lại ≥ mỗi 15 phút)
    user_id      uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    period       varchar(16) NOT NULL CHECK (period IN ('weekly', 'all_time')),
    period_start date NOT NULL DEFAULT date '1970-01-01',         -- [ADD: all_time dùng 1970-01-01; weekly = thứ Hai]
    star_total   int NOT NULL DEFAULT 0 CHECK (star_total >= 0),
    rank         int NOT NULL CHECK (rank >= 1),
    computed_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, period, period_start)
);
CREATE INDEX idx_leaderboard_top ON leaderboard_entry(period, period_start, rank);

CREATE TABLE user_daily_activity (                                -- [ADD: Admin.Analytics DAU]
    user_id       uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    activity_date date NOT NULL,
    PRIMARY KEY (user_id, activity_date)
);
CREATE INDEX idx_daily_activity_date ON user_daily_activity(activity_date);

-- ---------------------------------------------------------------------
-- 7b. THÔNG BÁO, ĐÁNH GIÁ APP (ngoài SRS, đã chọn giữ - D-6)
-- ---------------------------------------------------------------------
CREATE TABLE notification_preference (                            -- [UI: Cài đặt > Thông báo; CI-2, SI-4]
    user_id               uuid PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    master_enabled        boolean NOT NULL DEFAULT true,
    daily_reminder        boolean NOT NULL DEFAULT true,
    remind_time           time    NOT NULL DEFAULT '20:00',       -- giờ local (users.timezone)
    streak_at_risk        boolean NOT NULL DEFAULT true,          -- CI-2
    new_content           boolean NOT NULL DEFAULT true,
    friend_request        boolean NOT NULL DEFAULT true,          -- CI-2
    friend_overtake       boolean NOT NULL DEFAULT true,
    writing_feedback      boolean NOT NULL DEFAULT true,          -- báo khi chấm xong (chấm bất đồng bộ)
    updated_at            timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notif_pref_remind ON notification_preference(remind_time)
    WHERE master_enabled AND daily_reminder;                      -- job nhắc học quét theo giờ

CREATE TABLE notification (                                       -- [UI: chuông ở Trang chủ] hộp thư trong app
    notification_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    type       varchar(24) NOT NULL,                              -- badge_earned, friend_request, writing_graded, streak_at_risk...
    payload    jsonb NOT NULL DEFAULT '{}'::jsonb,
    read_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_user ON notification(user_id, created_at DESC);
-- Giữ 90 ngày (đề xuất, giống DI-2)

CREATE TABLE app_rating (                                         -- [UI: "Bạn thích PELA chứ?"]
    rating_id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid REFERENCES users(user_id) ON DELETE SET NULL,
    stars      int NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment    varchar(500),                                      -- 1-3 sao: mở ô góp ý; 4-5 sao: mở store
    app_version varchar(20),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------
-- 8. OFFLINE SYNC (UC-14)
-- ---------------------------------------------------------------------
CREATE TABLE sync_log (                                           -- [SRS] SYNC_LOG (giữ 90 ngày - DI-2)
    sync_id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    device_id         varchar(100) NOT NULL,
    sync_started_at   timestamptz NOT NULL,
    sync_completed_at timestamptz,
    records_synced    int NOT NULL DEFAULT 0 CHECK (records_synced >= 0),
    status            varchar(10) NOT NULL CHECK (status IN ('success', 'partial', 'failed'))
);
CREATE INDEX idx_sync_log_time ON sync_log(sync_started_at);

CREATE TABLE sync_conflict (                                      -- [ADD: Offline.Sync.Conflict, DI-4 giữ bản thua 30 ngày]
    conflict_id   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    sync_id       uuid REFERENCES sync_log(sync_id) ON DELETE SET NULL,
    entity_type   varchar(50) NOT NULL,
    entity_id     uuid NOT NULL,
    losing_payload jsonb NOT NULL,
    resolved_at   timestamptz NOT NULL DEFAULT now(),
    expires_at    timestamptz NOT NULL DEFAULT now() + interval '30 days'
);
CREATE INDEX idx_sync_conflict_expiry ON sync_conflict(expires_at);

-- ---------------------------------------------------------------------
-- 9. ADMIN: LOG, CẤU HÌNH, PROMPT, SUPPORT
-- ---------------------------------------------------------------------
CREATE TABLE admin_action_log (                                   -- [SRS] ADMIN_ACTION_LOG (SRS đặt tên cột "timestamp" -> đổi acted_at)
    log_id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id      uuid NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    action_type   varchar(50) NOT NULL,                           -- vd 'question.create', 'question.retire', 'user.suspend', 'report.ignore'
    target_entity varchar(50) NOT NULL,
    target_id     varchar(100),                                   -- [D-22] id bản ghi bị tác động (uuid hoặc setting_key)
    detail        jsonb,                                          -- [D-22] {"before":..,"after":..} hoặc lý do
    acted_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_admin_log_target ON admin_action_log(target_entity, target_id);
-- [D-22, ADMIN H] Bắt buộc ghi log (cùng transaction) cho: lưu/sửa/ngừng câu hỏi & bài nghe, nhập & publish lộ trình,
-- khoá/mở khoá/xoá tài khoản, xử lý báo cáo. (system_setting, prompt_template sửa thẳng trong DB - MNT-3/4.)
-- Màn "Nhật ký hoạt động" (H) và dialog hồ sơ Admin (D) đọc bảng này. Bảng chỉ INSERT, không UPDATE/DELETE:
REVOKE UPDATE, DELETE ON admin_action_log FROM authenticated, anon;
CREATE INDEX idx_admin_log_admin_time ON admin_action_log(admin_id, acted_at DESC);

CREATE TABLE system_setting (                                     -- [ADD: MNT-3, MNT-4, AS-5/7/8/9 giá trị cấu hình được, không hard-code]
    setting_key varchar(100) PRIMARY KEY,
    value       jsonb NOT NULL,
    description text,
    updated_by  uuid REFERENCES users(user_id),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE prompt_template (                                    -- [ADD: MNT-4 prompt AI có version, nằm ngoài ứng dụng đã biên dịch]
    template_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(60) NOT NULL,         -- writing_grading | rag_question_generation | placement_scoring
    version     int NOT NULL,
    body        text NOT NULL,
    is_active   boolean NOT NULL DEFAULT false,
    created_by  uuid REFERENCES users(user_id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (name, version)
);
CREATE UNIQUE INDEX uq_prompt_active ON prompt_template(name) WHERE is_active;

-- [A-13] Báo cáo sự cố app (UC-31) - thay support_ticket của v1.2 / Admin.Support.Reports.
-- Learner gửi từ Cài đặt > Báo cáo sự cố, hoặc lắc máy (công tắc "Lắc máy để báo cáo" lưu TRÊN MÁY như dark mode - D-18).
-- Admin xử lý ở màn Report, tab App (UC-29 A1).
CREATE TABLE app_report (
    report_id      uuid PRIMARY KEY,                              -- client sinh: gửi lại khi offline không bị trùng (UC-14)
    user_id        uuid REFERENCES users(user_id) ON DELETE SET NULL,
    category       varchar(16) NOT NULL CHECK (category IN ('bug', 'wrong_content', 'suggestion', 'other')),
    message        varchar(1000) NOT NULL CHECK (length(trim(message)) > 0),
    source         varchar(8) NOT NULL CHECK (source IN ('settings', 'shake')),
    context        jsonb NOT NULL DEFAULT '{}'::jsonb,            -- {"screen","app_version","device","os"} tự đính kèm
    question_id    uuid REFERENCES question_bank(question_id) ON DELETE SET NULL,  -- đang làm câu nào (nếu có)
    clip_id        uuid REFERENCES listening_clip(clip_id) ON DELETE SET NULL,
    screenshot_path varchar(255),                                 -- tuỳ chọn, Storage riêng tư; xoá khi đóng báo cáo (DI-7)
    status         varchar(10) NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'resolved', 'dismissed')),
    resolution_note varchar(500),
    resolved_by    uuid REFERENCES users(user_id),
    resolved_at    timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CHECK ((status = 'open') = (resolved_at IS NULL))
);
CREATE INDEX idx_app_report_status ON app_report(status, created_at DESC);

-- ---------------------------------------------------------------------
-- 10. SEED TỐI THIỂU
-- ---------------------------------------------------------------------
INSERT INTO system_setting (setting_key, value, description) VALUES
 ('star.bands',                      '[{"min_pct":100,"stars":5},{"min_pct":80,"stars":4},{"min_pct":60,"stars":3},{"min_pct":40,"stars":2},{"min_pct":20,"stars":1}]', 'BR-16/17/18 (AS-4)'),
 ('weekly.bonus_star_on_perfect',    'true',  'BR-17 (AS-6)'),
 ('final_test.unlock_stars',         '30',    'BR-20 (AS-5)'),
 ('streak.restores_per_month',       '3',     'D-2: thay Gamify.Streak.Freeze'),
 ('writing.daily_limit',             '5',     'D-9: demo 5 (SRS ghi 20, UI minh hoạ 3); chỉ trừ lượt khi chấm thành công'),
 ('writing.vocab_suggestions_max',   '3',     'D-3: số "từ đáng học" tối đa/bài'),
 ('writing.repetition_min_count',    '3',     'D-8: lặp >= 3 lần cùng một từ nội dung thì báo lặp từ'),
 ('profile.name_cooldown_days',      '10',    'UI: đổi tên hiển thị'),
 ('profile.username_cooldown_days',  '30',    'UI: đổi ID người dùng'),
 ('level_up.question_count',         '20',    'D-5 (UI minh hoạ 15)'),
 ('level_up.duration_minutes',       '20',    'D-5'),
 ('level_up.pass_percent',           '80',    'D-5, nhất quán BR-19/BR-20'),
 ('level_up.cooldown_days_after_fail','7',    'D-5 (UI minh hoạ 30 ngày)'),
 ('account.deletion_grace_days',     '30',    'D-12, DI-3'),
 ('rag.retrieval.top_k',             '5',     'BR-8: số đề Writing tương tự đưa vào prompt'),
 ('rag.retrieval.min_similarity',    '0.65',  'BR-8: hiệu chỉnh 12/10/2026 với gemini-embedding-001 768 chiều (top-1 đúng chủ đề 0.688-0.754, lạc đề 0.589); bắt buộc lọc topic + cefr_level'),
 ('rag.cache.min_similarity',        '0.92',  'BR-24 (AS-7): chưa đo, hiệu chỉnh trước khi test người dùng'),
 ('rag.cache.dedupe_days',           '14',    'BR-24 (AS-8)'),
 ('rag.cache.ttl_days',              '30',    'BR-25 (AS-9)'),
 ('srs.weak_graduation_ef',          '2.18',  'BR-20/BR-6: SRS chỉ nói "dưới 2.5" - chốt 2.5 - 0.32 (một lần sai theo AS-10)'),
 ('ai.grading_timeout_ms',           '8000',  'SI-1.2'),
 ('ai.question_gen_p95_budget_ms',   '4000',  'SI-1.2 / PER-3: quá hạn -> phát đề có sẵn cùng topic/level'),
 ('import.max_csv_mb',               '2',     'ADMIN E: CSV lộ trình'),
 ('import.max_audio_mb',             '20',    'ADMIN F: audio Listening (~10 phút mp3)'),
 ('import.max_file_mb',              '5',     'ADMIN F: file đề Writing (docx/pdf/txt)'),
 ('import.duplicate_similarity',     '0.95',  'ADMIN F: đề Writing mới giống đề cũ >= ngưỡng thì cảnh báo trùng'),
 ('account.max_suspend_days',        '365',   'ADMIN C/G: Ban tạm thời tối đa'),
 ('writing.ai_prompt_daily_limit',   '10',    'P-12: số lần "Để AI tạo đề" mỗi learner mỗi ngày'),
 ('report.screenshot_max_kb',        '500',   'A-13: ảnh chụp đính kèm báo cáo sự cố (nén trên máy)');

INSERT INTO badge (name, description, icon_url, criteria, category, tier, metric, threshold) VALUES
 -- Ngưỡng lấy theo UI (minh hoạ) - Admin chỉnh được
 ('Bước đầu 1',   'Học bài đầu tiên',     '', 'lessons_completed >= 1',    'first_steps', 1, 'lessons_completed',   1),
 ('Bước đầu 2',   'Xong 5 bài học',       '', 'lessons_completed >= 5',    'first_steps', 2, 'lessons_completed',   5),
 ('Bước đầu 3',   'Xong 20 bài học',      '', 'lessons_completed >= 20',   'first_steps', 3, 'lessons_completed',  20),
 ('Bước đầu 4',   'Xong 50 bài học',      '', 'lessons_completed >= 50',   'first_steps', 4, 'lessons_completed',  50),
 ('Streak 1',     'Streak 3 ngày',        '', 'current_streak >= 3',       'streak',      1, 'current_streak',      3),
 ('Streak 2',     'Streak 7 ngày',        '', 'current_streak >= 7',       'streak',      2, 'current_streak',      7),
 ('Streak 3',     'Streak 30 ngày',       '', 'current_streak >= 30',      'streak',      3, 'current_streak',     30),
 ('Streak 4',     'Streak 100 ngày',      '', 'current_streak >= 100',     'streak',      4, 'current_streak',    100),
 ('Từ vựng 1',    'Thuộc 50 từ',          '', 'words_mastered >= 50',      'vocabulary',  1, 'words_mastered',     50),
 ('Từ vựng 2',    'Thuộc 200 từ',         '', 'words_mastered >= 200',     'vocabulary',  2, 'words_mastered',    200),
 ('Từ vựng 3',    'Thuộc 500 từ',         '', 'words_mastered >= 500',     'vocabulary',  3, 'words_mastered',    500),
 ('Từ vựng 4',    'Thuộc 1.000 từ',       '', 'words_mastered >= 1000',    'vocabulary',  4, 'words_mastered',   1000),
 ('Tốt nghiệp 1', 'Qua 1 Final Test',     '', 'final_tests_passed >= 1',   'topic',       1, 'final_tests_passed',  1),
 ('Tốt nghiệp 2', 'Qua 3 Final Test',     '', 'final_tests_passed >= 3',   'topic',       2, 'final_tests_passed',  3),
 ('Tốt nghiệp 3', 'Qua 6 Final Test',     '', 'final_tests_passed >= 6',   'topic',       3, 'final_tests_passed',  6),
 ('Tốt nghiệp 4', 'Qua 10 Final Test',    '', 'final_tests_passed >= 10',  'topic',       4, 'final_tests_passed', 10),
 ('Cây viết 1',   'Nộp 1 bài viết',       '', 'writings_submitted >= 1',   'writing',     1, 'writings_submitted',  1),
 ('Cây viết 2',   'Nộp 10 bài viết',      '', 'writings_submitted >= 10',  'writing',     2, 'writings_submitted', 10),
 ('Cây viết 3',   'Nộp 30 bài viết',      '', 'writings_submitted >= 30',  'writing',     3, 'writings_submitted', 30),
 ('Cây viết 4',   'Nộp 60 bài viết',      '', 'writings_submitted >= 60',  'writing',     4, 'writings_submitted', 60),
 ('Máy nghe 1',   'Nghe 10 bài',          '', 'listening_completed >= 10', 'listening',   1, 'listening_completed', 10),
 ('Máy nghe 2',   'Nghe 50 bài',          '', 'listening_completed >= 50', 'listening',   2, 'listening_completed', 50),
 ('Máy nghe 3',   'Nghe 150 bài',         '', 'listening_completed >= 150','listening',   3, 'listening_completed',150),
 ('Máy nghe 4',   'Nghe 300 bài',         '', 'listening_completed >= 300','listening',   4, 'listening_completed',300),
 ('Kết bạn 1',    'Có 1 người bạn',       '', 'friends_count >= 1',        'social',      1, 'friends_count',       1),
 ('Kết bạn 2',    'Có 5 người bạn',       '', 'friends_count >= 5',        'social',      2, 'friends_count',       5),
 ('Kết bạn 3',    'Có 15 người bạn',      '', 'friends_count >= 15',       'social',      3, 'friends_count',      15),
 ('Kết bạn 4',    'Có 30 người bạn',      '', 'friends_count >= 30',       'social',      4, 'friends_count',      30);
-- 7 huy hiệu của SRS (Gamify.Badges.Criteria) đều nằm trong bảng trên, trừ "Perfect Writer" (bài viết 5 sao đầu tiên)
-- và "First Steps" của SRS (= Daily Challenge đầu tiên) - xem D-7.

-- ---------------------------------------------------------------------
-- 11. BẢO MẬT Ở TẦNG DB (defense-in-depth; backend Java dùng service role
--     nên SEC-3/SEC-4 vẫn PHẢI được kiểm tra trong ứng dụng)
-- ---------------------------------------------------------------------
-- Learner chỉ sửa được các cột hồ sơ; role/status/email chỉ đổi qua backend (service role)
REVOKE UPDATE ON users FROM authenticated, anon;
GRANT  UPDATE (avatar_url, native_language, timezone) ON users TO authenticated;
-- full_name / username có thời gian chờ đổi -> chỉ đổi qua backend để kiểm tra cooldown

DO $$
DECLARE t text;
BEGIN
  -- Bảng sở hữu theo learner: chỉ thấy/sửa dòng của chính mình
  FOREACH t IN ARRAY ARRAY['level_progress','survey_response','placement_test_attempt','lesson_progress',
        'topic_progress','final_test_attempt','review_card','challenge_session','writing_submission',
        'listening_session','answer_log','streak','user_badge','star_event','user_daily_activity',
        'sync_log','sync_conflict','user_device','app_report','learner_profile','level_up_attempt',
        'writing_vocab_suggestion','streak_day','user_stats','notification_preference','notification']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY own_rows ON %I FOR ALL TO authenticated
                    USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid())', t);
  END LOOP;
  -- Bảng chỉ backend/admin truy cập: bật RLS và không tạo policy cho learner
  FOREACH t IN ARRAY ARRAY['question_bank','topic_final_test_pool','rag_question_cache','system_setting',
        'prompt_template','admin_action_log','survey_level_rule','challenge_item','listening_attempt',
        'writing_issue','user_block','user_report','app_rating','listening_clip','import_job','rag_cache_serve']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
  END LOOP;
END $$;

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY users_self      ON users FOR SELECT TO authenticated USING (user_id = auth.uid() OR is_admin());
CREATE POLICY users_self_edit ON users FOR UPDATE TO authenticated USING (user_id = auth.uid());

-- Nội dung học: learner chỉ đọc nội dung đã publish (E1 UC-18)
ALTER TABLE topic ENABLE ROW LEVEL SECURITY;
ALTER TABLE lesson ENABLE ROW LEVEL SECURITY;
ALTER TABLE vocabulary ENABLE ROW LEVEL SECURITY;
ALTER TABLE lesson_vocab ENABLE ROW LEVEL SECURITY;
CREATE POLICY topic_read  ON topic  FOR SELECT TO authenticated USING (status = 'published' OR is_admin());
CREATE POLICY lesson_read ON lesson FOR SELECT TO authenticated USING (status = 'published' OR is_admin());
CREATE POLICY vocab_read  ON vocabulary FOR SELECT TO authenticated
    USING (owner_user_id IS NULL OR owner_user_id = auth.uid() OR is_admin());   -- từ riêng chỉ chủ sở hữu thấy
CREATE POLICY lv_read     ON lesson_vocab FOR SELECT TO authenticated USING (true);

-- 3 bảng bị sót RLS ở v1.2 (Supabase SQL Editor cảnh báo khi chạy thử 12/10/2026)
ALTER TABLE friendship ENABLE ROW LEVEL SECURITY;
CREATE POLICY friendship_own ON friendship FOR SELECT TO authenticated
    USING (user_id = auth.uid() OR friend_id = auth.uid());       -- chỉ thấy lời mời/quan hệ của chính mình
ALTER TABLE badge ENABLE ROW LEVEL SECURITY;
CREATE POLICY badge_read ON badge FOR SELECT TO authenticated USING (is_active);
ALTER TABLE survey_question ENABLE ROW LEVEL SECURITY;
CREATE POLICY survey_question_read ON survey_question FOR SELECT TO authenticated USING (is_active);

-- Leaderboard công khai (không chứa PII) để dùng Supabase Realtime
ALTER TABLE leaderboard_entry ENABLE ROW LEVEL SECURITY;
CREATE POLICY lb_read ON leaderboard_entry FOR SELECT TO authenticated USING (true);

-- Hồ sơ công khai: chỉ lộ tên, avatar, level, streak, huy hiệu đang hiển thị (SAF-2, Profile.View.Public)
CREATE VIEW v_public_profile AS
SELECT u.user_id, u.full_name, u.username, u.avatar_url, u.cefr_level,
       s.current_streak,
       COALESCE((SELECT jsonb_agg(b.name ORDER BY ub.earned_at)
                 FROM user_badge ub JOIN badge b USING (badge_id)
                 WHERE ub.user_id = u.user_id AND ub.is_displayed), '[]'::jsonb) AS displayed_badges
FROM users u LEFT JOIN streak s USING (user_id)
WHERE u.status = 'active' AND u.role = 'learner';
