# 추천 코스 Supabase 전수 감사

## 실행 방법

운영 데이터에 쓰기 작업을 하지 않도록 `spring.sql.init.mode=never`로 실행되는 감사 테스트다.

```bash
COURSE_AUDIT_ENABLED=true ./gradlew test \
  --tests 'com.jeontongjuro.backend.course.RecommendedCourseSupabaseAuditTest'
```

## 2026-08-31 검증 결과

- Supabase 양조장: 59곳
- 코스 생성 성공: 59곳
- 9개 장소 완성 코스: 59곳
- 코스당 최대 9곳: 통과
- 양조장 첫 장소 고정 및 연속된 `order`: 통과
- 카테고리별 최대 2곳: 통과
- 동일 `contentId` 중복 없음: 통과
- 모든 주변 장소 좌표·거리·카카오맵 링크 제공: 통과
- 사용자 응답에 관광공사 원본 분류 코드 미노출: 통과

감사 테스트는 현재 Supabase 데이터 전체를 읽어 실제 `RecommendedCourseService`로 코스를 재생성한다.
일반 CI에서는 외부 데이터베이스 의존성을 만들지 않기 위해 명시적 환경변수가 없으면 스킵한다.
