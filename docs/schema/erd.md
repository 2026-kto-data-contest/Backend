# ERD 설계 문서 — 전통주로 백엔드

- **성격**: ERDCloud 이관용 스키마 설계 문서. DBML(임포트용) + 표(병기)로 이중 기술.
- **데이터 원천**: 공공데이터포털 `찾아가는양조장정보`(59행) · `전통주정보`(1,215행). 원본 스냅샷은 `src/test/resources/golden/` 골든 픽스처로 보존(sha256 매니페스트 포함).
- **논리타입만 사용**(varchar/int/bigint/enum/bool/timestamp/date). DB엔진 전용 타입·확장문법 없음.
- **enum 값은 ASCII 코드**로 정의(ERDCloud 임포트 안정성) + 한글은 Note로 병기.

---

## 1. 설계 원칙

| 원칙 | 내용 |
|---|---|
| 런타임 조인·외부 API 호출 금지 | 모든 조회는 배치로 물질화된 파생·롤업·조인 테이블에서 SELECT |
| 이름 문자열 참조 금지 | 모든 조인/보정 참조는 내부키 `brewery_id`·`product_id` |
| 채번 불변·append-only | `brewery_id`(BRW-xxx)는 최초 채번 후 불변. 재적재 시 신규만 append, 기존 행은 정규화명 매칭으로 기존 id 재사용 |
| 원본 보존 | raw 층은 원본 필드를 그대로 보존(null과 빈문자열 구분 유지). 파생값은 파생 층에서만 |
| 재적재 방어 | raw 복합PK(+snapshot_date)·source_version·감사 컬럼·AUTO/MANUAL 태그로 스냅샷 갱신에 대비 |
| MANUAL wins | 수기 보정(`manual_override`)은 자동 재생성 결과 위에 id 매칭으로 재적용되는 불변 원장 |

### 설계 결정 (근거 포함)

**결정 A — brewery PK 전략: 자연키 `brewery_id`(BRW-xxx, varchar) 직접 PK**

| 후보 | 장점 | 단점 |
|---|---|---|
| **(A) 자연키 BRW-xxx = PK** ✅채택 | FK가 자기설명적·override 참조 안정·조인 디버깅 쉬움·별도 조회 조인 불필요 | varchar 인덱스가 int보다 약간 큼 |
| (B) surrogate bigint PK + BRW-xxx unique 업무키 | 인덱스 최소·포맷 변경 내성 | 표시·override에 BRW-xxx 얻으려 항상 조인 1회 |

- 자연키 PK의 유일한 실질 리스크는 '키가 변한다'인데, 본 프로젝트는 **채번 후 절대 불변·append-only가 확정 전제**이므로 그 리스크가 제거된다. 59행 소규모라 인덱스 크기 차이는 무의미.

**결정 B — 주종 태그 억제 방식: `source enum(AUTO/MANUAL)` + `suppressed_from_tab bool` 2컬럼 분리**

- 단일 enum(AUTO/MANUAL/SUPPRESSED)은 ①태그 출처 ②탭 억제 여부라는 **두 직교 축을 한 컬럼에 뭉갠다** — "MANUAL로 검수했으나 특정 탭에선 억제" 조합을 표현 못 함.
- 2축 분리 시 태그 행 자체는 보존하고 롤업/탭 질의에서만 `suppressed_from_tab=false` 필터 — '특정 주종 태그만 탭에서 억제' 같은 실제 요구를 표현 가능.

---

## 2. 채번표 — brewery_id 매핑 59건 (채번원장 `brewery_id_ledger.json`, 상호명 가나다순·append-only·불변)

> **[갱신 2026-07-30]** 초안(20260728)의 원본배열순 채번표는 **폐기**한다. 이 방 3b 결정으로 채번 기준이
> **상호명 가나다순(NFC 코드포인트 오름차순, Java String.compareTo 동치)**으로 재확정되어 초안과 BRW 번호가
> 전면 달라졌다(예: 갈기산 056→001, 조옥화안동소주 059→003). 진실원천은 봉인 채번원장
> `src/main/resources/brewery_id_ledger.json`이며 아래 표는 그 사본이다.

- 컬럼: `brewery_id | 상호명(원장 원문 NFC) | norm(정규화명, 조인 매칭키)`
- ★`sido`·`region`·`join_status`·`liquor_status`는 계산 파생 컬럼이라 이 표에 싣지 않는다 — 주소 파싱/조인/주종
  롤업은 후속 단계(3c-2/3d)가 채운다(3c-1은 원장·raw에서 그대로 오는 값만 적재).

| brewery_id | 상호명(원장) | norm |
|---|---|---|
| BRW-001 | 갈기산 | 갈기산 |
| BRW-002 | 고도리 와이너리 | 고도리와이너리 |
| BRW-003 | 국가유산·명인 조옥화 안동소주 | 국가유산·명인조옥화안동소주 |
| BRW-004 | 국순당 | 국순당 |
| BRW-005 | 그린영농조합 | 그린 |
| BRW-006 | 금정산성 토산주 | 금정산성토산주 |
| BRW-007 | 금풍양조 | 금풍 |
| BRW-008 | 다도참주가 | 다도참 |
| BRW-009 | 대강양조장 | 대강 |
| BRW-010 | 대대로영농조합법인 | 대대로 |
| BRW-011 | 덕유양조 | 덕유 |
| BRW-012 | 도란원 | 도란원 |
| BRW-013 | 두레양조 | 두레 |
| BRW-014 | 맑은내일 | 맑은내일 |
| BRW-015 | 명인안동소주 | 명인안동소주 |
| BRW-016 | 모월 | 모월 |
| BRW-017 | 문경주조 | 문경 |
| BRW-018 | 밀과노닐다 | 밀과노닐다 |
| BRW-019 | 밝은세상영농조합 | 밝은세상 |
| BRW-020 | 배상면주가 | 배상면 |
| BRW-021 | 배혜정도가 | 배혜정 |
| BRW-022 | 복순도가 | 복순 |
| BRW-023 | 산막와이너리 | 산막와이너리 |
| BRW-024 | 산머루농원 | 산머루 |
| BRW-025 | 솔송주 | 솔송주 |
| BRW-026 | 수도산와이너리 | 수도산와이너리 |
| BRW-027 | 술빚는 전가네 | 술빚는전가네 |
| BRW-028 | 술샘 | 술샘 |
| BRW-029 | 술아원 | 술아원 |
| BRW-030 | 시나브로 와이너리 | 시나브로와이너리 |
| BRW-031 | 신평양조장 | 신평 |
| BRW-032 | 양촌양조 | 양촌 |
| BRW-033 | 양촌와이너리 | 양촌와이너리 |
| BRW-034 | 여포와인농장 | 여포와인 |
| BRW-035 | 예산사과와인 | 예산사과와인 |
| BRW-036 | 예술주조 | 예술 |
| BRW-037 | 오미나라 | 오미나라 |
| BRW-038 | 오산양조 | 오산 |
| BRW-039 | 우리술 | 우리술 |
| BRW-040 | 울진술도가 | 울진술 |
| BRW-041 | 은척양조장 | 은척 |
| BRW-042 | 이원양조장 | 이원 |
| BRW-043 | 인천탁주 | 인천탁주 |
| BRW-044 | 장희도가 | 장희 |
| BRW-045 | 제주고소리술익는집 | 제주고소리술익는집 |
| BRW-046 | 제주샘주 | 제주샘주 |
| BRW-047 | 조은술 세종 | 조은술세종 |
| BRW-048 | 좋은술 | 좋은술 |
| BRW-049 | 중원당 | 중원당 |
| BRW-050 | 지리산 운봉주조 | 지리산운봉 |
| BRW-051 | 청산녹수 | 청산녹수 |
| BRW-052 | 추성고을 | 추성고을 |
| BRW-053 | 태인합동주조장 | 태인합동 |
| BRW-054 | 풍정사계 | 풍정사계 |
| BRW-055 | 하미앙 와인밸리 | 하미앙와인밸리 |
| BRW-056 | 한국애플리즈 | 한국애플리즈 |
| BRW-057 | 한국와인 | 한국와인 |
| BRW-058 | 한산소곡주 | 한산소곡주 |
| BRW-059 | 해창주조장 | 해창 |

- 정렬 기준: 골든 20260728 brewery raw 상호명 원문 NFC → 유니코드 코드포인트 오름차순. 재현성 위해 골든 시점 고정.
- 번호 순서에 의미 없음(단순 식별자). 신규 양조장은 정렬 무시하고 BRW-060~ append(기존 001~059 절대 불변).

---

## 3. 테이블 정의 — DBML

**층 구성**: raw(원본 스냅샷) → 파생(정규화·상태·롤업) → 조인(다대다 중간) → manual_override(수기 보정 원장).
**재적재 태그**: `[AUTO재생성]`(재적재 시 덮어씀) / `[MANUAL보존]`(id 매칭으로 유지) / `[불변]`.

### 3.0 enum 정의

```dbml
enum visit_state {
  Y      // 가능
  N      // 불가
  UNKNOWN // 미입력(null). boolean 금지 근거: 예약 null 52%·상시 null 15%
}

enum active_state {
  Y
  N
}

enum join_status {
  JOINED
  UNJOINED
}

enum liquor_status {
  TAGGED
  UNTAGGED
  NA        // UNJOINED이면 NA
}

enum region_code {
  CAPITAL      // 수도권(서울·인천·경기)
  GANGWON      // 강원
  CHUNGCHEONG  // 충청(대전·세종·충북·충남)
  GYEONGSANG   // 경상(부산·대구·울산·경북·경남)
  JEOLLA       // 전라(광주·전북·전남)
  JEJU         // 제주
}

enum liquor_type_code {
  TAKJU        // 탁주
  YAKJU        // 약주
  CHEONGJU     // 청주
  JEUNGRYU     // 증류주
  GWASILJU     // 과실주
}

enum tag_source {
  AUTO         // 파생규칙 자동 태깅(재적재 시 재생성)
  MANUAL       // 수기 검수(재적재 시 보존)
}

enum override_source {
  MANUAL       // manual_override는 항상 MANUAL. 확장 대비 enum
}

enum override_type {
  NAME_MAP   // 양조장명 norm으로 매칭
  ROW_PIN    // 제품명으로 특정 행 고정(원본 양조장 필드 null)
}

enum match_key_kind {
  BREWERY_NORM  // match_key = 양조장명 norm
  PRODUCT_NAME  // match_key = 제품명
}

enum override_reason {
  ADDR_EXACT    // 주소 완전 일치
  ADDR_STRONG   // 주소 강한 근거
  MANUAL_DOMAIN // 도메인 지식 수기 확정(원본 양조장 필드 결손·recheck 대상)
}
```

> `sido`는 17개 확장 가능성(현재 12개 관측)이라 enum 대신 `varchar`로 두고 Note 병기. 광역권만 enum 고정.

### 3.1 RAW 층

#### `brewery_raw` — 찾아가는양조장정보 원본 스냅샷 `[AUTO재생성]`

```dbml
Table brewery_raw {
  brewery_id       varchar      [not null, note: '채번 FK(BRW-xxx). 정규화명 매칭으로 배정, 불변']
  snapshot_date    date         [not null, note: '수집 스냅샷(manifest collected_at). 재적재 전후 diff']
  source_version   varchar      [not null, note: 'uddi 데이터셋 버전(예: 20241231)']
  brewery_name_raw varchar      [not null, note: '원본 상호명. 이름은 참조키 아님, 보존만']
  address_raw      varchar      [not null, note: '원본 주소. 시도 앞토큰 파싱 원천']
  homepage_url     varchar      [null, note: '원본 홈페이지. 한글도메인·punycode·SNS 혼재']
  always_visit_raw varchar      [null, note: '상시방문 원본 Y/N/null. null 15%. 원본 그대로(3-state 변환은 파생층)']
  reservation_visit_raw varchar [null, note: '예약방문 원본 Y/N/null. null 52%. 원본 그대로']
  is_active_raw    varchar      [not null, note: '사용여부 원본(전건 Y). 원본 보존']
  view_count       int          [not null, note: '누적 조회수. 재적재마다 변동 → 기본정렬 키 금지. 스냅샷 값']
  loaded_at        timestamp    [not null, note: '적재 시각(감사)']

  indexes {
    (brewery_id, snapshot_date) [pk]
  }
}
```

#### `product_raw` — 전통주정보 원본 스냅샷 `[AUTO재생성]`

실측 12필드 그대로 + 스냅샷. `(제품명,양조장)` 조합에 중복이 존재해 자연키 불가 → surrogate `product_id`.

```dbml
Table product_raw {
  product_id         varchar   [not null, note: 'surrogate 채번(PRD-xxxxx), append-only']
  snapshot_date      date      [not null, note: '수집 스냅샷']
  source_version     varchar   [not null, note: 'uddi 버전']
  product_name       varchar   [not null, note: '제품명 원본']
  brewery_name_raw   varchar   [null, note: '원본 양조장 필드. null/공백 존재. 이름은 참조키 아님']
  brewery_address_raw varchar  [null, note: '양조장주소 원본. 시도접두 생략행 존재 → 매칭 비교 시 참고']
  intro              varchar   [null, note: '제품소개']
  alcohol_pct_raw    varchar   [null, note: '알콜도수 원본(string). 파싱은 파생층']
  volume_raw         varchar   [null, note: '용량 원본(string)']
  ingredient         varchar   [null, note: '성분']
  features           varchar   [null, note: '특징']
  note_etc           varchar   [null, note: '특이사항']
  award              varchar   [null, note: '수상경력']
  is_for_sale_raw    varchar   [not null, note: '판매여부 Y/N']
  homepage_url       varchar   [null, note: '홈페이지주소 원본']
  loaded_at          timestamp [not null]

  indexes {
    (product_id, snapshot_date) [pk]
    (product_name, brewery_name_raw)
  }
}
```

### 3.2 파생 층

#### `brewery` — 양조장 코어 dimension(채번원장 실체) `[혼합: id·상호명·norm·raw속성 확정 / 계산파생 후속 UPDATE]`

> **[갱신 2026-07-30]** 실제 적재 스키마(3c-1)로 교체. 초안 대비 변경: `normalized_name/display_name` →
> `norm/business_name`, raw 원문 `address·homepage_url·view_count` 추가, 방문 2필드를 `reservation_visit_state·
> always_visit_state`(visit_state 3-state)로 확정. 진실원천은 `src/main/resources/schema.sql`.

§2 채번원장이 시드. ★컬럼 생성 ≠ 값 확정 — raw에서 그대로 오는 값만 3c-1이 채우고, 계산 파생(sido·region·
join_status·liquor_status)은 컬럼만 두고 후속 단계가 UPDATE한다.

```dbml
Table brewery {
  brewery_id              varchar       [pk, note: 'BRW-xxx 자연키 PK(원장). 서러게이트 없음·불변·append-only']
  business_name           varchar       [not null, note: '상호명(골든 원문 NFC). ★UNIQUE 금지(개명 대비)·인덱스만']
  norm                    varchar       [not null, note: '원장 norm(정규화명). 조인 매칭키 재현용']
  address                 varchar       [not null, note: '골든 주소 원문(무손실, 골든 null 0건)']
  homepage_url            varchar       [null, note: '골든 홈페이지(null 1건)']
  view_count              bigint        [not null, note: '골든 조회수(원본 number). 기본정렬 키 금지']
  reservation_visit_state visit_state   [not null, note: '3-state. raw null→UNKNOWN(골든 null 31/59)']
  always_visit_state      visit_state   [not null, note: '3-state. raw null→UNKNOWN(골든 null 9/59)']
  sido                    varchar       [null, note: '★3c-1 미계산. 주소 파싱 결과 자리(후속 UPDATE)']
  region                  varchar       [null, note: '★3c-1 미계산. 광역권 매핑 자리(후속 UPDATE)']
  join_status             join_status   [not null, default: 'UNJOINED', note: '★3c-1 초기값. 3c-2 조인이 UPDATE']
  liquor_status           liquor_status [not null, default: 'NA', note: '★3c-1 초기값. 3d 주종롤업이 UPDATE']
  image_url               varchar       [null, note: '소스 미확정(C-10) 격리 자리']
  created_at              timestamp     [not null]
  updated_at              timestamp     [not null]

  indexes {
    business_name
    norm
  }
}
```

#### `product` — 제품 코어 dimension `[혼합]`

```dbml
Table product {
  product_id     varchar     [pk, note: 'PRD-xxxxx. product_raw와 동일 채번']
  brewery_id     varchar      [null, note: 'FK. 조인 해결 시 채움. 미해결이면 null']
  product_name   varchar      [not null]
  alcohol_pct    varchar      [null, note: '알콜도수 정규화값(원본 string 파싱). 파싱실패 null']
  volume_ml      int          [null, note: '용량 파싱값. 실패 null']
  is_for_sale    active_state [not null, note: '판매여부 2-state']
  created_at     timestamp    [not null]
  updated_at     timestamp    [not null]

  indexes {
    brewery_id
  }
}
```

#### `liquor_type` — 주종 카테고리 사전(5종) `[불변·시드]`

```dbml
Table liquor_type {
  liquor_type_code liquor_type_code [pk, note: '5종 카테고리']
  name_ko          varchar          [not null, note: '탁주/약주/청주/증류주/과실주']
  display_order    int              [not null, note: '탭 노출 순서']
}
```

시드: `(TAKJU,탁주),(YAKJU,약주),(CHEONGJU,청주),(JEUNGRYU,증류주),(GWASILJU,과실주)`.

### 3.3 조인(다대다) 층

#### `brewery_product` — 양조장↔제품 조인 매핑 `[혼합: AUTO링크 / MANUAL재매핑 보존]`

런타임 조인 금지 원칙 → 조인 결과를 배치로 이 테이블에 물질화. **참조키는 brewery_id(이름 아님)**.

```dbml
Table brewery_product {
  product_id       varchar    [not null, note: 'FK product']
  brewery_id       varchar    [not null, note: 'FK brewery. 참조키=brewery_id(이름 금지)']
  match_source     tag_source [not null, note: 'AUTO(정규화매칭) / MANUAL(수기 재매핑). MANUAL wins']
  matched_raw_name varchar    [null, note: '어느 raw 양조장명이 매칭됐는지(1:N 매칭 감사)']
  created_at       timestamp  [not null]
  updated_at       timestamp  [not null]

  indexes {
    (product_id, brewery_id) [pk]
    brewery_id
  }
}
```

> 카디널리티는 사실상 product→brewery 1:N이나, ①1:N 매칭 감사 ②수기 재매핑을 brewery_id 기준으로 안정 보관하기 위해 **링크 테이블로 분리**(재적재에도 brewery_id 매칭으로 보존).

#### `product_liquor_type` — 제품↔주종 다중 태깅 `[혼합]`

다중 태깅 + 억제 2컬럼 분리(결정 B).

```dbml
Table product_liquor_type {
  product_id        varchar          [not null, note: 'FK product']
  liquor_type_code  liquor_type_code [not null, note: 'FK liquor_type']
  source            tag_source       [not null, note: '결정B: AUTO(파생규칙) / MANUAL(검수). MANUAL wins']
  suppressed_from_tab bool           [not null, default: false, note: '결정B: 탭 롤업 제외 플래그(태그 보존·탭만 억제)']
  created_at        timestamp        [not null]
  updated_at        timestamp        [not null]

  indexes {
    (product_id, liquor_type_code) [pk]
    liquor_type_code
  }
}
```

#### `brewery_liquor_type` — 양조장↔주종 롤업(탭 소스) `[AUTO재생성]`

양조장 단위 union 롤업. **주종 탭 조회가 SELECT하는 캐시**. `suppressed_from_tab=true`·`liquor_status≠TAGGED`는 롤업 제외.

```dbml
Table brewery_liquor_type {
  brewery_id       varchar          [not null, note: 'FK brewery']
  liquor_type_code liquor_type_code [not null, note: 'FK liquor_type']
  product_cnt      int              [not null, note: '해당 주종 태깅 제품 수(억제행 제외). 정렬 보조']
  created_at       timestamp        [not null]
  updated_at       timestamp        [not null]

  indexes {
    (brewery_id, liquor_type_code) [pk]
    liquor_type_code
  }
}
```

### 3.4 manual_override 층

#### `manual_override` — 수기 보정 원장 `[MANUAL보존·불변원장]`

> **[갱신 2026-07-30]** 초안의 범용 `(target_type/field/value)` 스키마를 실제 3c-1 시드 스키마로 교체.
> 제품→brewery 수기 보정을 폴리모픽(NAME_MAP/ROW_PIN)으로 확정. 시드 = `src/main/resources/manual_override_seed.json`
> (assignment §결정4 확정 9행, 재판정·증감 없음). 참조키 = brewery_id(이름 아님).

```dbml
Table manual_override {
  id              bigint          [pk, note: 'surrogate. append-only']
  override_type   override_type   [not null, note: 'NAME_MAP=양조장명 norm 매칭 / ROW_PIN=제품명으로 행 고정(양조장 필드 null)']
  match_key       varchar         [not null, note: 'BREWERY_NORM=양조장명 norm / PRODUCT_NAME=제품명']
  match_key_kind  match_key_kind  [not null, note: 'BREWERY_NORM / PRODUCT_NAME']
  brewery_id      varchar         [not null, note: 'FK brewery. 참조키=brewery_id(이름 금지)']
  reason          override_reason [not null, note: 'ADDR_EXACT / ADDR_STRONG / MANUAL_DOMAIN']
  recheck_flag    bool            [not null, note: '재점검 필요(조옥화 25/45도 2행 true)']
  source_raw_name varchar         [not null, note: '원본 product raw명(추적용)']
  created_at      timestamp       [not null]

  indexes {
    (match_key_kind, match_key) [unique, note: '멱등 재적재 방어']
    brewery_id
  }
}
```

> **확정 9행 분해**: NAME_MAP 7(양촌감와이너리·양촌감→BRW-033, 민속주안동소주→BRW-003, 제이엘→BRW-037,
> 솔송주(명가원)→BRW-025, 갈기산농업회사→BRW-001, 태인→BRW-053), ROW_PIN 2(조옥화 안동소주 25/45도→BRW-003,
> recheck=true). 6개 BRW 전부 brewery에 실재(FK 유효). ★이번(3c-1)은 원장 "적재"까지 — 실제 조인 적용은 3c-2.

---

## 4. 관계도 요약 (FK 참조 — 텍스트)

```
brewery (PK brewery_id)  ──1:N──▶ brewery_raw (FK brewery_id, 스냅샷별)
brewery                  ──1:N──▶ product (FK brewery_id, nullable)
brewery                  ──1:N──▶ brewery_product (FK brewery_id)
brewery                  ──1:N──▶ brewery_liquor_type (FK brewery_id)
brewery                  ──1:N──▶ manual_override (FK brewery_id, nullable)

product (PK product_id)  ──1:N──▶ product_raw (FK product_id, 스냅샷별)
product                  ──1:N──▶ brewery_product (FK product_id)
product                  ──1:N──▶ product_liquor_type (FK product_id)
product                  ──1:N──▶ manual_override (FK product_id, nullable)

liquor_type (PK code)    ──1:N──▶ product_liquor_type (FK liquor_type_code)
liquor_type              ──1:N──▶ brewery_liquor_type (FK liquor_type_code)
liquor_type              ──1:N──▶ manual_override (FK liquor_type_code, nullable)

다대다 실체:
  product ⇄ liquor_type   via product_liquor_type (중간)
  brewery ⇄ liquor_type   via brewery_liquor_type (롤업·중간)
  brewery ⇄ product       via brewery_product (조인 링크·중간)
```

- **런타임 조인/API 호출 없음**: 모든 조회는 파생·롤업·조인 테이블(배치 물질화)에서 SELECT.
- **참조는 전부 brewery_id/product_id(내부키)**, 이름 문자열 참조 없음.

---

## 5. ERDCloud 옮기기 체크리스트

**그리는 순서(의존도 낮은 것 먼저)**:
1. **enum 8종 먼저 등록**(visit_state, join_status, liquor_status, region_code, liquor_type_code, tag_source, active_state, override_source) — 테이블이 참조하므로 선행.
2. **코어 dimension 3개**: `brewery` → `product` → `liquor_type`(독립, 시드 5행).
3. **raw 2개**: `brewery_raw`(FK→brewery) → `product_raw`(FK→product). 복합PK(+snapshot_date) 주의.
4. **조인 3개**: `brewery_product` → `product_liquor_type` → `brewery_liquor_type`. 전부 복합PK 중간테이블.
5. **override 1개**: `manual_override`(nullable FK 다수).

**주의점**:
- 복합PK 테이블 5개(brewery_raw·product_raw·brewery_product·product_liquor_type·brewery_liquor_type): ERDCloud에서 PK 컬럼 2개 체크 필수.
- enum 한글 값은 코드(ASCII)로 넣고 한글은 컬럼/enum Note에. 임포트 깨짐 방지.
- nullable FK(product.brewery_id, manual_override.*): 관계선 '0..N'로.
- `suppressed_from_tab`·`source`·`match_source`는 롤업 로직의 핵심 → Note에 "롤업 제외 조건" 명시.
- view_count는 정렬 기본키 아님 — Note 강조.
