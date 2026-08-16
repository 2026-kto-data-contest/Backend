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
    -- 좌표 파생 자리 (#28 8단계 카카오 지오코딩이 UPDATE). ★address는 항상 raw 원문 — 좌표만 파생.
    latitude                NUMERIC(9,6),                   -- 위도(카카오 y). 한국 범위 33~39 검증 후 저장
    longitude               NUMERIC(9,6),                   -- 경도(카카오 x). 한국 범위 124~132 검증 후 저장
    coord_source            VARCHAR(32),                    -- 좌표 출처(폴백 단계 식별). 3값 — 실사용만
    geocoded_at             TIMESTAMPTZ,                    -- 카카오 호출로 좌표 확정한 시각(UTC)
    -- 콘텐츠 매칭 파생 자리 (10단계 TourAPI 매칭이 UPDATE). ★FK는 파일 말미 ALTER로 건다 —
    --   tour_content가 이 CREATE보다 뒤에 정의되므로 인라인 REFERENCES는 순서상 불가.
    content_id              TEXT,                           -- 확정 매칭 TourAPI contentid → tour_content.content_id (nullable — 미매칭 정상)
    content_matched_at      TIMESTAMPTZ,                    -- 매칭 확정 시각(UTC). ★source 컬럼 없음 — content_id IS NOT NULL과 동치라 무정보(수정2)
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
-- 상세 필드 편입(#50) — brewery가 이미 존재하는 DB는 CREATE TABLE IF NOT EXISTS가 통째로 건너뛰어
-- 아래 컬럼이 생기지 않는다. 신규 설치는 CREATE TABLE로 반영되지 않으므로(위 CREATE에 미포함)
-- 신규·기존 DB 모두 아래 멱등 ALTER로만 반영한다. 전례: 위 좌표 4컬럼 ALTER.
--   기준일 분리: operating_hours~accom_count·phone은 관광공사/카카오(현행), founded_year~designation_note는
--   농림부 2019 지정현황. ★농림부 소재지·주종·업체명은 컬럼으로 만들지 않는다(2019값이 마스터 오염 — 시드에도 미포함).
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS phone               TEXT;
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS phone_source        TEXT;
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS operating_hours     TEXT;
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS rest_date           TEXT;
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS parking_info        TEXT;
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS accom_count         TEXT;
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS founded_year        INT;
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS representative_name TEXT;
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS designated_year     INT;
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS designation_note    TEXT;
-- phone_source는 phone 출처(TOUR=관광공사 우선, KAKAO=카카오 보충). phone이 null이면 source도 null.
-- DO/$$ 금지(ScriptUtils ';' 분할 파손) — 단문 2개(DROP IF EXISTS→ADD)로 멱등화. 위 coord CHECK 선례 동일.
ALTER TABLE brewery DROP CONSTRAINT IF EXISTS ck_brewery_phone_source;
ALTER TABLE brewery ADD CONSTRAINT ck_brewery_phone_source CHECK (phone_source IN ('TOUR', 'KAKAO'));
-- 카카오 place URL 편입(#54) — 상세 지도 딥링크. 16단계 카카오 place 시드가 UPDATE(전용 UPDATE 단계 — 마스터
-- 로드는 기존 brewery_id를 skip하므로 여기 편입, 백필 함정 방어). 위 상세 필드 4컬럼 ALTER와 동일 멱등 패턴.
--   ★값은 카카오가 준 http:// 원문 그대로 저장한다(접속 시 https 리다이렉트 — 변환 금지). 경쟁 소스 없음.
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS kakao_place_url TEXT;
-- 부채 #15 제거(#56) — image_url은 소스 미확정 격리 컬럼(C-10)으로 만들었으나 전 행 null이고 읽는 코드가
-- 0건이다. 대표 이미지 소스가 tour_content.first_image로 확정(#48)되어 의미를 잃었다.
--   ★위 CREATE TABLE 정의에서 지우는 것만으로는 기존 DB에서 사라지지 않는다(테이블 존재 시 통째 스킵).
--   ★ddl-auto=validate는 엔티티에 없는 잔여 컬럼을 문제 삼지 않아 조용히 남는다. DROP을 명시한다.
--   IF EXISTS라 매 기동 재실행돼도 멱등(ck_brewery_coord_source 선례).
ALTER TABLE brewery DROP COLUMN IF EXISTS image_url;
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
    alcohol_min       NUMERIC(4,1),                       -- 도수 파싱 최솟값(#35). nullable — 파싱 실패 시 null. 단일값은 min==max
    alcohol_max       NUMERIC(4,1),                       -- 도수 파싱 최댓값(#35). nullable. 구간(저/중/고)은 조회 시점 계산(미저장)
    created_at        TIMESTAMP NOT NULL,
    CONSTRAINT fk_product_brewery_link_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (brewery_id),
    CONSTRAINT uq_product_brewery_link_source_row UNIQUE (source_row_ref),
    CONSTRAINT ck_product_brewery_link_join_source CHECK (join_source IN ('AUTO', 'OVERRIDE_NAME', 'OVERRIDE_ROW', 'UNMATCHED'))
);
CREATE INDEX IF NOT EXISTS ix_product_brewery_link_brewery ON product_brewery_link (brewery_id);

-- 도수 파싱 마이그레이션(#35) — product_brewery_link가 이미 존재하는 DB는 CREATE TABLE IF NOT EXISTS가
-- 통째로 건너뛰어 위 두 컬럼이 생기지 않는다. 신규 설치는 CREATE TABLE로 반영되고, 기존 DB는 아래 멱등 ALTER로 반영.
ALTER TABLE product_brewery_link ADD COLUMN IF NOT EXISTS alcohol_min NUMERIC(4,1);
ALTER TABLE product_brewery_link ADD COLUMN IF NOT EXISTS alcohol_max NUMERIC(4,1);

-- ────────────────────────────────────────────────────────────────────────────
-- 회원·인증 도메인. 카카오 회원번호가 외부 인증 식별자이며 카카오 토큰은 저장하지 않는다.

CREATE TABLE IF NOT EXISTS member_account (
    id            BIGSERIAL PRIMARY KEY,
    kakao_user_id BIGINT    NOT NULL,
    nickname      TEXT      NOT NULL,
    email         TEXT,
    role          TEXT      NOT NULL DEFAULT 'USER',
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    post_login_return_to TEXT,
    created_at    TIMESTAMP NOT NULL,
    updated_at    TIMESTAMP NOT NULL,
    CONSTRAINT uq_member_account_kakao_user_id UNIQUE (kakao_user_id),
    CONSTRAINT ck_member_account_role CHECK (role IN ('USER', 'ADMIN'))
);
ALTER TABLE member_account ADD COLUMN IF NOT EXISTS post_login_return_to TEXT;

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

INSERT INTO terms_definition (code, version, title, required, display_order, content_url)
VALUES
    ('SERVICE_USE', '1.0', '서비스 이용약관 동의', TRUE, 1, '/terms/service-use'),
    ('PRIVACY', '1.0', '개인정보 수집 및 이용 동의', TRUE, 2, '/terms/privacy'),
    ('LOCATION', '1.0', '위치기반 서비스 이용약관', FALSE, 3, '/terms/location'),
    ('MARKETING', '1.0', '마케팅 정보 수신 동의 (카카오톡, 이메일 등)', FALSE, 4, '/terms/marketing')
ON CONFLICT (code, version) DO UPDATE SET
    title = EXCLUDED.title,
    required = EXCLUDED.required,
    display_order = EXCLUDED.display_order,
    content_url = EXCLUDED.content_url;

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

-- ────────────────────────────────────────────────────────────────────────────
-- 파생층 (11단계, 이슈 #43). 양조장 특징 태그 5종 — product_brewery_link로 연결된 제품의 서술 컬럼
-- (product_raw)에 확정 규칙을 적용해 양조장으로 롤업한다. 양조장 grain: (brewery_id, feature_type) 1행.
-- ★확정 파생(주종과 달리 검수·MANUAL 없음) → 삭제형 diff로 유령 행 없음(FeatureRollupService).
--   골든(snapshot 2026-08-01, DISTINCT brewery_id): 수상이력 36·식품명인 9·유기농 7·무형문화재 3·대통령상 2.
CREATE TABLE IF NOT EXISTS brewery_feature_tag (
    id              BIGSERIAL PRIMARY KEY,             -- 서러게이트 PK(의미 없음)
    brewery_id      TEXT      NOT NULL,                -- 태그 소유 양조장 → brewery.brewery_id
    feature_type    TEXT      NOT NULL,                -- 수상이력/식품명인/유기농/무형문화재/대통령상
    source_row_ref  INT       NOT NULL,                -- 이 특징 유발 대표 제품 raw 참조(= product_brewery_link.source_row_ref)
    matched_keyword TEXT,                              -- 걸린 키워드(디버깅). 수상이력은 presence 규칙이라 null
    created_at      TIMESTAMP NOT NULL,
    CONSTRAINT fk_brewery_feature_tag_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (brewery_id),
    CONSTRAINT uq_brewery_feature_tag_brewery_type UNIQUE (brewery_id, feature_type),
    CONSTRAINT ck_brewery_feature_tag_type CHECK (feature_type IN ('수상이력', '식품명인', '유기농', '무형문화재', '대통령상'))
);
CREATE INDEX IF NOT EXISTS ix_brewery_feature_tag_brewery ON brewery_feature_tag (brewery_id);
-- CHECK 멱등화(전례: ck_brewery_coord_source). CREATE TABLE IF NOT EXISTS는 테이블이 이미 있으면 통째로
-- 건너뛰므로, 특징 종류가 추가돼도 CHECK가 옛 목록에 머문다. DROP IF EXISTS→ADD 단문 2개로 매 기동 재적용한다
-- (DO/$$ 미사용 — ScriptUtils가 ';'로 쪼개며 달러쿼트를 깨뜨림).
ALTER TABLE brewery_feature_tag DROP CONSTRAINT IF EXISTS ck_brewery_feature_tag_type;
ALTER TABLE brewery_feature_tag ADD CONSTRAINT ck_brewery_feature_tag_type CHECK (feature_type IN ('수상이력', '식품명인', '유기농', '무형문화재', '대통령상'));

-- ────────────────────────────────────────────────────────────────────────────
-- 파생층 (관광 콘텐츠 캐시·매칭, TourAPI KorService2). 9단계 근접 캐싱 + 10단계 매칭.
-- ★tour_content를 brewery FK ALTER보다 먼저 정의한다(brewery.content_id → tour_content 참조 대상).
--
-- content_type_id에 CHECK를 걸지 않는다: 강화 표본 실측 8종 혼재(39/28/12/14/32/38/25/15)이나
--   표본 1곳 기준이라 타 지역 미관측 타입이 나올 수 있다 — 관측 안 된 값을 배제하지 않는다(교정4).
-- overview·overview_fetched_at은 컬럼만 만들고 이번 사이클엔 채우지 않는다(둘 다 NULL 유지).
--   ★이유(수정4): overview_fetched_at은 "이 콘텐츠 상세를 받았는가"의 유일한 판별 컬럼이다. 접지분
--   일부만 채우면 NULL이 "안 받음"인지 "받았는데 overview 없음"인지 구분 불가 → 판별력이 사라진다.
--   그래서 detailCommon2로 접지할 때도 overview는 저장하지 않고 둘 다 NULL로 남긴다(후속 전량 사이클 몫).
CREATE TABLE IF NOT EXISTS tour_content (
    content_id            TEXT      PRIMARY KEY,             -- TourAPI contentid 자연키 PK(문자열 원문)
    content_type_id       TEXT      NOT NULL,                -- contenttypeid. ★CHECK 없음(교정4 — 미관측 타입 배제 금지)
    title                 TEXT,                              -- 콘텐츠명(원문)
    addr1                 TEXT,                              -- 주소
    addr2                 TEXT,                              -- 상세주소(빈 문자열 다수)
    zipcode               TEXT,                              -- 우편번호
    area_code             TEXT,                              -- areacode(빈 문자열 관측)
    sigungu_code          TEXT,                              -- sigungucode
    cat1                  TEXT,                              -- 대분류(type12 목록응답은 빈 문자열 관측)
    cat2                  TEXT,
    cat3                  TEXT,
    lcls_systm1           TEXT,                              -- 분류체계 lclsSystm1~3
    lcls_systm2           TEXT,
    lcls_systm3           TEXT,
    ldong_regn_cd         TEXT,                              -- 법정동 시도/시군구 코드
    ldong_signgu_cd       TEXT,
    -- 좌표: mapx=경도/mapy=위도. 저장 직전 위도 33~39·경도 124~132 검증 통과분만(축 전도 fail-fast).
    latitude              NUMERIC(9,6),                      -- mapy
    longitude             NUMERIC(9,6),                      -- mapx
    mlevel                TEXT,                              -- 지도 레벨(원문 문자열)
    first_image           TEXT,                              -- 대표이미지 원본(결측 다수 — 결측률 리포트)
    first_image2          TEXT,                              -- 대표이미지 썸네일
    cpyrht_div_cd         TEXT,                              -- 저작권 구분(Type1/Type3 등)
    source_created_time   TEXT,                              -- TourAPI createdtime 원문(YYYYMMDDHHMMSS)
    source_modified_time  TEXT,                              -- TourAPI modifiedtime 원문 — upsert 변경 감지 기준
    overview              TEXT,                              -- ★컬럼만 — 이번 미충전(NULL 유지, 위 주석 참조)
    overview_fetched_at   TIMESTAMPTZ,                       -- ★컬럼만 — 이번 미충전(NULL 유지, 판별력 보존)
    created_at            TIMESTAMP NOT NULL,                -- 최초 적재 시각(UTC)
    updated_at            TIMESTAMP NOT NULL                 -- 최종 upsert 시각(UTC)
);
CREATE INDEX IF NOT EXISTS ix_tour_content_type ON tour_content (content_type_id);

-- 9단계 근접 캐싱 산물: 양조장 반경 내 콘텐츠(20km 실측 캐시). 복합 PK로 (양조장,콘텐츠) 1행.
-- ★자기 자신 제외는 별도 플래그 컬럼 없이 brewery_nearby.content_id = brewery.content_id 파생으로 판정.
--   양조장 A가 양조장 B 반경에 섞이는 것은 정상(코스 시나리오) — 파생이라 보존된다.
CREATE TABLE IF NOT EXISTS brewery_nearby (
    brewery_id            TEXT      NOT NULL,                -- 반경 기준 양조장 → brewery.brewery_id
    content_id            TEXT      NOT NULL,                -- 반경 내 콘텐츠 → tour_content.content_id
    distance_m            NUMERIC,                           -- TourAPI 응답 dist(m). 정렬·200m 검증 근거
    radius_m              INT       NOT NULL,                -- 이 캐싱에 쓴 반경(tour.api.radius-m 스냅샷)
    created_at            TIMESTAMP NOT NULL,
    CONSTRAINT pk_brewery_nearby PRIMARY KEY (brewery_id, content_id),
    CONSTRAINT fk_brewery_nearby_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (brewery_id),
    CONSTRAINT fk_brewery_nearby_content FOREIGN KEY (content_id) REFERENCES tour_content (content_id)
);
CREATE INDEX IF NOT EXISTS ix_brewery_nearby_brewery ON brewery_nearby (brewery_id);
CREATE INDEX IF NOT EXISTS ix_brewery_nearby_content ON brewery_nearby (content_id);

-- brewery 콘텐츠 매칭 마이그레이션(기존 DB) — CREATE TABLE brewery는 이미 존재하면 통째로 건너뛰므로
-- content_id·content_matched_at 컬럼과 FK를 아래 멱등 문으로 보강한다(좌표 4컬럼 ALTER 선례 동일).
-- ★FK는 tour_content가 위에서 먼저 정의된 뒤에야 걸 수 있다(순서 의존). DO/$$ 미사용 — DROP IF EXISTS→ADD.
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS content_id         TEXT;
ALTER TABLE brewery ADD COLUMN IF NOT EXISTS content_matched_at TIMESTAMPTZ;
ALTER TABLE brewery DROP CONSTRAINT IF EXISTS fk_brewery_content;
ALTER TABLE brewery ADD CONSTRAINT fk_brewery_content FOREIGN KEY (content_id) REFERENCES tour_content (content_id);
CREATE INDEX IF NOT EXISTS ix_brewery_content ON brewery (content_id);

-- ────────────────────────────────────────────────────────────────────────────
-- 파생층 (15단계, 이슈 #52). aT 전통주 체험 프로그램(공공데이터 15089109, 기준일 2021-09-17)을
-- experience_match_seed.json(양조장명→brewery_id)으로 매칭해 편입한다. 양조장 1:N.
-- ★자연키 (brewery_id, program_name) — 원본 52행 전수 실측 중복 0(program_name·place·전필드 모두).
--   100% 외부 API 파생·검수 워크플로 없음 → 삭제형 diff로 유령 방지(FeatureRollupService 선례).
--   payload(내용·장소·소요시간·비용)가 있어 값 인지 diff(insert/update/delete) — 특징 태그와 다른 점.
-- ★주소·연락처·홈페이지·주종·상시/예약방문 컬럼을 만들지 않는다: 전부 2021 값이라 마스터(2024-12-31)를
--   오염시킨다. 컬럼 부재로 구조적으로 봉쇄한다(예술주조 이전·제주샘주 애언로/애원로 오타 실증). 전화번호도 금지.
-- ★CHECK 없음: cost는 자유 정수(0=무료·null=미입력 구분), duration은 자유 텍스트("H:MM" 원문). enum 컬럼 부재.
CREATE TABLE IF NOT EXISTS brewery_experience (
    id            BIGSERIAL PRIMARY KEY,             -- 서러게이트 PK(의미 없음)
    brewery_id    TEXT      NOT NULL,                -- 체험 소유 양조장 → brewery.brewery_id (시드 매칭 결과)
    program_name  TEXT      NOT NULL,                -- 체험프로그램명(원문). 자연키 일부
    content       TEXT,                              -- 내용(원문, 실측 결측 0 — 방어적 nullable)
    place         TEXT,                              -- 장소(원문, 결측 1 → null 정규화)
    duration      TEXT,                              -- 소요시간 원문 "H:MM"(결측 9 → null). 분 변환 안 함(원문 보존)
    cost          INTEGER,                           -- 투어비용(원). 0=무료·null=미입력 엄격 구분(최대 350000)
    created_at    TIMESTAMP NOT NULL,                -- 최초 적재 시각(UTC)
    updated_at    TIMESTAMP NOT NULL,                -- 값 갱신(update path) 최종 반영 시각(UTC)
    CONSTRAINT fk_brewery_experience_brewery FOREIGN KEY (brewery_id) REFERENCES brewery (brewery_id),
    CONSTRAINT uq_brewery_experience_brewery_program UNIQUE (brewery_id, program_name)
);
CREATE INDEX IF NOT EXISTS ix_brewery_experience_brewery ON brewery_experience (brewery_id);
