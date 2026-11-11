# AeroCore Backend API (`services/backend-api`)

Spring Boot 3.5 (Java 21, Gradle) REST 백엔드입니다. 패키지 목록 → 상세 → 예약, 관리자(백오피스) API, 그리고 1차 프로토타입인 항공 예약/전자항공권 API를 제공합니다.

## 실행 · 검증

JDK 21 이상이 필요합니다. 빌드 도구는 저장소의 Gradle wrapper를 씁니다.

```bash
./gradlew bootRun                 # DB_URL 이 없으면 항공 예약만 인메모리로 동작
./gradlew test                    # 단위 + HTTP(MockMvc) + 계약 일치 검사
./gradlew exportApiDocs           # 컨트롤러 → ../../contracts/{public,admin}.openapi.json 재생성
```

> Windows 에서 `JAVA_HOME` 이 존재하지 않는 경로를 가리키면 Gradle이 시작하지 못합니다. `check_all.ps1` 은 `java` 가 가리키는 JDK로 자동 대체합니다.

## 설정 (환경 변수)

| 변수 | 설명 |
|---|---|
| `PORT` | 서버 포트 (기본 8080) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | SQL Server 연결. 없으면 패키지·예약·관리자 API는 `503 UNAVAILABLE` |
| `ADMIN_USERNAME` (기본 `admin`), `ADMIN_PASSWORD`, `ADMIN_TOKEN_SECRET` | 관리자 계정. `ADMIN_PASSWORD` 가 비어 있으면 관리자 API 비활성 (기본 비밀번호 없음) |

## memory 프로필 (DB 없이 전체 API)

```bash
./gradlew bootRun --args='--spring.profiles.active=memory'
```

`MemoryConfig`가 상품·출발·예약·채널 수수료를 인메모리로 조립하고 샘플 상품(`DAD501`, `TYO201`)을 시드합니다. 관리자 계정은 `application-memory.properties`의 고정값(환경 변수로 덮어쓰기 가능)이고, `POST /api/test/reset`으로 샘플 상태로 되돌립니다(이 프로필에만 존재, 계약에는 포함되지 않음). 로컬 실행과 E2E(`e2e/`)용이며 운영에는 쓰지 않습니다.

## 구조

```text
aerocore.backend
├── controller/   REST 컨트롤러 (HTTP ↔ 서비스 변환만). 문서 그룹: public / admin
├── config/       빈 조립(AppConfig, DatabaseConfig), CORS·관리자 토큰 검사(WebConfig), 오류 응답(ApiExceptionHandler), OpenAPI 그룹
├── service/      도메인 로직 (예약 상태 머신, 가격 계산, 출발일 계산 …). Spring 비의존
├── repository/   JDBC(SQL Server) / 인메모리 구현
├── dto/ model/ exception/
```

서비스·리포지토리는 Spring 애너테이션 없는 순수 Java이며 `config/` 에서 조립합니다. 그래서 단위 테스트는 가짜 리포지토리로 DB 없이 돌고, `HttpApiTest` 는 같은 서비스를 가짜 리포지토리로 조립해 HTTP 계층만 검증합니다.

## API 계약

계약은 컨트롤러에서 생성합니다 (springdoc).

- 실행 중: `/v3/api-docs/public`, `/v3/api-docs/admin`, `/swagger-ui.html`
- 커밋된 사본: `../../contracts/public.openapi.json`, `../../contracts/admin.openapi.json`
- 컨트롤러를 바꾸고 사본을 갱신하지 않으면 `OpenApiContractTest` 가 실패합니다 → `./gradlew exportApiDocs` 후 함께 커밋하세요.

웹은 `packages/api-client`가 이 사본에서 타입(`pnpm --filter @itera/api-client gen`)을 생성합니다.

## 오류 응답

모든 오류는 `{ "error": "...", "code": "..." }` 입니다.

| 상태 | code | 상황 |
|---|---|---|
| 400 | `INVALID_ARGUMENT` | 검증 실패, 잘못된 JSON·날짜 |
| 401 | `UNAUTHENTICATED` | 관리자 토큰 없음/만료/위조 |
| 404 | `NOT_FOUND` | 없는 리소스·경로 |
| 409 | `SEAT_CONFLICT`, `ALREADY_EXISTS` | 좌석 부족, 중복 상품 |
| 422 | `FAILED_PRECONDITION` | 상태상 불가능한 작업 |
| 503 | `UNAVAILABLE` | DB 또는 관리자 비밀번호 미설정 |
