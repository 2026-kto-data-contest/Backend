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

## 2. 채번표 — brewery_id 매핑 59건 (`brewery_raw.json` 상호명 순서, append-only·이후 불변)

- 컬럼: `brewery_id | 상호명(원본) | 정규화명 | 시도 | 광역권`
- **정규화명**은 정규화 규칙(공백제거+법인격제거+접미어제거) 적용값. 조인 매칭키.
- **광역권 코드**: CAPITAL=수도권, GANGWON=강원, CHUNGCHEONG=충청, GYEONGSANG=경상, JEOLLA=전라, JEJU=제주.

| brewery_id | 상호명(원본) | 정규화명 | 시도 | 광역권 |
|---|---|---|---|---|
| BRW-001 | 산머루농원 | 산머루 | 경기 | CAPITAL |
| BRW-002 | 배상면주가 | 배상면 | 경기 | CAPITAL |
| BRW-003 | 우리술 | 우리술 | 경기 | CAPITAL |
| BRW-004 | 그린영농조합 | 그린 | 경기 | CAPITAL |
| BRW-005 | 배혜정도가 | 배혜정 | 경기 | CAPITAL |
| BRW-006 | 밝은세상영농조합 | 밝은세상 | 경기 | CAPITAL |
| BRW-007 | 좋은술 | 좋은술 | 경기 | CAPITAL |
| BRW-008 | 술샘 | 술샘 | 경기 | CAPITAL |
| BRW-009 | 신평양조장 | 신평 | 충남 | CHUNGCHEONG |
| BRW-010 | 예산사과와인 | 예산사과와인 | 충남 | CHUNGCHEONG |
| BRW-011 | 양촌양조 | 양촌 | 충남 | CHUNGCHEONG |
| BRW-012 | 한산소곡주 | 한산소곡주 | 충남 | CHUNGCHEONG |
| BRW-013 | 중원당 | 중원당 | 충북 | CHUNGCHEONG |
| BRW-014 | 대강양조장 | 대강 | 충북 | CHUNGCHEONG |
| BRW-015 | 조은술 세종 | 조은술세종 | 충북 | CHUNGCHEONG |
| BRW-016 | 이원양조장 | 이원 | 충북 | CHUNGCHEONG |
| BRW-017 | 여포와인농장 | 여포와인 | 충북 | CHUNGCHEONG |
| BRW-018 | 시나브로 와이너리 | 시나브로와이너리 | 충북 | CHUNGCHEONG |
| BRW-019 | 도란원 | 도란원 | 충북 | CHUNGCHEONG |
| BRW-020 | 태인합동주조장 | 태인합동주조장 | 전북 | JEOLLA |
| BRW-021 | 지리산 운봉주조 | 지리산운봉 | 전북 | JEOLLA |
| BRW-022 | 청산녹수 | 청산녹수 | 전남 | JEOLLA |
| BRW-023 | 추성고을 | 추성고을 | 전남 | JEOLLA |
| BRW-024 | 대대로영농조합법인 | 대대로 | 전남 | JEOLLA |
| BRW-025 | 해창주조장 | 해창주조장 | 전남 | JEOLLA |
| BRW-026 | 예술주조 | 예술 | 강원 | GANGWON |
| BRW-027 | 국순당 | 국순당 | 강원 | GANGWON |
| BRW-028 | 울진술도가 | 울진술 | 경북 | GYEONGSANG |
| BRW-029 | 오미나라 | 오미나라 | 경북 | GYEONGSANG |
| BRW-030 | 문경주조 | 문경 | 경북 | GYEONGSANG |
| BRW-031 | 명인안동소주 | 명인안동소주 | 경북 | GYEONGSANG |
| BRW-032 | 한국애플리즈 | 한국애플리즈 | 경북 | GYEONGSANG |
| BRW-033 | 은척양조장 | 은척 | 경북 | GYEONGSANG |
| BRW-034 | 한국와인 | 한국와인 | 경북 | GYEONGSANG |
| BRW-035 | 고도리 와이너리 | 고도리와이너리 | 경북 | GYEONGSANG |
| BRW-036 | 수도산와이너리 | 수도산와이너리 | 경북 | GYEONGSANG |
| BRW-037 | 복순도가 | 복순 | 울산 | GYEONGSANG |
| BRW-038 | 금정산성 토산주 | 금정산성토산주 | 부산 | GYEONGSANG |
| BRW-039 | 제주샘주 | 제주샘주 | 제주 | JEJU |
| BRW-040 | 제주고소리술익는집 | 제주고소리술익는집 | 제주 | JEJU |
| BRW-041 | 모월 | 모월 | 강원 | GANGWON |
| BRW-042 | 술아원 | 술아원 | 경기 | CAPITAL |
| BRW-043 | 장희도가 | 장희 | 충북 | CHUNGCHEONG |
| BRW-044 | 하미앙 와인밸리 | 하미앙와인밸리 | 경남 | GYEONGSANG |
| BRW-045 | 풍정사계 | 풍정사계 | 충북 | CHUNGCHEONG |
| BRW-046 | 솔송주 | 솔송주 | 경남 | GYEONGSANG |
| BRW-047 | 금풍양조 | 금풍 | 인천 | CAPITAL |
| BRW-048 | 오산양조 | 오산 | 경기 | CAPITAL |
| BRW-049 | 산막와이너리 | 산막와이너리 | 충북 | CHUNGCHEONG |
| BRW-050 | 맑은내일 | 맑은내일 | 경남 | GYEONGSANG |
| BRW-051 | 인천탁주 | 인천탁주 | 인천 | CAPITAL |
| BRW-052 | 술빚는 전가네 | 술빚는전가네 | 경기 | CAPITAL |
| BRW-053 | 두레양조 | 두레 | 충남 | CHUNGCHEONG |
| BRW-054 | 양촌와이너리 | 양촌와이너리 | 충남 | CHUNGCHEONG |
| BRW-055 | 덕유양조 | 덕유 | 전북 | JEOLLA |
| BRW-056 | 갈기산 | 갈기산 | 충북 | CHUNGCHEONG |
| BRW-057 | 다도참주가 | 다도참 | 전남 | JEOLLA |
| BRW-058 | 밀과노닐다 | 밀과노닐다 | 경북 | GYEONGSANG |
| BRW-059 | 국가유산·명인 조옥화 안동소주 | 국가유산·명인조옥화안동소주 | 경북 | GYEONGSANG |

- 시도 분포: 경기11·충북11·경북11·충남6·전남5·경남3·강원3·전북3·인천2·제주2·부산1·울산1 (12개 시도).
- 광역권 분포: 충청17·경상16·수도권13·전라8·강원3·제주2.

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

#### `brewery` — 양조장 코어 dimension(채번표 실체) `[혼합: id 불변 / 파생필드 AUTO / 3-state·image MANUAL보존]`

§2 채번표가 이 테이블의 시드. 서비스 조회의 기준 dimension.

```dbml
Table brewery {
  brewery_id       varchar       [pk, note: 'BRW-xxx 자연키 PK(결정A). 불변·append-only']
  normalized_name  varchar       [not null, note: '정규화명. 조인 매칭키']
  display_name     varchar       [not null, note: '서비스 표시명 = 최신 raw 상호명 복사. 표시 안정용']
  sido             varchar       [not null, note: '주소 앞토큰 파싱(12종 관측)']
  region           region_code   [not null, note: '광역권 매핑. 지역 탭 기준']
  join_status      join_status   [not null, note: '제품 조인 성립 여부 2-state']
  liquor_status    liquor_status [not null, note: '파생값: UNJOINED→NA, 조인제품 주종태그≥1→TAGGED, else UNTAGGED']
  reservation_visit visit_state  [not null, note: '3-state. raw null→UNKNOWN. null 52% 근거. boolean 금지']
  always_visit     visit_state   [not null, note: '3-state. raw null→UNKNOWN. null 15% 근거']
  view_count_snapshot int        [null, note: '조회수 정렬용 스냅샷. 기본정렬 아님. 재적재 시 변동']
  image_url        varchar      [null, note: 'nullable 격리 — 이미지 소스 확정과 무관하게 개발 진행 가능']
  created_at       timestamp     [not null]
  updated_at       timestamp     [not null]

  indexes {
    normalized_name
    (region)
    (join_status, liquor_status)
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

**참조키 brewery_id/product_id(이름 아님). MANUAL always wins over AUTO. 재적재 시 id 매칭으로 재적용.**

```dbml
Table manual_override {
  override_id   bigint          [pk, note: 'surrogate. 원장 append-only']
  target_type   varchar         [not null, note: 'BREWERY/PRODUCT/BREWERY_PRODUCT/PRODUCT_LIQUOR 중. 어느 테이블 대상']
  brewery_id    varchar         [null, note: 'FK brewery. brewery계열 override']
  product_id    varchar         [null, note: 'FK product. product계열 override']
  liquor_type_code liquor_type_code [null, note: '주종 태그 override 시']
  field         varchar         [not null, note: '보정 대상 필드명(예: liquor_status, reservation_visit, join_mapping)']
  value         varchar         [not null, note: '보정값(문자열 표준화)']
  source        override_source [not null, default: 'MANUAL']
  reason        varchar         [null, note: '보정 근거(감사)']
  overridden_by varchar         [null, note: '검수 담당']
  overridden_at timestamp       [not null]
  created_at    timestamp       [not null]
  updated_at    timestamp       [not null]

  indexes {
    brewery_id
    (target_type, field)
  }
}
```

> **병합 규칙**: 재적재 시 AUTO 파이프라인이 `brewery/brewery_product/product_liquor_type/brewery_liquor_type`를 재생성 → 그 위에 `manual_override`를 **brewery_id(또는 product_id) 매칭으로 재적용**(MANUAL wins). override는 삭제되지 않는 원장.

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
