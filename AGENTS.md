# AeroCore Agent Working Rules (AGENTS.md)

이 문서는 AeroCore 프로젝트에서 AI 코딩 에이전트(LLM)와 개발자가 협업할 때 반드시 준수해야 하는 **아키텍처 규칙, 코딩 컨벤션 및 검증 가이드**입니다.

---

## 1. 프로젝트 개요 & 아키텍처

AeroCore(제품명 Itera)는 단일 Java 백엔드와 웹 UI 중심 구조로 만든 항공/여행 예약 플랫폼입니다. 1차 이전 범위는 **패키지 목록 → 상세 → 예약** 흐름입니다.

```text
[Desktop] Tauri (Rust, Thin Shell) ──┐
                                     ├──(REST / OpenAPI)──> [Backend] Java
[Web] Next.js (App Router, TS) ──────┘
```

1. **`services/backend-api/` (Java 21 / Spring Boot / Gradle)**: 예약 상태 머신, 좌석/재고 락, 결제/정산/위약금 등 비즈니스 무결성 코어. REST API 제공.
2. **`apps/admin/` (Next.js App Router / TypeScript)**: **관리자(백오피스) 전용** 웹 UI (`/admin/**`). 고객용(B2C) 화면은 별도 앱의 몫이며 이 앱에는 두지 않습니다. 원격 서버에서 서빙되어 클라이언트 무재배포. 구조·계층·이전 계획은 `docs/design/frontend-architecture.md`.
3. **`apps/desktop-shell/` (Tauri v2 / Rust)**: 제품명 *Itera Enterprise Workstation*. 설정한 웹 주소(`ITERA_WEB_URL`)를 시스템 WebView로 로드하고, 창 제어·파일 내보내기·알림 같은 OS 브리지만 대행하는 Thin Shell. 윈도우/맥 대상(리눅스는 설정만). 제목 표시줄은 웹(`features/shell/ShellTitleBar`)이 그리고 셸은 네이티브 프레임만 끄고 켠다. 자세한 내용은 `apps/desktop-shell/README.md`.
4. **`services/gds-adapter/` (Java)**: GDS 연동 계층의 포트-어댑터 스케치(시뮬레이션, 벤더 SDK 없음).
5. **`contracts/{public,admin}.openapi.json`**: 클라이언트-서버 통신의 **단일 진실의 원천(SSOT)**. 백엔드 컨트롤러(Spring + springdoc)에서 생성하고 커밋합니다. 클라이언트 타입은 `packages/api-client`가 이 계약에서 생성합니다.

---

## 2. 절대 규칙 (Absolute Rules for Agents)

### 🚨 Rule 1: 계약(Contract) First 원칙
- REST 요청/응답 DTO를 TS 코드에서 **절대 수동으로 정의하지 마십시오.** 타입은 항상 `@itera/api-client`(생성물)에서 가져옵니다.
- 변경 순서: ① Java 컨트롤러·DTO 수정 → ② `cd services/backend-api && ./gradlew exportApiDocs`로 `contracts/*.openapi.json` 갱신 → ③ `pnpm --filter @itera/api-client gen`으로 클라이언트 타입 재생성 → ④ 앱 코드 수정.
- 컨트롤러와 계약 사본이 어긋나면 `OpenApiContractTest`가, 계약과 생성 타입이 어긋나면 `gen:check`가 실패합니다.
- 응답 DTO(record)의 모든 필드는 필수로 문서화되며, null 가능한 필드는 `@Nullable`(또는 박스 타입)로 표시합니다.

### 🚨 Rule 2: 데스크톱 셸(Thin Shell) 불변 원칙
- `apps/desktop-shell`에는 **UI 코드(HTML/CSS/JS)를 번들링하거나 추가하지 마십시오.** (`ui/`는 로딩 스플래시 전용; 제목 표시줄 같은 화면은 웹이 그린다)
- 웹에 열어 줄 네이티브 명령은 `build.rs`에 등록하고 capability로 허용한 것만 가능합니다. 새 명령은 입력 검증(경로·크기·확장자)과 단위 테스트를 함께 추가하십시오.
- 이 앱은 Next.js 웹 URL을 WebView2로 로드하는 역할만 합니다.
- 로컬 파일 저장, 알림 등 네이티브 OS API 브리지 외에는 코드를 수정하지 마십시오.

### 🚨 Rule 3: 비즈니스 로직은 Java 백엔드에
- 도메인 정산/예약 상태 머신/재고 락 로직은 `services/backend-api`에만 작성하십시오.
- Next.js(서버 컴포넌트/Route Handler)와 Tauri에는 화면 오케스트레이션 외의 도메인 로직을 두지 마십시오.

---

## 3. 검증 명령어 (Feedback Loop)

에이전트는 코드 수정 후 반드시 다음 검증 명령어를 실행하여 에러가 0건임을 확인하고 보고해야 합니다.

```bash
# 0. 전체 원클릭 종합 검증
powershell -ExecutionPolicy Bypass -File .\check_all.ps1

# 1. 계약 변경 시: 컨트롤러 → 계약 → TS 타입 순으로 재생성
cd services/backend-api && ./gradlew exportApiDocs
pnpm --filter @itera/api-client gen

# 1-1. 백엔드 (Spring) 테스트 + 컨트롤러-계약 일치 검사
cd services/backend-api && ./gradlew test

# 1-2. 프레젠테이션 도메인(레이블·포맷·폼 매퍼) 단위 테스트
pnpm --filter @itera/domain check && pnpm --filter @itera/domain test

# 2. Next.js 웹 검증
pnpm --filter @itera/admin check && pnpm --filter @itera/admin build

# 1-3. 모듈 경계 검사 (dependency-cruiser: packages 계층, features 격리, 순환 금지)
pnpm check:boundaries

# 2-1. E2E (Playwright, 백엔드 memory 프로필; 처음 한 번 `pnpm --filter @itera/e2e install:browsers`)
powershell -ExecutionPolicy Bypass -File .\check_all.ps1 -E2E

# 3. Tauri 데스크톱 셸 검증
cd apps/desktop-shell && cargo check && cargo test
```

---

## 4. 디렉터리 구조 맵

```text
aerocore/
├── AGENTS.md
├── README.md
├── check_all.ps1              # 전체 검증 파이프라인 (-E2E: Playwright 포함)
├── e2e/                       # @itera/e2e: Playwright E2E (백엔드 memory 프로필 위에서 실행)
├── docker-compose.yml         # 로컬 테스트 환경 (SQL Server + backend + admin)
├── infra/db/                  # DB 초기화 SQL (001 DB, 002 상품/출발 스키마, 003 샘플 시드, 005 예약, 006 가격, 007 날짜별 원가)
├── packages/                  # @itera/api-client(계약 생성 클라이언트), domain(레이블·폼 매퍼), ui(Tailwind 프리미티브), theme, token
├── tooling/                   # 공유 tsconfig·Biome 설정
├── apps/
│   ├── admin/                 # Next.js App Router (TS), 관리자 웹
│   └── desktop-shell/         # Tauri v2 (Rust) Thin Shell
├── contracts/                 # 컨트롤러에서 생성한 OpenAPI (public / admin), exportApiDocs 로 갱신
├── services/
│   ├── backend-api/           # Spring Boot REST 백엔드 (Gradle)
│   └── gds-adapter/           # Java GDS 어댑터 (시뮬레이션)
└── docs/
    ├── adr/                   # Architecture Decision Records
    └── design/                # 설계 문서 (package-departure, package-pricing, package-booking)
```
