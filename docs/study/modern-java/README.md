# 모던 자바(Java 21) & 스프링 실무 가이드
> **Target Audience**: TypeScript / JavaScript 등 타 언어 실무 개발자를 위한 모던 자바(Java 21 LTS) & Spring Boot 3 핵심 가이드

---

## 📌 문서 개요

과거의 자바(Java 8 이전)를 기억하는 개발자들은 자바를 **"장황하고(boilerplate), 느리고, 온갖 디자인 패턴으로 점철된 지루한 언어"**로 기억하곤 합니다.

하지만 **Java 21 (LTS)**과 **Spring Boot 3** 시대의 모던 자바는 완전히 달라졌습니다.
- `record`를 통한 간결하고 안전한 불변 데이터 모델링
- `Stream API`와 람다 파이프라인의 완성도 높은 함수형 패러다임
- `Optional<T>`를 활용한 명시적인 Null 안전성 계약
- 가상 스레드(Virtual Threads)와 진화된 동시성 모델
- 강력한 타입 시스템과 선언적인 의존성 주입(DI) 아키텍처

본 가이드는 **TypeScript / JavaScript, Node.js, Express/NestJS 환경에 익숙한 실무 개발자**가 모던 자바 백엔드 코드를 읽거나 작성할 때, 언어적 패러다임 차이로 인해 겪을 수 있는 혼란과 실전 지뢰를 해소하기 위해 기획되었습니다.

---

## 📚 챕터별 목차 (Curriculum)

각 문서는 **"왜 필요한가 → TS와 1:1 비교 → Good vs Bad 실전 코드 → 자주 밟는 지뢰 → 한 줄 요약"**의 5단계 구조로 깊이 있게 다룹니다.

| 번호 | 주제 | 파일 링크 | 핵심 키워드 |
| :--- | :--- | :--- | :--- |
| **01** | **`record`와 불변 데이터 모델링** | [01-record-and-immutability.md](./01-record-and-immutability.md) | `record`, 불변 객체, TS `readonly` 비교, Compact Constructor |
| **02** | **`Optional<T>`와 Null 안전성** | [02-optional-and-null-safety.md](./02-optional-and-null-safety.md) | NPE 방어, TS `T \| undefined` 비교, `.orElseThrow()`, 체이닝 안티패턴 |
| **03** | **Stream API와 람다 파이프라인** | [03-stream-api-and-lambda.md](./03-stream-api-and-lambda.md) | 선언형 데이터 처리, 지연 평가(Lazy), JS `Array` 비교, `.toList()` |
| **04** | **Interface와 생성자 기반 의존성 주입 (DI)** | [04-interface-and-dependency-injection.md](./04-interface-and-dependency-injection.md) | DIP, 다형성, `private final`, 생성자 주입 vs 필드 주입, NestJS 비교 |
| **05** | **Checked vs Unchecked Exception과 전역 에러 핸들링** | [05-runtime-exception-and-global-handling.md](./05-runtime-exception-and-global-handling.md) | `RuntimeException`, `@RestControllerAdvice`, `@ExceptionHandler`, HTTP 상태 매핑 |
| **06** | **동등성(`==` vs `.equals()`)과 래퍼 클래스 언박싱 지뢰** | [06-equality-and-wrapper-pitfalls.md](./06-equality-and-wrapper-pitfalls.md) | 메모리 주소 vs 내용 비교, 리터럴 앞치기, Integer 캐시 범위(-128~127), 언박싱 NPE |
| **07** | **멀티스레드 동시성과 `ConcurrentHashMap`** | [07-concurrency-and-atomic-operations.md](./07-concurrency-and-atomic-operations.md) | Thread Pool vs Event Loop, Check-Then-Act 경합, `putIfAbsent`, 원자적 락 판별 |
| **08** | **Spring Web REST API 핵심 애노테이션** | [08-spring-web-annotations.md](./08-spring-web-annotations.md) | `@RestController`, `@PathVariable`, `@RequestParam`, `@RequestBody`, DTO 검증 |

---

## 🎯 이 가이드의 3대 핵심 원칙

1. **타 언어(TypeScript) 개발자의 멘탈 모델 존중**
   - TS의 `interface`, `type`, `async/await`, `Array.filter/map` 개념과 모던 자바의 대응 요소를 1:1로 대조하여 러닝 커브를 최소화합니다.
2. **실제 프로덕션 환경의 실전 지뢰 분석**
   - 단순 문법 나열을 넘어, "테스트 환경에서는 잘 되다가 실서버 운영 중에 터지는 대표적인 버그 패턴"들을 깊이 있게 분석합니다.
3. **도메인 중립적이고 보편적인 예제**
   - 특정 회사나 프로젝트의 내부 비즈니스 로직에 종속되지 않고, 누구나 직관적으로 이해할 수 있는 티켓(Ticket), 예약(Booking), 상품(Product), 회원(Member) 도메인을 기반으로 설명합니다.
