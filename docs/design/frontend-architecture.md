# 프런트엔드 아키텍처

관리자(백오피스) 앱을 기준으로 하되, 이후 B2C 앱이 같은 기반 위에 올라가도록 workspace로 나눈다. 비즈니스 규칙은 계속 Java 백엔드에만 둔다 (AGENTS.md Rule 3).

## 1. 구조

```text
apps/
  admin/            # 관리자 웹. Tauri 셸이 이 URL을 로드
  b2c/              # 나중에 추가
  desktop-shell/    # Tauri thin shell (Itera Enterprise Workstation). 제목 표시줄은 웹의 features/shell이 그림
packages/
  api-client/       # @itera/api-client — contracts/*.openapi.json → 타입 + openapi-fetch 클라이언트
  domain/           # @itera/domain     — 프레젠테이션 도메인 (레이블·포맷·검증·매핑)
  ui/               # @itera/ui         — shadcn 기반 프리미티브 (Tailwind v4)
  theme/            # @itera/theme      — 토큰으로 만든 테마(light, 이후 dark)와 Tailwind @theme 매핑
  token/            # @itera/token      — 디자인 토큰 원시 값 (색, 간격, 타이포, 반경)
tooling/            # 앱도 패키지도 아닌 개발 도구 설정
  typescript/       # @itera/tsconfig — base.json, nextjs.json
  biome/            # @itera/biome    — 공유 biome.json
contracts/          # 백엔드 컨트롤러에서 생성한 OpenAPI (public / admin)
```

- 패키지는 앱을 모르고, 앱끼리는 서로 import하지 않는다.
- 패키지는 빌드 없이 소스를 그대로 노출한다 (`exports`가 `src/*.ts`를 가리키고 Next는 `transpilePackages`). 앱 내부 import는 `@/` → `src/`.
- 패키지 스코프는 `@itera`로 확정했다 (`@itera/api-client`, `@itera/domain`, `@itera/ui`, `@itera/theme`, `@itera/token`, `@itera/tsconfig`, `@itera/biome`).

## 2. 의존 방향

```text
apps/*/app (라우트, 얇게)
   └─ apps/*/features/<도메인>   서버 액션 · 조회 · domain+ui를 합친 컴포넌트
         ├─ @itera/ui          (도메인을 모름)
         ├─ @itera/domain      ─┐
         └─ @itera/api-client ◀─┘  (domain은 타입만 참조)
```

- 앱에는 `features/`만 둔다 (공용 `shared/` 없음). 여러 앱이 쓰는 것은 패키지로, Next에 묶인 것(세션 쿠키 등)은 `features/auth`에 둔다.
- feature끼리는 서로 import하지 않는다. 공용 부품은 `features/shell`에만 두고 모든 feature가 쓸 수 있다(`shell`은 다른 feature를 모른다). 라우트(`app/`)는 feature 파일을 직접 import해 조합하며, feature는 라우트를 import하지 않는다. 그래서 feature마다 `index.ts`를 두지 않는다(서버 액션 파일을 다시 export하면 서버/클라이언트 경계가 꼬일 수 있다).
- 경계는 dependency-cruiser로 검사한다(`.dependency-cruiser.cjs`, `pnpm check:boundaries`, `check_all.ps1`의 기본 단계). 규칙: `ui`는 domain·api-client를 모른다, `domain`은 ui·Next를 모르고 api-client는 타입만, `api-client`·`token`·`theme`은 잎(leaf), 패키지는 앱을 모른다, 앱에서는 `services → features → app` 방향, 백엔드 호출은 `services/`만(계약 타입은 어디서나), 순환 금지, 해석 안 되는 import 금지(선언하지 않은 워크스페이스 의존).
- feature에서 zod 스키마·레이블·포맷터를 직접 정의하지 않는다. `@itera/domain`에 둔다.

## 3. packages/domain — 프레젠테이션 도메인

서버가 맡지 못하는 화면 쪽 도메인만 둔다. **가격 계산, 마진·수수료, 위약금, 예약 상태 전이, 좌석 판단은 두지 않는다.**

| 둔다 | 두지 않는다 |
|---|---|
| 상태·유형 레이블과 배지 톤 | 가격·수수료·위약금 계산 |
| 포맷터(원화, 날짜, 출발 요약) | 예약 상태 전이 |
| 폼 값 ↔ API 요청 매핑, zod 스키마 | 재고·좌석 판단 |
| 조회/무효화에 쓰는 이름 상수 | |

- 도메인별 서브패스로 노출한다: `@itera/domain/pricing`. 지금 있는 서브패스는 `format`, `forms`, `booking`, `departure`, `pricing`, `product`, `tone`이다. 화면이 있는 도메인만 만든다(channel은 수수료 파서가 `pricing`에 있어 아직 따로 없다).
- 폼 파서는 `parseXxxForm(formData): ParseResult<요청>` 형태다. 숫자 형식 같은 **입력 모양**만 zod로 검사하고, 값의 의미(범위, 중복 등)는 백엔드가 판단한다. 실패하면 사용자에게 보여줄 메시지를 돌려준다.
- 상태 배지는 의미 있는 색(`Tone`: success, warning, muted, neutral)만 domain이 정하고, 앱이 자기 스타일로 매핑한다.
- 순수 로직과 React 코드는 서브패스로 분리한다 (`.../pricing`은 순수, `.../pricing/react`는 훅·컴포넌트). 순수 로직은 React 없이 Vitest로 테스트한다. 패키지에는 React를 기본 의존성으로 둔다.

## 4. 데이터 흐름

- **읽기**: Server Component → feature의 조회 함수 → `api-client`. 토큰은 httpOnly 쿠키라서 브라우저는 백엔드를 직접 호출하지 않는다. 클라이언트 캐시 라이브러리는 쓰지 않는다.
- **쓰기**: Server Action이 `FormData`를 domain의 zod 스키마로 검증 → 요청으로 매핑 → `api-client` 호출 → `revalidatePath` 또는 `redirect`. 실패하면 `FormState{error, fieldErrors, values}`를 돌려준다.
- **캐싱**: 서버에서만 한다. 기본은 매번 새로 조회(`no-store`)이고, 느리거나 거의 바뀌지 않는 조회에만 `fetch`의 유효 시간을 개별로 준다. 좌석·예약·출발일 상태는 항상 최신이어야 한다.
- **Client Component**는 상호작용이 필요한 곳(행 편집기, 확인 다이얼로그, 탭, 필터)에만 쓴다.

## 5. packages/api-client

- `contracts/admin.openapi.json`, `public.openapi.json`에서 `openapi-typescript`로 타입을 생성하고 커밋한다. `check_all`이 최신 여부를 검사한다.
- 서버 전용(`server-only`) 클라이언트 생성 함수가 `openapi-fetch` 클라이언트를 만든다. 인증 헤더 주입과 오류 정규화(`ApiError{status, code, message}`)를 미들웨어로 한 번에 처리한다.
- REST DTO를 TS에서 수동 정의하지 않는다 (Rule 1).

## 6. packages/ui

- (구현) `token`은 `tokens.css`의 원시 값, `theme`은 `theme.css`가 의미(primary, surface, danger …)를 부여하고 Tailwind `@theme`로 노출한다. 앱은 `globals.css`에서 `tailwindcss`와 `@itera/theme/theme.css`를 import하고 `@source`로 `packages/ui`를 스캔한다.
- 색은 스크린샷에서 뽑은 값을 `@itera/token`에 두고, `@itera/theme`이 이를 테마(CSS 변수)와 Tailwind v4 `@theme`로 매핑한다. `ui`는 theme과 token을 쓰고 그 반대는 없다.
- shadcn/ui 프리미티브를 복사해 직접 수정한다: Button, Input, Select, Checkbox, Badge, Table, Card, Dialog, Banner, Field, Pagination, Tabs.
- 도메인 개념은 넣지 않는다. 전역 CSS 클래스(`pkg-*`, `admin-*`)는 화면을 옮기면서 지운다.

## 7. 품질 도구

- Biome(lint·format), Vitest(domain의 순수 로직), dependency-cruiser(경계).
- E2E는 저장소 루트 `e2e/`(`@itera/e2e`, Playwright)에 둔다. 백엔드 `memory` 프로필(인메모리 리포지토리 + 시드 + 테스트 전용 `POST /api/test/reset`)로 SQL Server 없이 격리해서 돈다. 로컬 프로세스(기본)와 compose `e2e` 프로필 두 방식으로 실행하고, `check_all.ps1 -E2E`로 선택 실행한다. 자세한 사용법은 `e2e/README.md`.

## 8. 이전 순서

1. 잔재 삭제, React 19 타입 정렬, `@/` 별칭, Biome. **(완료)**
2. pnpm workspace, `apps/web` → `apps/admin`, 앱 제목 Itera. **(완료)**
3. `packages/api-client` 생성 후 `adminApi.ts`·`packageApi.ts` 교체. `openapi.yaml` 제거, Rule 1 문구 변경.
4. `packages/domain`(레이블·포맷·폼 매퍼·zod) + Vitest. **(완료)**
5. Tailwind v4 + `@itera/token`/`theme`/`ui`. 모든 화면을 feature 단위로 이전하고 CSS 3개 파일을 제거, `actions.ts`를 feature별로 분리. **(완료)**
6. 경계 검사(dependency-cruiser), Playwright E2E(백엔드 `memory` 프로필). **완료**(E2E 18개 테스트, 경계 규칙 12개).

미정: `/admin` 라우트 접두사를 `/`로 올릴지 (마지막 단계에서 결정).
