# 기술 부채 레지스트리 (DEBT.md)

저장소 밖(개인 지침)에만 있던 부채 목록을 코드·DB로 검증해 저장소에 고정한다.
세션이 "부채 #23" 식으로 언급하면 이 파일에서 근거·상태·위험도를 찾을 수 있어야 한다.

- **최종 검증**: 2026-08-21 / HEAD `9da1879`
- **직전 전수 검증 결과**: 실재 20 · 해소 6 · 오등록 1 (합 27)
- **[트리거] 8건은 해당 코드에 `// DEBT-N:` 인라인 앵커**를 함께 심었다(문서=지도 / 앵커=지뢰 표지).
  단 #25는 "테스트 부재"라 단일 코드 라인이 없어 문서에만 둔다.

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

## 열린 부채 (실재 20건)

### [트리거] 8건 — 무엇을 하면 무엇이 터지는가

| # | 제목 | 근거 위치 | 트리거 → 결과 |
|---|---|---|---|
| 1 | exclusion 취소 경로 부재 | `LiquorInferenceService`:127 / `liquor_exclusion_seed.json` _meta | 제외를 되돌려야 할 때 → revoke 필드·경로 없음(seed 물리 삭제뿐) |
| 2 | 6단계 검증-적재 순서 | `ProcessOrchestrator`:170,172 / `LiquorInferenceService`:151 | MANUAL+EXCLUSION 모순 authoring → 커밋 후 예외, 재적재(truncate) |
| 4 | 테스트 매직넘버 | `RawLoadConsistencyTest`:83 외 3파일 | 양조장/스냅샷 추가 → 59·1215·366 하드코딩 다수 동시 수정 |
| 17 | 9단계 라이브 재스캔 | `TourNearbyCollectService`:92,99 | process 재실행 → 유령 봉인 파손 + 외부 API 재스캔(~4분) |
| 23 | 단일필드 게이트(12·14단계) | `TourDetailEnrichService`:102 / `NonglimSeedLoadService`:74 | 새 상세/시드 컬럼 추가 → 기존 행 영구 NULL(전용 백필 필요) |
| 24 | 외부 API 타임아웃·재시도 전무 | `CollectHttpConfig`:22 | 외부 API 호출(collect/process) → 무한 대기 또는 1회 실패로 단계 사망 |
| 25 | 구조 봉인 5/11 시드 | `*SeedFileStructureTest` 4파일(5시드) | 미봉인 6종 구조 오류 → Postgres 미기동 CI를 무검증 통과 |
| a | prod 프로파일 활성화 미고정 | `application-prod.yml` / main resources grep 0 | env(`SPRING_PROFILES_ACTIVE`) 누락 배포 → prod 하드닝 조용히 미적용 |

> #25 미봉인 6종: `experience_match` · `liquor_exclusion` · `liquor_keyword` · `liquor_manual` · `manual_override` · `tour_match`.

### [심사까지X] 12건 — 문서에만 둔다(코드 무수정)

| # | 제목 | 근거 위치 |
|---|---|---|
| 3 | excluded 카운트 의미 불일치(로그=실제 필터 / 리포트=시드 전체) | `LiquorInferenceService`:128 / `LiquorAuditReportService`:229 |
| 5–7 | reason 문구 약함(주관) | `liquor_manual_seed` · `liquor_exclusion_seed` |
| 8 | 향미이중 9행 미검수(전부 AUTO·recheck·suppress) | `product_liquor_type WHERE suppressed_from_tab` |
| 9 | coord_source CHECK 매 기동 재적용(멱등·무해) | `schema.sql`:94-95 |
| 10 | BRW-002 본번지 좌표 폴백(근사) | `coord_source=KAKAO_ADDRESS_NORMALIZED` |
| 11 | tour_content 유령 부분해소(화면 밖 미확인 잔존) | 10,279→10,268 교차확인 |
| 13 | product 코어 스텁(query층은 #61로 완성) | `product/package-info.java` |
| 14 | FeatureRollup 전건 로드(366·57행 무해) | `FeatureRollupService`:72,93 |
| 16 | 농림부 heldBack 2건(사람 확인 대기) | `nonglim_seed.json` _meta.heldBack |
| 20 | manual_override recheck 7건(검수 대기) | `manual_override WHERE recheck_flag` |
| 26 | _meta 드리프트(서사 vs entries 불일치) | `liquor_manual_seed`(74 vs 221) · `manual_override_seed`(9 vs 14) |
| b | 시드 값 회귀 방어 부분(봉인은 구조만, count 불변 값오류 미방어) | 골든 테스트는 출력 count만 핀 |

## 해소·오등록 (7건 — append-only, 삭제 금지)

| # | 제목 | 상태 | 근거 |
|---|---|---|---|
| 12 | overview 전량 NULL | 해소 | `TourDetailEnrichService`:88-93 backfill · `BreweryQueryService`:120 서빙 · overview 19행(=매칭 19곳). schema.sql:296- 주석 stale였어 이번에 정정 |
| 15 | brewery.image_url 죽은 컬럼 | 해소 | `schema.sql`:124 `DROP COLUMN` · DB 컬럼 부재. 이슈 `#56`을 PR `#57`이 해소(상단 경고 참조) |
| 18 | 테스트 datasource 미격리 | 해소 | 전 통합테스트 `spring.datasource.url=…/jeontongjuro_test` |
| 19 | overview_fetched_at 전량 NULL | 오등록 | `TourDetailEnrichService`:88 `isOverviewFetched()` 멱등 게이트로 정상 사용 · 19행. schema.sql 주석 stale였어 정정 |
| 21 | 필터 트림 비일관 | 해소 | `BrewerySearchCondition.of`:54-58 `strip()` |
| 22 | 일반 500 핸들러 부재 | 해소 | `GlobalExceptionHandler`:87 `@ExceptionHandler(Exception.class)` |
| 27 | prod 프로파일 부재 | 해소 | `application-prod.yml` 존재(단 활성화 미고정은 부채 #a로 분리) |

## 미결 (사람 판단 필요 — 저장소만으로 확정 불가)

- **#11** "화면범위 443 생존 / 미확인 9,499" 세부 수치 — 감사 문서(이슈 코멘트·PR 본문, `docs/audit/`는 gitignore) 대조 필요.
- **#3** 런타임 "excluded 5"의 정확값 — 파이프라인 실행 로그 필요(현재 실행 금지·봉인 상태).
- **#16** heldBack 2건 — 제이엘=오미나라(BRW-037) / 명가원=솔송주(BRW-025) 동일 법인 여부는 외부 확인.
- **#15** 근거 번호 — `(#56)`=이슈 / `(#57)`=PR로 확정(git log). schema.sql:119 "(#56)"은 이슈 표기라 정정 불필요.
