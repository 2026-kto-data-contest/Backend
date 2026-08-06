-- 파이프라인 RAW 적재층 DDL (PostgreSQL 전용). 이 파일은 raw 두 테이블만 정의한다.
-- 원본 필드는 전부 무손실 문자열(TEXT) 저장. 유일한 예외: brewery 조회수(원본 JSON number → BIGINT).
-- 원본 필드에는 NOT NULL 등 의미 제약을 걸지 않는다(원본 null은 null 그대로 보존).

CREATE TABLE IF NOT EXISTS brewery_raw (
    id                    BIGSERIAL PRIMARY KEY,          -- 서러게이트 PK(의미 없음)
    snapshot_date         DATE      NOT NULL,             -- 논리 스냅샷 라벨(주입 우선, 미주입 시 collected_at의 UTC 날짜) — 스냅샷 축
    source_row_index      INT       NOT NULL,             -- 원본 data 배열을 페이지 순서대로 이어붙인 전역 0-based 위치
    source_reference_date DATE,                           -- 원본 기준일(현재는 자리만, 채움 로직 없음)
    collected_at          TIMESTAMP NOT NULL,             -- 원본 수집 시각(UTC, manifest.collected_at 유래) — created_at과 다른 개념
    created_at            TIMESTAMP NOT NULL,             -- DB 적재 시각(UTC, audit)
    -- 원본 7필드 (한글 키 → 영문 컬럼, docs/audit/20260729_raw_field_mapping.md 참조)
    use_yn                TEXT,                           -- 사용여부
    anytime_visit_yn      TEXT,                           -- 상시방문가능여부
    business_name         TEXT,                           -- 상호명
    reservation_visit_yn  TEXT,                           -- 예약방문가능여부
    view_count            BIGINT,                         -- 조회수 (원본 number 타입 유지)
    address               TEXT,                           -- 주소
    homepage_url          TEXT,                           -- 홈페이지
    CONSTRAINT uq_brewery_raw_snapshot_row UNIQUE (snapshot_date, source_row_index)
);

CREATE TABLE IF NOT EXISTS product_raw (
    id                    BIGSERIAL PRIMARY KEY,          -- 서러게이트 PK(의미 없음)
    snapshot_date         DATE      NOT NULL,             -- 논리 스냅샷 라벨(주입 우선, 미주입 시 collected_at의 UTC 날짜) — 스냅샷 축
    source_row_index      INT       NOT NULL,             -- 원본 data 배열을 페이지 순서대로 이어붙인 전역 0-based 위치
    source_reference_date DATE,                           -- 원본 기준일(현재는 자리만, 채움 로직 없음)
    collected_at          TIMESTAMP NOT NULL,             -- 원본 수집 시각(UTC, manifest.collected_at 유래) — created_at과 다른 개념
    created_at            TIMESTAMP NOT NULL,             -- DB 적재 시각(UTC, audit)
    -- 원본 12필드 (한글 키 → 영문 컬럼, docs/audit/20260729_raw_field_mapping.md 참조)
    ingredients           TEXT,                           -- 성분
    awards                TEXT,                           -- 수상경력
    alcohol_content       TEXT,                           -- 알콜도수 (숫자성 문자열 — 변환 없이 원문 보존)
    brewery_name          TEXT,                           -- 양조장 ★파생층 조인 입구 필드 (null 61건 실측)
    brewery_address       TEXT,                           -- 양조장주소
    volume                TEXT,                           -- 용량 (예: "750ml / 1700ml" — 분해 없이 원문 보존)
    product_name          TEXT,                           -- 제품명
    description           TEXT,                           -- 제품소개
    special_note          TEXT,                           -- 특이사항
    characteristics       TEXT,                           -- 특징
    sale_yn               TEXT,                           -- 판매여부
    homepage_url          TEXT,                           -- 홈페이지주소
    CONSTRAINT uq_product_raw_snapshot_row UNIQUE (snapshot_date, source_row_index)
);

-- ────────────────────────────────────────────────────────────────────────────
-- 파생층 (3c-1). 채번원장(brewery_id_ledger.json) + 골든 raw 속성 → brewery 마스터,
-- assignment 확정 9행 → manual_override 시드. schema.sql이 스키마 진실원천(JPA는 validate만).
-- ★컬럼 생성 ≠ 값 확정: sido/region(주소파싱)·join_status(조인)·liquor_status(주종롤업)은
--   이번에 계산하지 않는다. 컬럼만 만들고 초기값(UNJOINED/NA)·nullable로 두며 후속 단계가 UPDATE한다.

CREATE TABLE IF NOT EXISTS brewery (
    brewery_id              TEXT      PRIMARY KEY,          -- BRW-xxx 자연키 PK(원장). 서러게이트 없음·불변·append-only
    business_name           TEXT      NOT NULL,             -- 상호명(골든 원문 NFC). ★UNIQUE 금지(개명 대비) — 인덱스만
    norm                    TEXT      NOT NULL,             -- 원장 norm(정규화명). 조인 매칭키 재현용
    address                 TEXT      NOT NULL,             -- 골든 주소 원문(무손실, 골든 null 0건)
    homepage_url            TEXT,                           -- 골든 홈페이지(null 1건)
    view_count              BIGINT    NOT NULL,             -- 골든 조회수(원본 number)
    reservation_visit_state TEXT      NOT NULL,             -- 3-state Y/N/UNKNOWN (예약방문, raw null→UNKNOWN)
    always_visit_state      TEXT      NOT NULL,             -- 3-state Y/N/UNKNOWN (상시방문, raw null→UNKNOWN)
    -- 계산 파생 자리 (★이번 미계산, 후속 UPDATE)
    sido                    TEXT,                           -- 주소 파싱 결과 자리(다음 단계)
    region                  TEXT,                           -- 광역권 매핑 자리(다음 단계)
    join_status             TEXT      NOT NULL DEFAULT 'UNJOINED',  -- 3c-2 조인이 UPDATE
    liquor_status           TEXT      NOT NULL DEFAULT 'NA',        -- 3d 주종롤업이 UPDATE
    image_url               TEXT,                           -- 소스 미확정(C-10) 격리 자리
    -- 좌표 파생 자리 (#28 8단계 카카오 지오코딩이 UPDATE). ★address는 항상 raw 원문 — 좌표만 파생.
    latitude                NUMERIC(9,6),                   -- 위도(카카오 y). 한국 범위 33~39 검증 후 저장
    longitude               NUMERIC(9,6),                   -- 경도(카카오 x). 한국 범위 124~132 검증 후 저장
    coord_source            VARCHAR(32),                    -- 좌표 출처(폴백 단계 식별). 3값 — 실사용만
    geocoded_at             TIMESTAMPTZ,                    -- 카카오 호출로 좌표 확정한 시각(UTC)
    created_at              TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP NOT NULL,
    CONSTRAINT ck_brewery_reservation_visit CHECK (reservation_visit_state IN ('Y', 'N', 'UNKNOWN')),
    CONSTRAINT ck_brewery_always_visit      CHECK (always_visit_state IN ('Y', 'N', 'UNKNOWN')),
    CONSTRAINT ck_brewery_join_status       CHECK (join_status IN ('JOINED', 'UNJOINED')),
    CONSTRAINT ck_brewery_liquor_status     CHECK (liquor_status IN ('TAGGED', 'UNTAGGED', 'NA')),
    CONSTRAINT ck_brewery_coord_source      CHECK (coord_source IN
                ('KAKAO_ADDRESS', 'KAKAO_ADDRESS_NORMALIZED', 'KAKAO_ADDRESS_SEED'))
);
-- 기존 DB 마이그레이션(#28 8단계 좌표) — 신규 설치는 위 CREATE TABLE로 좌표 4컬럼·CHECK가 반영되지만,
-- brewery 테이블이 이미 존재하는 DB는 CREATE TABLE IF NOT EXISTS가 통째로 건너뛰어 컬럼이 생기지 않는다.
-- 아래 멱등 문으로 좌표 4컬럼과 CHECK 제약을 보강한다(전례: 아래 terms_definition content_url ALTER).
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS latitude     NUMERIC(9,6);
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS longitude    NUMERIC(9,6);
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS coord_source VARCHAR(32);
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS geocoded_at  TIMESTAMPTZ;
-- CHECK는 PostgreSQL이 ADD CONSTRAINT IF NOT EXISTS를 지원하지 않는다. DO/PL-pgSQL 블록은
-- spring.sql.init(mode:always)의 ScriptUtils가 ';'로 문장을 쪼개며 $$ 달러쿼트를 인식하지 못해 깨진다.
-- 따라서 내부 세미콜론 없는 단문 2개(DROP IF EXISTS→ADD)로 멱등화한다 — 매 기동 재적용, 재검증(59행) 무시가능.
ALTER TABLE brewery DROP CONSTRAINT IF EXISTS ck_brewery_coord_source;
ALTER TABLE brewery ADD CONSTRAINT ck_brewery_coord_source CHECK (coord_source IN ('KAKAO_ADDRESS', 'KAKAO_ADDRESS_NORMALIZED', 'KAKAO_ADDRESS_SEED'));
CREATE INDEX IF NOT EXISTS ix_brewery_business_name ON brewery (business_name);
CREATE INDEX IF NOT EXISTS ix_brewery_norm ON brewery (norm);

CREATE TABLE IF NOT EXISTS manual_override (
    id              BIGSERIAL PRIMARY KEY,                  -- 서러게이트 PK(의미 없음)
    override_type   TEXT      NOT NULL,                     -- NAME_MAP / ROW_PIN
    match_key       TEXT      NOT NULL,                     -- BREWERY_NORM=양조장명 norm / PRODUCT_NAME=제품명
    match_key_kind  TEXT      NOT NULL,                     -- BREWERY_NORM / PRODUCT_NAME
    brewery_id      TEXT      NOT NULL,                     -- 참조키(이름 아님) → brewery.brewery_id
    reason          TEXT      NOT NULL,                     -- ADDR_EXACT / ADDR_STRONG / MANUAL_DOMAIN
    recheck_flag    BOOLEAN   NOT NULL,                     -- 재점검 필요(조옥화 2행 true)
    source_raw_name TEXT      NOT NULL,                     -- 원본 product raw명(추적)
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_manual_override_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (brewery_id),
    CONSTRAINT uq_manual_override_match UNIQUE (match_key_kind, match_key),
    CONSTRAINT ck_manual_override_type   CHECK (override_type IN ('NAME_MAP', 'ROW_PIN')),
    CONSTRAINT ck_manual_override_kind   CHECK (match_key_kind IN ('BREWERY_NORM', 'PRODUCT_NAME')),
    CONSTRAINT ck_manual_override_reason CHECK (reason IN ('ADDR_EXACT', 'ADDR_STRONG', 'MANUAL_DOMAIN'))
);
CREATE INDEX IF NOT EXISTS ix_manual_override_brewery ON manual_override (brewery_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 파생층 (3c-2). product raw 1215건 중 brewery 59에 실제 연결되는 행(AUTO+override)만 적재.
-- brewery_id는 ★nullable — 미래 1215 전체 확장(UNMATCHED 포함) 대비 스키마이며, 이번 적재분은 전부 non-null.
-- ★주소 파싱·주종 롤업은 이 테이블의 범위 밖(3d).

CREATE TABLE IF NOT EXISTS product_brewery_link (
    id                BIGSERIAL PRIMARY KEY,             -- 서러게이트 PK(의미 없음)
    source_row_ref    INT       NOT NULL,                -- 원본 product raw 추적 참조(source_row_index, 전역 0-based)
    product_name      TEXT      NOT NULL,                -- 제품명(원문)
    brewery_name_raw  TEXT,                               -- 원본 '양조장' 필드(원문, null 가능 — ROW_PIN 대상)
    product_norm      TEXT,                               -- brewery_name_raw 정규화 결과(3d 재계산 방지 저장값)
    brewery_id        TEXT,                               -- 연결 확정 BRW → brewery.brewery_id. ★nullable(미래 UNMATCHED 확장 대비)
    join_source       TEXT      NOT NULL,                -- AUTO / OVERRIDE_NAME / OVERRIDE_ROW / UNMATCHED(정의만, 이번 미적재)
    created_at        TIMESTAMP NOT NULL,
    CONSTRAINT fk_product_brewery_link_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (brewery_id),
    CONSTRAINT uq_product_brewery_link_source_row UNIQUE (source_row_ref),
    CONSTRAINT ck_product_brewery_link_join_source CHECK (join_source IN ('AUTO', 'OVERRIDE_NAME', 'OVERRIDE_ROW', 'UNMATCHED'))
);
CREATE INDEX IF NOT EXISTS ix_product_brewery_link_brewery ON product_brewery_link (brewery_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 회원·인증 도메인. 카카오 회원번호가 외부 인증 식별자이며 카카오 토큰은 저장하지 않는다.

CREATE TABLE IF NOT EXISTS member_account (
    id            BIGSERIAL PRIMARY KEY,
    kakao_user_id BIGINT    NOT NULL,
    nickname      TEXT      NOT NULL,
    email         TEXT,
    role          TEXT      NOT NULL DEFAULT 'USER',
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT uq_member_account_kakao_user_id UNIQUE (kakao_user_id),
    CONSTRAINT ck_member_account_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE TABLE IF NOT EXISTS auth_session (
    id            BIGSERIAL PRIMARY KEY,
    member_id     BIGINT    NOT NULL,
    token_hash    TEXT      NOT NULL,
    expires_at    TIMESTAMP NOT NULL,
    revoked_at    TIMESTAMP,
    created_at    TIMESTAMP NOT NULL,
    last_used_at  TIMESTAMP NOT NULL,
    CONSTRAINT fk_auth_session_member FOREIGN KEY (member_id) REFERENCES member_account (id) ON DELETE CASCADE,
    CONSTRAINT uq_auth_session_token_hash UNIQUE (token_hash)
);
CREATE INDEX IF NOT EXISTS ix_auth_session_member ON auth_session (member_id);
CREATE INDEX IF NOT EXISTS ix_auth_session_expires ON auth_session (expires_at);

CREATE TABLE IF NOT EXISTS terms_definition (
    code          TEXT      NOT NULL,
    version       TEXT      NOT NULL,
    title         TEXT      NOT NULL,
    required      BOOLEAN   NOT NULL,
    display_order INT       NOT NULL,
    content_url   TEXT,
    active        BOOLEAN   NOT NULL DEFAULT TRUE,
    PRIMARY KEY (code, version)
);
ALTER TABLE terms_definition ADD COLUMN IF NOT EXISTS content_url TEXT;

CREATE TABLE IF NOT EXISTS terms_agreement (
    id            BIGSERIAL PRIMARY KEY,
    member_id     BIGINT    NOT NULL,
    term_code     TEXT      NOT NULL,
    term_version  TEXT      NOT NULL,
    agreed        BOOLEAN   NOT NULL,
    recorded_at   TIMESTAMP NOT NULL,
    CONSTRAINT fk_terms_agreement_member FOREIGN KEY (member_id) REFERENCES member_account (id) ON DELETE CASCADE,
    CONSTRAINT fk_terms_agreement_definition FOREIGN KEY (term_code, term_version)
        REFERENCES terms_definition (code, version),
    CONSTRAINT uq_terms_agreement_member_term UNIQUE (member_id, term_code, term_version)
);

INSERT INTO terms_definition (code, version, title, required, display_order)
VALUES
    ('SERVICE_USE', '1.0', '서비스 이용약관 동의', TRUE, 1),
    ('PRIVACY', '1.0', '개인정보 수집 및 이용 동의', TRUE, 2),
    ('LOCATION', '1.0', '위치기반 서비스 이용약관', FALSE, 3),
    ('MARKETING', '1.0', '마케팅 정보 수신 동의 (카카오톡, 이메일 등)', FALSE, 4)
ON CONFLICT (code, version) DO UPDATE SET
    title = EXCLUDED.title,
    required = EXCLUDED.required,
    display_order = EXCLUDED.display_order;

-- ────────────────────────────────────────────────────────────────────────────
-- 파생층 (3d, 이슈 #15 1차). 주종 추론 결과 태깅 — product_brewery_link로 연결된 제품(366)만 대상.
-- ★이 테이블은 "확정"이 아니라 "추론+검수 대상 추출"이다. 전 AUTO행 recheck_flag=true(미검증).
--   골든 정답지 없음(프로젝트 첫 사례) → 분포를 봉인하지 않는다. brewery.liquor_status는 이 단계에서 건드리지 않는다.
-- 다대다: 한 제품이 여러 주종 키워드에 걸리면 (source_row_ref, liquor_type) 조합으로 여러 행이 정상.
-- MANUAL wins: 같은 (제품, 주종)에 MANUAL이 있으면 AUTO는 그 조합을 적재하지 않는다(uq가 최종 방어선).

CREATE TABLE IF NOT EXISTS product_liquor_type (
    id                  BIGSERIAL PRIMARY KEY,             -- 서러게이트 PK(의미 없음)
    source_row_ref      INT       NOT NULL,                -- 원본 product raw 추적 참조(= product_brewery_link.source_row_ref)
    brewery_id          TEXT      NOT NULL,                -- ★조회 편의 비정규화(양조장별 GROUP BY 집계를 link 재조인 없이). link가 이미 brewery_id를 담는 선례 동일
    liquor_type         TEXT      NOT NULL,                -- 탁주/약주/청주/증류주/과실주/기타. AUTO는 '기타' 미생성(판정 안 됨≠기타)
    source              TEXT      NOT NULL,                -- AUTO(추론) / MANUAL(수기 시드)
    recheck_flag        BOOLEAN   NOT NULL,                -- 재점검 필요. AUTO 전건 true(미검증), MANUAL false
    suppressed_from_tab BOOLEAN   NOT NULL DEFAULT false,  -- C-4 향미이중 억제 표시(행은 남기고 표시만)
    matched_keyword     TEXT,                              -- 어느 키워드에 걸렸는지(검수·디버깅 근거). MANUAL은 null
    created_at          TIMESTAMP NOT NULL,
    CONSTRAINT fk_product_liquor_type_link FOREIGN KEY (source_row_ref) REFERENCES product_brewery_link (source_row_ref),
    CONSTRAINT fk_product_liquor_type_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (brewery_id),
    CONSTRAINT uq_product_liquor_type_row_type UNIQUE (source_row_ref, liquor_type),
    CONSTRAINT ck_product_liquor_type_type   CHECK (liquor_type IN ('탁주', '약주', '청주', '증류주', '과실주', '기타')),
    CONSTRAINT ck_product_liquor_type_source CHECK (source IN ('AUTO', 'MANUAL'))
);
CREATE INDEX IF NOT EXISTS ix_product_liquor_type_brewery ON product_liquor_type (brewery_id);
