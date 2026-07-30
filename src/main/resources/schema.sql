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
