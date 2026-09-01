# 기술 부채 레지스트리 (DEBT.md)

저장소 밖(개인 지침)에만 있던 부채 목록을 코드·DB로 검증해 저장소에 고정한다.
세션이 "부채 #23" 식으로 언급하면 이 파일에서 근거·상태·위험도를 찾을 수 있어야 한다.

- **최종 검증**: 2026-09-01 / HEAD `f6af511`
- **직전 전수 검증 결과**: 실재 20 · 해소 6 · 오등록 1 (합 27) — 이번 검증도 번호부채 상태는 동일, 앵커 라인·근거만 갱신
- ★위 집계는 표 행 수 기준이다. `5–7` 1행이 부채 3개를 묶은 표기라 부채 번호 기준 개수와는 다르다(다음 세션이 다시 셀 것에 대비해 명기)
- **2026-08-31 추가**: 문서·Swagger 대조 세션에서 #f 신규 등록(mainImage OpenAPI 스키마 자기모순, 문서 전용·앵커 없음).
  이 세션은 전수 재검증이 아니라 #f 1건 추가만 수행했다. 위 "실재 20" 등 기존 합계 산식을 표의 행 수와 대조했더니
  이미 어긋나 있었다(트리거 9행 + 심사까지X 14행 = 23행인데 "실재 20"으로 적혀 있음 — `5–7` 1행이 부채 3개를 묶은 표기라
  행 수≠부채 개수인 데서 온 차이로 추정되나 확정하지 못했다). 전면 재산정은 이번 세션 범위 밖이라 건드리지 않고,
  #f 추가분만 반영해 상대값으로 갱신한다: 실재 20→21 · 해소 6(불변) · 오등록 1(불변) (합 27→28, 단 기저값 자체가 재검증 필요)
- **2026-09-01 추가**: DEBT.md 앵커 정정 세션(문서·주석 전용, 프로덕션 라인 변경 0). #f 앵커 정정(`BreweryDetailResponse.java`:57→:59, 실제 파일 재확인), #25 테스트 수 정정(27개→29개, `@SpringBootTest` 실측 재검증), #18 근거 문장 정정(사실 주장이 실제와 달라 "전 통합테스트"→"29 중 27(예외 2건은 환경변수 게이트가 걸린 타인 라인 감사 테스트)"로 수정, 해소 상태는 유지), #c에 신규 호출부 근거(`RecommendedCourseListService`:26, `/recommendations/courses` 신설로 인한 추가 호출 경로) 추가. 신규 부채 #g 등록: 양조장 상세 조회가 `listProducts(breweryId, 0, 100)` 고정 인자로 전체 제품 파이프라인을 매번 재실행(`BreweryQueryService.java`:215) — 현재 양조장당 최대 19건(BRW-020)·평균 5.92건·전체 349건/59곳으로 SQL 직접 재산출해 무해 확인. #12(overview 서빙)는 이번 프롬프트가 정정 대상으로 지목했으나 실제로는 해소·오등록 섹션 항목(88행)이라 좌표를 이력으로 보존하고 손대지 않았다. [트리거] 9건→10건(#g 추가분): 실재 21→22 · 해소 6(불변) · 오등록 1(불변) (합 28→29, 단 기저값 자체가 재검증 필요)
- **[트리거] 10건 중 7건은 해당 코드에 `// DEBT-N:` 인라인 앵커**를 함께 심었다(문서=지도 / 앵커=지뢰 표지).
  단 #25·c·g는 단일 코드 라인으로 특정할 성격이 아니라(#25=테스트 부재, c=상수 자체가 문제가 아니라 무경계 전제가 문제, g=이번 세션이 문서 전용이라 인라인 주석 미부여) 문서에만 둔다.

## 🔴 GitHub 이슈 번호 ≠ 부채 번호 ≠ PR 번호

세 가지는 완전히 별개다. 이슈 #56이 "부채 #15 해소"를 뜻하는 식이며, 그 이슈를 PR #57이 닫을 수 있다.
실증: 커밋 `3961c83 chore(#56): brewery.image_url 죽은 컬럼 제거 (#57)` — `#56`은 이슈, `#57`은 PR.
**이슈/PR 번호에서 부채 번호를 역추적하지 마라.** 이 파일의 `#N`은 부채 번호이고, 근거의 `(#NN)`은 이슈/PR 번호다.

## 갱신 규칙

- 부채를 해소해도 **행을 지우지 말고** 상태만 `해소`/`오등록`으로 바꾼다(append-only).
  다음 세션이 "왜 없지"를 재조사하는 낭비를 막는다.
- 근거는 반드시 **파일:라인 / SQL / 테스트 클래스명**. "지침에 그렇게 적혀 있다"는 근거가 아니다.
- 부채를 건드린 PR이 같은 PR에서 이 파일을 갱신한다.
- 얇게 유지한다. 서술이 길어지면 아무도 안 읽고 stale된다.

## 범례

- 상태: `실재`=코드/DB에 그대로 있음 · `해소`=해결됨 · `오등록`=애초에 부채 아님
- 위험도: `[지금]` 현재 동작에 영향 · `[트리거]` 특정 행동을 하면 터짐 · `[심사까지X]` 10월 심사까지 무영향

## 열린 부채 (실재 22건)

### [트리거] 10건 — 무엇을 하면 무엇이 터지는가

| # | 제목 | 근거 위치 | 트리거 → 결과 |
|---|---|---|---|
| 1 | exclusion 취소 경로 부재 | `LiquorInferenceService`:127 / `liquor_exclusion_seed.json` _meta | 제외를 되돌려야 할 때 → revoke 필드·경로 없음(seed 물리 삭제뿐) |
| 2 | 6단계 검증-적재 순서 | `ProcessOrchestrator`:169-170,172 / `LiquorInferenceService`:153 | MANUAL+EXCLUSION 모순 authoring → 커밋 후 예외, 재적재(truncate) |
| 4 | 테스트 매직넘버 | `RawLoadConsistencyTest`:80-81 외 3파일 | 양조장/스냅샷 추가 → 59·1215·366 하드코딩 다수 동시 수정 |
| 17 | 9단계 라이브 재스캔 | `TourNearbyCollectService`:92-94,102 | process 재실행 → 유령 봉인 파손 + 외부 API 재스캔(~4분) |
| 23 | 단일필드 게이트(12·14단계) | `TourDetailEnrichService`:102 / `NonglimSeedLoadService`:74 | 새 상세/시드 컬럼 추가 → 기존 행 영구 NULL(전용 백필 필요) |
| 24 | 외부 API 타임아웃·재시도 전무 | `CollectHttpConfig`:22 | 외부 API 호출(collect/process) → 무한 대기 또는 1회 실패로 단계 사망 |
| 25 | 구조 봉인 12/13 시드 — 잔여 1종은 Postgres 게이트 테스트에서만 검증 | `*SeedFileStructureTest`류 10파일(11시드) + `LiquorKeywordDictionaryTest`(1시드, DB-free 단위) | `recommended_brewery_seed` 구조 오류 → `FixedBrewerySeed`는 fail-fast하지만 유일 소비 테스트 `RecommendedBreweryApiTest`가 `@EnabledIf(LocalPostgres)`라 Postgres 미기동 CI를 무검증 통과 |
| a | prod 프로파일 활성화 미고정 | `application-prod.yml` / main resources grep 0 | env(`SPRING_PROFILES_ACTIVE`) 누락 배포 → prod 하드닝 조용히 미적용 |
| c | `RecommendedBreweryService` 무필터 조회가 100곳 상한을 무경계 전제 | `RecommendedBreweryService`:56(`POPULATION_FETCH_SIZE=MAX_SIZE`),81(호출부), `RecommendedCourseListService`:26(신규 `/recommendations/courses` 경유 호출부) | 양조장이 100곳 초과 → 상위 100곳만 정렬되고 `slice`의 `totalElements`도 100으로 거짓 보고. 현재 59곳이라 무해, 경계 방어(assert/log) 없음 |
| g | 양조장 상세 조회가 `listProducts(breweryId, 0, 100)` 고정 인자로 전체 제품 파이프라인을 매번 재실행(무경계 전제, #c와 동일 패턴) | `BreweryQueryService.java`:215 | 양조장당 노출 제품이 100건 초과 → 뒤 제품 누락(현재 최대 19건 BRW-020, 평균 5.92건, 전체 349건/59곳이라 무해). #c와 마찬가지로 경계 방어(assert/log) 없음 |

> #25 잔여 미봉인 1종: `recommended_brewery_seed`. `liquor_keyword`는 2026-08-28 재검증에서 `LiquorKeywordDictionaryTest`(`new LiquorKeywordDictionary(new ObjectMapper())`, Spring 컨텍스트 불필요)가 DB 없이 사전 파싱 자체를 검증한다는 사실을 확인해 실질 봉인으로 재분류했다. `recommended_brewery_seed`도 `FixedBrewerySeed` 생성자가 동일하게 fail-fast하지만, 저장소의 `@SpringBootTest` 29개 전부가 `@EnabledIf(LocalPostgres#isUp)`로 게이트돼 있어 그 생성자가 Postgres 없이는 아예 실행되지 않는다 — 그래서 이 한 종만 [트리거]에 남긴다.

### [심사까지X] 15건 — 문서에만 둔다(코드 무수정)

| # | 제목 | 근거 위치 |
|---|---|---|
| 3 | excluded 카운트 의미 불일치(로그=실제 필터 / 리포트=시드 전체) | `LiquorInferenceService`:130 / `LiquorAuditReportService`:229 |
| 5–7 | reason 문구 약함(주관) | `liquor_manual_seed` · `liquor_exclusion_seed` |
| 8 | 향미이중 9행 미검수(전부 AUTO·recheck·suppress) | `product_liquor_type WHERE suppressed_from_tab` |
| 9 | coord_source CHECK 매 기동 재적용(멱등·무해) | `schema.sql`:94-95 |
| 10 | BRW-002·BRW-039 본번지 좌표 폴백(근사, 2건) | `coord_source=KAKAO_ADDRESS_NORMALIZED`(BRW-002 고도리 와이너리 · BRW-039 우리술) |
| 11 | tour_content 유령 부분해소(화면 밖 미확인 잔존) | 10,279→10,268 교차확인 |
| 13 | product 코어 스텁(query층은 #61로 완성) | `product/package-info.java` |
| 14 | FeatureRollup 전건 로드(366·57행 무해) | `FeatureRollupService`:72,93 |
| 16 | 농림부 heldBack 2건(사람 확인 대기) | `nonglim_seed.json` _meta.heldBack |
| 20 | manual_override recheck 7건(검수 대기) | `manual_override WHERE recheck_flag` |
| 26 | _meta 드리프트(서사 vs entries 불일치) | `liquor_manual_seed`(74 vs 221) · `manual_override_seed`(9 vs 14) |
| b | 시드 값 회귀 방어 부분(봉인은 구조만, count 불변 값오류 미방어) | 골든 테스트는 출력 count만 핀 |
| d | 검색 정규화 2벌(목록 API keyword 필터는 특수문자 미제거) | `SearchKeyword.normalizeTarget`:53-54(제거+NFC+lower) vs `BrewerySearchCondition.normalizeKeyword`:152(NFC만) + `BreweryQuerySpecifications.keywordContains`:121(lower만, 제거 없음) |
| e | `OpenApiDocumentationTest`가 신규 7개 엔드포인트 중 1개(`/api/v1/breweries`)만 핀 | `OpenApiDocumentationTest.java`:42 — 나머지 6개(상세·제품·metadata·search·suggestions·recommendations)는 그룹 소속을 회귀 방어하는 테스트가 없다 |
| f | `mainImage` 필드 OpenAPI 3.1 스키마 자기모순(`type:"null"` + `$ref` 형제, 실제 응답은 정상) | springdoc-openapi 3.0.3 출력(`/v3/api-docs`) · `BreweryListItemResponse.java`:60 · `BreweryDetailResponse.java`:59 (둘 다 object 타입 `$ref` 필드에 `@Schema(nullable=true)`) |

> #f `mainImage`: OpenAPI 3.1(JSON Schema 2020-12)에서 `$ref`는 형제 키워드와 AND로 합성된다. object 타입 `$ref` 필드에
> `@Schema(nullable=true)`를 붙이면 springdoc 3.0.3이 3.1 출력 모드에서 `anyOf:[{$ref},{type:"null"}]` 대신
> `{"type":"null","$ref":"#/components/schemas/MainImageResponse", ...}`를 그대로 얹어, null도 객체도 통과할 수 없는
> 모순 스키마가 된다(원시 타입 nullable 필드는 `type:["string","null"]`로 정상 렌더링되므로 이 패턴에만 국한).
> **실제 JSON 응답 자체는 정상**이다 — 2026-08-30 검증 세션에서 양조장 59건 전수 조회 결과 null 43건·object 16건 모두
> shape이 정상임을 확인했다. 영향 조건: 프론트가 ajv strict 등 **런타임 JSON Schema 검증**을 이 스키마에 대해 돌릴 때만
> 검증 실패로 터진다(이 프로젝트 프론트가 그런 검증을 쓰는지는 미확인 — 안 쓰면 무해). 고치지 않는 근거: (1) 실응답에
> 결함이 없어 원인이 springdoc 렌더링 한정이다 (2) 고치려면 springdoc 버전업 또는 `OpenApiCustomizer` 신설이 필요한데
> `global/OpenApiConfig`는 수빈 라인 파일이라 이번 세션(문서 문자열 2건 한정) 범위 밖이다 (3) 심사 5주 전에 문서 렌더링
> 목적으로 의존성·설정을 건드릴 유인이 없다.

## 해소·오등록 (7건 — append-only, 삭제 금지)

| # | 제목 | 상태 | 근거 |
|---|---|---|---|
| 12 | overview 전량 NULL | 해소 | `TourDetailEnrichService`:88-93 backfill · `BreweryQueryService`:120 서빙 · overview 19행(=매칭 19곳). schema.sql:296- 주석 stale였어 이번에 정정 |
| 15 | brewery.image_url 죽은 컬럼 | 해소 | `schema.sql`:124 `DROP COLUMN` · DB 컬럼 부재. 이슈 `#56`을 PR `#57`이 해소(상단 경고 참조) |
| 18 | 테스트 datasource 미격리 | 해소 | 29 중 27(예외 2건은 환경변수 게이트가 걸린 타인 라인 감사 테스트) `spring.datasource.url=…/jeontongjuro_test` |
| 19 | overview_fetched_at 전량 NULL | 오등록 | `TourDetailEnrichService`:88 `isOverviewFetched()` 멱등 게이트로 정상 사용 · 19행. schema.sql 주석 stale였어 정정 |
| 21 | 필터 트림 비일관 | 해소 | `BrewerySearchCondition.of`:54-58 `strip()` |
| 22 | 일반 500 핸들러 부재 | 해소 | `GlobalExceptionHandler`:94 `@ExceptionHandler(Exception.class)` |
| 27 | prod 프로파일 부재 | 해소 | `application-prod.yml` 존재(단 활성화 미고정은 부채 #a로 분리) |

## 미결 (사람 판단 필요 — 저장소만으로 확정 불가)

- **#11** "화면범위 443 생존 / 미확인 9,499" 세부 수치 — 감사 문서(이슈 코멘트·PR 본문, `docs/audit/`는 gitignore) 대조 필요.
- **#3** 런타임 "excluded 5"의 정확값 — 파이프라인 실행 로그 필요(현재 실행 금지·봉인 상태).
- **#16** heldBack 2건 — 제이엘=오미나라(BRW-037) / 명가원=솔송주(BRW-025) 동일 법인 여부는 외부 확인.
- **#15** 근거 번호 — `(#56)`=이슈 / `(#57)`=PR로 확정(git log). schema.sql:119 "(#56)"은 이슈 표기라 정정 불필요.
