# 카카오 로그인·약관 연동

## 로컬 실행

최초 실행 시 저장소 루트에 `.env` 파일을 만들고 다음 두 값을 입력한다.

```dotenv
KAKAO_REST_API_KEY=<카카오 앱 REST API 키>
KAKAO_CLIENT_SECRET=<카카오 앱 Client Secret>
```

`.env`에는 실제 비밀값이 들어가므로 Git에 커밋하지 않는다.

```bash
docker compose up -d
./gradlew bootRun
```

Spring Boot는 프로젝트 루트의 `.env`를 선택적으로 읽는다.

DB·Redirect URI·프론트 주소·세션 기간·쿠키 보안은 `application.yml`의 로컬 기본값을 사용한다. 프론트 포트가
다르거나 서버에 배포할 때만 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAKAO_REDIRECT_URI`,
`FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `AUTH_SESSION_DURATION`, `AUTH_COOKIE_SECURE`,
`AUTH_COOKIE_SAME_SITE` 환경변수로 기본값을 덮어쓴다. `CORS_ALLOWED_ORIGINS`는 쉼표로 여러 origin을
등록할 수 있으며 경로를 포함하지 않는다. 서로 다른 사이트의 프론트와 연동할 때는 HTTPS 환경에서
`AUTH_COOKIE_SECURE=true`, `AUTH_COOKIE_SAME_SITE=None`을 함께 사용한다.
운영·개발 서버에서는 `.env` 파일을 배포하지 않고 배포 플랫폼의 Secret/Environment Variables에 등록한다.
운영 환경에서는 HTTPS를 사용하고 `AUTH_COOKIE_SECURE=true`로 설정한다.

## 카카오 디벨로퍼스 설정

1. 카카오 로그인 사용 설정을 켠다.
2. REST API 키를 확인한다.
3. Redirect URI에 `KAKAO_REDIRECT_URI`와 완전히 동일한 값을 등록한다.
4. 동의항목에서 닉네임과 카카오계정(이메일)을 설정한다. 이메일은 사용자가 동의하지 않거나
   카카오계정에 값이 없을 수 있으므로 서비스에서는 nullable로 처리한다.
5. Client Secret을 활성화한 경우 환경변수에도 같은 값을 입력한다.

## 브라우저 로그인 흐름

1. 프론트가 `GET /api/v1/auth/kakao?returnTo=/현재경로`로 브라우저를 이동시킨다.
2. 백엔드가 CSRF 방어용 OAuth `state`를 발급하고 카카오 로그인 화면으로 이동시킨다.
3. 카카오가 `/api/v1/auth/kakao/callback`으로 인가 코드를 전달한다.
4. 백엔드가 카카오 토큰과 사용자 정보를 서버 간 통신으로 조회한다.
5. 백엔드가 무작위 세션 원문의 SHA-256 해시만 DB에 저장하고, 원문은 HttpOnly 쿠키로 전달한다.
6. 백엔드는 검증한 `returnTo`를 회원의 로그인 진행 상태에 보관한다.
7. 필수 약관 미동의 회원은 `/terms`, 온보딩 미완료 회원은 `/onboarding`, 완료 회원은 `returnTo`로 이동한다.
8. 신규 회원도 약관과 온보딩을 모두 완료하면 로그인 직전 경로로 복귀한다.

카카오 로그인 취소 또는 오류가 발생하면 프론트의 `/login`으로 돌아간다.

```text
/login?error=kakao_cancelled
/login?error=invalid_oauth_state
/login?error=kakao_auth_failed
```

## 프론트 API 호출

쿠키 인증을 위해 요청에 credentials를 포함한다.

```javascript
await fetch("http://localhost:8080/api/v1/auth/me", {
  credentials: "include"
});
```

POST·PATCH·DELETE 전에 `GET /api/v1/auth/csrf`를 호출하고 응답의 토큰을 `X-XSRF-TOKEN` 헤더로 보낸다.

프론트에는 다음 경로가 준비되어 있어야 한다.

- `/login`: `error` 쿼리에 맞는 안내 및 재시도 버튼 표시
- `/terms`: 필수·선택 약관 동의 화면
- `/onboarding`: 신규 회원 온보딩 화면
- 로그인 시작 시 돌아갈 화면을 `returnTo`로 전달

약관 저장 후 `POST /api/v1/auth/continue`를 호출해 다음 화면으로 이동한다. 온보딩 마지막 저장이
성공하면 `POST /api/v1/onboarding/complete`를 호출하고 응답의 `nextPath`로 이동한다. 로그인 직전 경로는
백엔드가 보관하므로 프론트가 임의의 외부 URL을 저장하거나 그대로 리다이렉트하지 않는다.

## API

- `GET /api/v1/auth/kakao`: 카카오 로그인 시작
- `GET /api/v1/auth/kakao/callback`: 카카오 서버용 콜백(Swagger 비노출)
- `GET /api/v1/auth/csrf`: CSRF 토큰 발급
- `GET /api/v1/auth/me`: 현재 회원 및 진행 상태
- `POST /api/v1/auth/continue`: 약관·온보딩 상태에 따른 다음 내부 경로 조회
- `POST /api/v1/auth/logout`: 현재 세션 폐기
- `GET /api/v1/terms`: 현재 약관과 회원 동의 상태 조회
- `POST /api/v1/terms/agreements`: 필수·선택 약관 선택 저장
- `PATCH /api/v1/terms/agreements/{code}`: 마이페이지에서 선택 약관 동의 변경·철회
- `GET /api/v1/onboarding/preferences`: 현재 회원의 저장된 취향 조회
- `PUT /api/v1/onboarding/preferences`: 선호 주종·지역·도수 취향 저장 또는 수정
- `POST /api/v1/onboarding/complete`: 필수 약관·취향 저장 확인 후 온보딩 완료 및 로그인 직전 경로 반환

약관 전문 경로는 프론트 배포 주소가 바뀌어도 사용할 수 있도록 `/terms/service-use` 같은 상대 경로로 반환한다.
프론트는 각 경로에 약관 전문 화면을 제공하고, 실제 법적 문구는 서비스 정책 확정본을 사용한다.

API 오류 응답은 조회·인증 등 모든 도메인에서 다음 공통 형식을 사용한다.

```json
{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "로그인이 필요합니다."
}
```

Swagger UI는 `http://localhost:8080/swagger-ui.html`에서 확인한다. 공통 설정은 `/api/v1/**` 아래의
서비스 API를 자동으로 문서화하며, 프론트가 직접 사용하지 않는 엔드포인트는 해당 컨트롤러에 `@Hidden`을 붙여 제외한다.
