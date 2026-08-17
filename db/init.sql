-- 「杏坛智备」数据库初始化脚本（PostgreSQL 16 + pgvector）
-- 执行方式：docker compose 启动时自动执行，或 psql -f init.sql

CREATE EXTENSION IF NOT EXISTS vector;

-- 学校
CREATE TABLE IF NOT EXISTS school (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    region      VARCHAR(100),
    level       VARCHAR(20),           -- 小学/初中/九年一贯制
    type        VARCHAR(20),           -- 公办/民办
    status      SMALLINT DEFAULT 1,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- 用户
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGSERIAL PRIMARY KEY,
    phone         VARCHAR(20) UNIQUE,
    password      VARCHAR(100),
    nickname      VARCHAR(50),
    role          VARCHAR(20) DEFAULT 'TEACHER',  -- TEACHER/ADMIN/OPERATOR
    school_id     BIGINT REFERENCES school(id),
    status        SMALLINT DEFAULT 1,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ DEFAULT now()
);

-- 教师档案
CREATE TABLE IF NOT EXISTS teacher_profile (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES sys_user(id),
    subjects       VARCHAR(255),
    grades         VARCHAR(255),
    teaching_years SMALLINT,
    weak_network   BOOLEAN DEFAULT FALSE,
    created_at     TIMESTAMPTZ DEFAULT now()
);

-- 班级
CREATE TABLE IF NOT EXISTS class_group (
    id         BIGSERIAL PRIMARY KEY,
    school_id  BIGINT NOT NULL REFERENCES school(id),
    grade      VARCHAR(20),
    class_name VARCHAR(50),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 学生与成绩（学情分析数据，不含敏感个人信息之外的字段）
CREATE TABLE IF NOT EXISTS student (
    id         BIGSERIAL PRIMARY KEY,
    class_id   BIGINT NOT NULL REFERENCES class_group(id) ON DELETE CASCADE,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS student_score (
    id         BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES student(id) ON DELETE CASCADE,
    subject    VARCHAR(30),
    score      NUMERIC(5,1),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 知识库文档（课标/教材/模板/乡土案例）
CREATE TABLE IF NOT EXISTS kb_document (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(200) NOT NULL,
    doc_type         VARCHAR(30) NOT NULL,  -- STANDARD/TEXTBOOK/TEMPLATE/CASE
    user_id          BIGINT,
    subject          VARCHAR(30),
    grade            VARCHAR(20),
    textbook_version VARCHAR(50),
    status           SMALLINT DEFAULT 1,
    meta             JSONB,
    created_at       TIMESTAMPTZ DEFAULT now()
);

-- 知识库分块（含向量）
CREATE TABLE IF NOT EXISTS kb_chunk (
    id        BIGSERIAL PRIMARY KEY,
    doc_id    BIGINT NOT NULL REFERENCES kb_document(id) ON DELETE CASCADE,
    seq       INT,
    content   TEXT NOT NULL,
    embedding vector(1024),
    metadata  JSONB,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_doc ON kb_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_kb_chunk_embedding ON kb_chunk USING hnsw (embedding vector_cosine_ops);

-- 提示词模板
CREATE TABLE IF NOT EXISTS prompt_template (
    id          BIGSERIAL PRIMARY KEY,
    scene       VARCHAR(50) NOT NULL,  -- LESSON_PLAN/EXERCISE/PAPER/CHAT
    subject     VARCHAR(30),
    lesson_type VARCHAR(30),
    version     INT DEFAULT 1,
    content     TEXT NOT NULL,
    config      JSONB,
    status      SMALLINT DEFAULT 1,
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- 生成任务
CREATE TABLE IF NOT EXISTS generation_task (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES sys_user(id),
    scene         VARCHAR(30) DEFAULT 'LESSON_PLAN',
    params        JSONB,
    provider      VARCHAR(30),
    status        VARCHAR(20) DEFAULT 'PENDING',  -- PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT
    error_message TEXT,
    cost_cents    INT DEFAULT 0,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);

-- 教案
CREATE TABLE IF NOT EXISTS lesson_plan (
    id            BIGSERIAL PRIMARY KEY,
    task_id       BIGINT REFERENCES generation_task(id),
    user_id       BIGINT NOT NULL REFERENCES sys_user(id),
    title         VARCHAR(200),
    subject       VARCHAR(30),
    grade         VARCHAR(20),
    textbook      VARCHAR(100),
    lesson_type   VARCHAR(30),
    content       JSONB,
    quality_score SMALLINT,
    is_public     SMALLINT DEFAULT 0,
    status        SMALLINT DEFAULT 1,
    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
);

-- 教案区块（教学目标/重难点/教学过程/板书/作业）
CREATE TABLE IF NOT EXISTS lesson_section (
    id            BIGSERIAL PRIMARY KEY,
    lesson_plan_id BIGINT NOT NULL REFERENCES lesson_plan(id) ON DELETE CASCADE,
    section_type  VARCHAR(30),  -- OBJECTIVE/KEY_POINT/PROCESS/BOARD/HOMEWORK
    seq           INT,
    content       JSONB,
    standard_ref  VARCHAR(200)
);

-- 习题
CREATE TABLE IF NOT EXISTS exercise (
    id              BIGSERIAL PRIMARY KEY,
    lesson_plan_id  BIGINT REFERENCES lesson_plan(id) ON DELETE CASCADE,
    task_id         BIGINT REFERENCES generation_task(id),
    subject         VARCHAR(30),
    grade           VARCHAR(20),
    knowledge_point VARCHAR(100),
    difficulty      VARCHAR(10),
    type            VARCHAR(20),
    content         TEXT,
    answer          TEXT,
    analysis        TEXT
);

-- 使用埋点
CREATE TABLE IF NOT EXISTS usage_log (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT,
    school_id    BIGINT,
    action       VARCHAR(50),  -- GENERATE/EDIT/EXPORT/COLLECT/FEEDBACK/LOGIN
    scene        VARCHAR(30),
    duration_sec INT,
    cost_cents   INT,
    created_at   TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_usage_log_user_time ON usage_log(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_usage_log_action_time ON usage_log(action, created_at);

-- 反馈
CREATE TABLE IF NOT EXISTS feedback (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT,
    lesson_plan_id BIGINT,
    type           VARCHAR(20),  -- USEFUL/NEED_FIX/REPORT
    content        TEXT,
    created_at     TIMESTAMPTZ DEFAULT now()
);

-- 调研记录（基线/跟踪）
CREATE TABLE IF NOT EXISTS survey (
    id         BIGSERIAL PRIMARY KEY,
    school_id  BIGINT,
    round      VARCHAR(20),  -- BASELINE/FOLLOW_UP
    teacher_id BIGINT,
    metric     VARCHAR(50),
    value      NUMERIC(10,2),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 模型配置
CREATE TABLE IF NOT EXISTS model_config (
    id             BIGSERIAL PRIMARY KEY,
    provider       VARCHAR(30),
    model          VARCHAR(50),
    api_key_cipher VARCHAR(255),
    enabled        BOOLEAN DEFAULT TRUE,
    priority       INT,
    note           VARCHAR(200)
);

-- 内容审核日志
CREATE TABLE IF NOT EXISTS content_review_log (
    id           BIGSERIAL PRIMARY KEY,
    task_id      BIGINT,
    content_type VARCHAR(30),
    result       VARCHAR(20),
    detail       TEXT,
    created_at   TIMESTAMPTZ DEFAULT now()
);

-- 登录令牌
CREATE TABLE IF NOT EXISTS auth_token (
    token      VARCHAR(64) PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES sys_user(id),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 种子数据：演示账号（生产环境需删除并走真实注册）
INSERT INTO school (id, name, region, level, type)
VALUES (1, '演示学校-新乡某乡村小学', '新乡市', '小学', '公办')
ON CONFLICT DO NOTHING;

INSERT INTO sys_user (id, phone, password, nickname, role, school_id, status)
VALUES (1, '13800000000', '123456', '演示教师', 'TEACHER', 1, 1)
ON CONFLICT DO NOTHING;

-- 知识库种子：课标（语数英，其余 6 科由应用启动器补齐）/ 教材示例 / 模板 / 乡土案例
-- 与演示库（h2-schema.sql）保持一致，保证生产与演示口径统一（75 册教材目录 + 9 科课标）
INSERT INTO kb_document (id, title, doc_type, subject, grade, textbook_version, status)
VALUES (1, '2022版义务教育语文课程标准·核心素养与学习任务群', 'STANDARD', '语文', NULL, NULL, 1)
ON CONFLICT DO NOTHING;
INSERT INTO kb_document (id, title, doc_type, subject, grade, textbook_version, status)
VALUES (2, '2022版义务教育数学课程标准·三会与内容领域', 'STANDARD', '数学', NULL, NULL, 1)
ON CONFLICT DO NOTHING;
INSERT INTO kb_document (id, title, doc_type, subject, grade, textbook_version, status)
VALUES (3, '2022版义务教育英语课程标准·核心素养与内容六要素', 'STANDARD', '英语', NULL, NULL, 1)
ON CONFLICT DO NOTHING;
INSERT INTO kb_document (id, title, doc_type, subject, grade, textbook_version, status)
VALUES (4, '新授课教案通用模板', 'TEMPLATE', NULL, NULL, NULL, 1)
ON CONFLICT DO NOTHING;
INSERT INTO kb_document (id, title, doc_type, subject, grade, textbook_version, status)
VALUES (5, '人教版语文八年级上册·教材目录', 'TEXTBOOK', '语文', '八年级', '人教版', 1)
ON CONFLICT DO NOTHING;
INSERT INTO kb_document (id, title, doc_type, subject, grade, textbook_version, status)
VALUES (6, '河南新乡乡土案例库', 'CASE', NULL, NULL, NULL, 1)
ON CONFLICT DO NOTHING;

INSERT INTO kb_chunk (doc_id, seq, content) VALUES (1, 1, '语文核心素养：文化自信、语言运用、思维能力、审美创造。学习任务群：语言文字积累与梳理、实用性阅读与交流、文学阅读与创意表达、思辨性阅读与表达、整本书阅读、跨学科学习。')
ON CONFLICT DO NOTHING;
INSERT INTO kb_chunk (doc_id, seq, content) VALUES (2, 1, '数学核心素养三会：会用数学的眼光观察现实世界，会用数学的思维思考现实世界，会用数学的语言表达现实世界。内容领域：数与代数、图形与几何、统计与概率、综合与实践。')
ON CONFLICT DO NOTHING;
INSERT INTO kb_chunk (doc_id, seq, content) VALUES (3, 1, '英语核心素养：语言能力、文化意识、思维品质、学习能力。课程内容六要素：主题、语篇、语言知识、文化知识、语言技能、学习策略。')
ON CONFLICT DO NOTHING;
INSERT INTO kb_chunk (doc_id, seq, content) VALUES (4, 1, '教案结构：教学目标（素养导向）→ 教学重难点 → 教学准备 → 教学过程（导入/新授/互动/练习/总结，每环节标注时间与师生活动）→ 板书设计 → 分层作业（基础/提高/拓展）→ 课后反思。')
ON CONFLICT DO NOTHING;
INSERT INTO kb_chunk (doc_id, seq, content) VALUES (5, 1, '人教版语文八年级上册目录：第一单元 活动探究·新闻；第二单元 回忆性散文（藤野先生、回忆我的母亲、列夫·托尔斯泰、美丽的颜色）；第三单元 古诗文（三峡、短文二篇、与朱元思书、唐诗五首）；第四单元 散文（背影、白杨礼赞、散文二篇、昆明的雨）；第五单元 说明文（中国石拱桥、苏州园林、蝉、梦回繁华）；第六单元 古诗文（孟子二章、愚公移山、周亚夫军细柳、诗词五首）。')
ON CONFLICT DO NOTHING;
INSERT INTO kb_chunk (doc_id, seq, content) VALUES (6, 1, '新乡乡土素材：中原农谷国家级农业科技平台；牧野大地麦收文化（三夏抢收）；封丘陈桥驿（宋太祖黄袍加身处）；辉县百泉；新乡南太行（万仙山、八里沟）；毗邻红旗渠精神（林州）；豫剧、河南坠子等非遗；平原新区黄河滩区生态治理。可用于情境导入与跨学科教学。')
ON CONFLICT DO NOTHING;

-- 种子数据占用了显式 ID，重置序列避免后续插入冲突
SELECT setval('school_id_seq', 100, true);
SELECT setval('sys_user_id_seq', 100, true);
SELECT setval('kb_document_id_seq', 100, true);
SELECT setval('kb_chunk_id_seq', 100, true);
