# Backend

## Git Convention

원활한 협업과 작업 이력 관리를 위해 모든 작업은 **GitHub Issue를 기준으로 진행**한다.

### 작업 진행 순서

```text
Issue 생성
→ 작업 브랜치 생성
→ 코드 작성 및 커밋
→ Pull Request 생성
→ 코드 리뷰
→ main 브랜치 병합
```

---

## 1. Issue 규칙

모든 작업은 **GitHub Issue를 등록한 후 시작**한다.

### Issue 제목 형식

```text
[유형]: 작업 설명
```

### Issue 제목 예시

```text
[FEAT]: 회원가입 API 구현
[FIX]: 액세스 토큰 만료 오류 수정
[REFACTOR]: 사용자 조회 로직 개선
[DOCS]: API 문서 수정
[TEST]: 로그인 서비스 테스트 작성
[CHORE]: 프로젝트 환경 설정
```

### Issue 유형

| 유형 | 설명 |
|---|---|
| `FEAT` | 새로운 기능 추가 |
| `FIX` | 버그 수정 |
| `REFACTOR` | 기능 변경 없는 코드 개선 |
| `DOCS` | 문서 작성 및 수정 |
| `TEST` | 테스트 코드 작성 및 수정 |
| `CHORE` | 설정, 패키지, 빌드 관련 작업 |

### Issue 작성 기준

- 작업을 시작하기 전에 Issue를 등록한다.
- Issue 담당자에게 `Assignee`를 지정한다.
- 작업 성격에 맞는 `Label`을 설정한다.
- 하나의 Issue에는 하나의 작업만 포함한다.
- 세부 작업은 체크리스트로 작성한다.

### Issue 본문 예시

```md
## 작업 내용

- 회원가입 API를 구현한다.
- 이메일 중복 검증 기능을 추가한다.

## 세부 작업

- [ ] 회원가입 Request DTO 작성
- [ ] 회원가입 Service 구현
- [ ] 이메일 중복 예외 처리
- [ ] 테스트 코드 작성
```

---

## 2. Branch 규칙

`main` 브랜치에서는 직접 작업하지 않는다.

Issue를 생성한 후 해당 Issue 번호를 포함한 작업 브랜치를 생성한다.

### 브랜치 이름 형식

```text
유형/이슈번호/작업설명
```

### 브랜치 이름 예시

```text
feat/17/google-oauth
fix/21/token-expiration
refactor/24/user-service
docs/30/api-document
test/35/login-service
chore/40/project-setting
```

### 브랜치 작성 기준

- 브랜치 유형은 영문 소문자로 작성한다.
- 작업 설명은 영문 소문자와 하이픈(`-`)을 사용한다.
- 브랜치명에는 관련 Issue 번호를 포함한다.
- 하나의 브랜치에서는 하나의 Issue 작업만 진행한다.
- 작업이 완료되어 `main` 브랜치에 병합된 브랜치는 삭제한다.

### 브랜치 생성 방법

```bash
git checkout main
git pull origin main
git checkout -b feat/17/google-oauth
```

---

## 3. Commit 규칙

커밋 메시지는 아래 형식으로 작성한다.

### 커밋 메시지 형식

```text
유형: 작업 내용 (#이슈번호)
```

### 커밋 메시지 예시

```text
feat: Google OAuth 로그인 구현 (#17)
fix: 액세스 토큰 만료 예외 처리 (#21)
refactor: 사용자 조회 로직 분리 (#24)
docs: 로그인 API 문서 추가 (#30)
test: 로그인 서비스 테스트 추가 (#35)
chore: JWT 의존성 추가 (#40)
```

### Commit 유형

| 유형 | 설명 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 개선 |
| `docs` | 문서 작성 및 수정 |
| `test` | 테스트 코드 작성 및 수정 |
| `chore` | 설정, 패키지, 빌드 관련 작업 |

### Commit 작성 기준

- 커밋 하나에는 하나의 변경 목적만 포함한다.
- 제목에는 무엇을 변경했는지 간단하게 작성한다.
- 관련 Issue 번호를 커밋 메시지에 포함한다.
- 작업 내용이 많을 경우 커밋 본문에 상세 내용을 작성한다.
- 서로 관련 없는 작업을 하나의 커밋에 포함하지 않는다.

### 커밋 본문 예시

```text
feat: Google OAuth 로그인 구현 (#17)

- Google 인증 코드를 이용한 사용자 정보 조회
- 기존 회원과 신규 회원 로그인 처리
- 로그인 성공 시 JWT 토큰 발급
```

---

## 4. Pull Request 규칙

작업이 완료되면 작업 브랜치에서 `main` 브랜치로 Pull Request를 생성한다.

### PR 제목 형식

```text
[유형]: 작업 설명
```

### PR 제목 예시

```text
[FEAT]: Google OAuth 로그인 구현
[FIX]: 액세스 토큰 만료 오류 수정
[REFACTOR]: 사용자 조회 로직 개선
[DOCS]: API 문서 수정
[TEST]: 로그인 서비스 테스트 추가
[CHORE]: 프로젝트 환경 설정
```

### PR 작성 기준

- PR에 작업 성격에 맞는 `Label`을 설정한다.
- PR 본문에 관련 Issue 번호를 연결한다.
- 하나의 PR에는 하나의 Issue에 해당하는 작업만 포함한다.
- 작업 내용과 테스트 결과를 작성한다.
- 통합 테스트 또는 API 테스트 결과를 확인할 수 있는 스크린샷을 첨부한다.
- 불필요한 코드, 주석, 디버깅 출력문은 제거한다.
- 환경변수, 비밀번호, API Key 등의 민감한 정보는 포함하지 않는다.
- PR 생성 전 최신 `main` 브랜치 내용을 반영한다.

### PR 본문 양식

```md
## 관련 Issue

- close #17

## 작업 내용

- Google OAuth 로그인 기능을 구현했다.
- 신규 사용자와 기존 사용자의 로그인 처리를 분리했다.
- 로그인 성공 시 JWT 토큰을 반환하도록 구현했다.

## 테스트 내용

- [ ] Google OAuth 정상 로그인
- [ ] 신규 사용자 회원가입 및 로그인
- [ ] 기존 사용자 로그인
- [ ] 잘못된 인증 코드 예외 처리

## 테스트 결과

통합 테스트 결과 또는 API 테스트 스크린샷을 첨부한다.

## 참고 사항

- 리뷰어가 확인해야 할 내용을 작성한다.
```

`close #이슈번호`를 작성하면 PR이 `main` 브랜치에 병합될 때 연결된 Issue가 자동으로 종료된다.

---

## 5. Code Review 규칙

- 최소 2명 이상의 승인을 받은 후 `main` 브랜치에 병합한다.
- 리뷰어는 매주 랜덤으로 지정한다.
- 리뷰 요청을 받은 사람은 변경 목적과 영향 범위를 확인한다.
- 수정 요청이 있으면 반영한 후 다시 리뷰를 요청한다.
- 질문이나 의견에는 답변을 남긴다.
- 모든 리뷰가 완료된 후 PR 작성자가 직접 병합한다.
- 승인되지 않았거나 테스트가 실패한 PR은 병합하지 않는다.

### 리뷰 코멘트 구분

```text
MUST: 반드시 수정해야 하는 내용
SUGGESTION: 수정하면 더 좋은 내용
QUESTION: 코드의 의도나 동작을 확인하는 질문
NIT: 사소한 스타일 관련 의견
```

---

## 6. Merge 규칙

- 최소 2명 이상의 리뷰 승인을 받은 후 병합한다.
- PR 작성자가 본인의 PR을 직접 병합한다.
- 기본 병합 방식은 `Create a merge commit`을 사용한다.
- 병합 커밋 메시지에는 Issue 번호가 포함되도록 한다.
- 충돌이 발생하면 PR 작성자가 해결한다.
- 병합 후 작업 브랜치는 삭제한다.
- 테스트 또는 빌드가 실패한 상태에서는 병합하지 않는다.

### 병합 커밋 메시지 예시

```text
feat: Google OAuth 로그인 구현 (#17)
```

---

## 7. Backend Code Convention

프로젝트 내 코드 작성 방식과 API 구조를 통일하기 위해 아래 규칙을 따른다.

### 코드 작성 규칙

- 들여쓰기는 스페이스 4칸을 사용한다.
- 하나의 클래스와 메서드는 하나의 역할만 담당하도록 작성한다.
- 중복 코드는 공통 메서드나 클래스로 분리한다.
- 사용하지 않는 import, 변수, 메서드, 주석은 제거한다.
- 의존성 주입은 생성자 주입을 사용한다.
- `System.out.println()` 대신 로깅 프레임워크를 사용한다.
- 비밀번호, 토큰, API Key 등의 민감한 정보는 코드나 로그에 남기지 않는다.

### 네이밍 규칙

| 대상 | 규칙 | 예시 |
|---|---|---|
| 클래스 | PascalCase | `UserService` |
| 메서드·변수 | camelCase | `findUserById` |
| 상수 | UPPER_SNAKE_CASE | `MAX_LOGIN_COUNT` |
| 패키지 | 영문 소문자 | `user.service` |
| DB 테이블·컬럼 | snake_case | `user_profile` |

### 클래스 이름 규칙

```text
UserController
UserService
UserRepository
UserCreateRequest
UserResponse
UserNotFoundException
```

### 계층별 규칙

- `Controller`는 요청 검증과 응답 반환만 담당한다.
- 비즈니스 로직은 `Service` 또는 `Domain`에 작성한다.
- `Repository`는 데이터 조회와 저장만 담당한다.
- Entity를 API 응답으로 직접 반환하지 않고 DTO로 변환한다.
- Entity의 값을 변경할 때는 `Setter` 대신 의미 있는 메서드를 사용한다.

```java
public void changeNickname(String nickname) {
    this.nickname = nickname;
}
```

### API 규칙

- API URL은 소문자와 복수형 명사를 사용한다.
- URL에는 동사를 사용하지 않는다.
- API 버전은 `/api/v1` 형식으로 작성한다.
- 요청값은 DTO와 `@Valid`를 사용해 검증한다.
- 성공 및 에러 응답 형식은 프로젝트 전체에서 통일한다.

```text
GET    /api/v1/users
GET    /api/v1/users/{userId}
POST   /api/v1/users
PATCH  /api/v1/users/{userId}
DELETE /api/v1/users/{userId}
```

### 예외 및 테스트 규칙

- 예외는 전역 예외 처리기를 통해 공통으로 처리한다.
- 비즈니스 예외는 명확한 이름의 커스텀 예외로 작성한다.
- 기능 추가나 수정 시 관련 테스트 코드를 작성한다.
- 테스트는 `Given-When-Then` 구조로 작성한다.
- 정상 동작뿐만 아니라 실패 상황도 함께 테스트한다.

---

## 8. PR 체크리스트

PR을 생성하기 전에 아래 항목을 확인한다.

- [ ] 작업 전에 Issue를 생성했는가?
- [ ] Issue에 Assignee와 Label을 설정했는가?
- [ ] 브랜치명에 Issue 번호를 포함했는가?
- [ ] 하나의 PR에 하나의 Issue 작업만 포함했는가?
- [ ] 커밋 메시지에 Issue 번호를 포함했는가?
- [ ] PR 본문에 `close #이슈번호`를 작성했는가?
- [ ] 작업 내용과 테스트 내용을 작성했는가?
- [ ] 통합 테스트 또는 API 테스트 스크린샷을 첨부했는가?
- [ ] 코드 컨벤션을 준수했는가?
- [ ] 불필요한 코드와 디버깅 출력문을 제거했는가?
- [ ] 민감한 정보가 포함되지 않았는가?
- [ ] 최신 `main` 브랜치를 반영했는가?
- [ ] 최소 2명 이상의 리뷰 승인을 받았는가?
````
