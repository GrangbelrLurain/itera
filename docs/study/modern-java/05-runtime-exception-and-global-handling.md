# 05. Checked vs Unchecked Exception과 전역 에러 핸들링

> **핵심 주제**: 자바의 독특한 예외 계층 구조를 이해하고, 왜 모던 자바에서는 비즈니스 예외를 `RuntimeException`으로 다루며 `@RestControllerAdvice`로 일괄 처리하는지 알아봅니다.

---

## 1. 왜 이 문법/패턴이 필요한가? (배경과 기존의 문제)

자바는 프로그래밍 언어 역사에서 거의 유일하게 **Checked Exception(검사 예외)**을 언어 스펙에 정식 도입한 언어입니다.

```java
// 과거 자바의 Checked Exception 지옥
public void processBooking() throws SQLException, IOException, ClassNotFoundException {
    // 메서드를 호출하는 모든 상위 호출자에게 throws 절을 강제함
}
```

언어 설계자들의 의도는 "복구 가능한 에러를 컴파일 타임에 강제 처리하게 하자"는 선의였으나, 실제 현장에서는 대재앙이 되었습니다:
1. **무의미한 catch 블록 남발**: 귀찮아진 개발자들이 `catch (Exception e) {}` 처럼 에러를 삼켜버려 버그 추적이 불가능해졌습니다.
2. **함수형 프로그래밍과의 충돌**: Java 8의 `Stream`, `Lambda`, `Optional` 함수형 인터페이스 시그니처에는 Checked Exception이 선언되어 있지 않아, 스트림 내부에서 Checked Exception을 던지려면 코드가 기괴해집니다.
3. **캡슐화 파괴(Abstraction Leak)**: DB 계층의 `SQLException`이 서비스와 컨트롤러를 거쳐 최상위까지 `throws`로 노출되는 계층 누수가 발생했습니다.

따라서 **모던 자바와 스프링 생태계는 복구 불가능하거나 클라이언트에 안내해야 하는 모든 비즈니스 예외를 Unchecked Exception(`RuntimeException`)으로 전환**하여 처리합니다.

---

## 2. 타 언어(특히 TypeScript)와의 1:1 비교

TypeScript/JavaScript 개발자에게 "컴파일러가 `try-catch`를 강제한다"는 개념은 매우 낯선 제약입니다.

| 구분 | TypeScript / JavaScript | Modern Java |
| :--- | :--- | :--- |
| **기본 예외 모델** | 모든 예외가 런타임 언체크드 (`throw new Error()`) | `Checked Exception` vs `Unchecked Exception` 이원화 |
| **컴파일 타임 강제** | 없음 (`try-catch` 안 해도 컴파일 통과) | `Exception` 상속 시 `try-catch` 또는 `throws` 강제 |
| **모던 비즈니스 예외** | `class AppError extends Error` | `class BusinessException extends RuntimeException` |
| **전역 에러 핸들링** | Express 미들웨어 `(err, req, res, next)`, NestJS `ExceptionFilter` | Spring Boot `@RestControllerAdvice` + `@ExceptionHandler` |
| **API 에러 응답** | 보통 자유로운 JSON 객체 반환 | RFC 7807 `ProblemDetail` 또는 표준 `ErrorResponse` DTO |

TypeScript에서는 런타임 에러를 던지고 최상위 미들웨어에서 잡는 것이 자연스럽습니다. 모던 자바 역시 **도메인 계층에서 `RuntimeException`을 던지고, 웹 계층의 `@RestControllerAdvice`가 이를 낚아채어 적절한 HTTP 상태 코드(400, 404, 409 등)로 변환**하는 아키텍처를 표준으로 채택하고 있습니다.

---

## 3. 실전 코드 예제 (Good vs Bad)

### ❌ Bad: Checked Exception 남발 및 예외 삼키기
```java
public void reserveTicket(Long ticketId) throws TicketNotFoundCheckedException, SoldOutCheckedException {
    // ❌ 비즈니스 예외마다 Checked Exception을 선언하여 상위 메서드 전체를 오염시킴
}

// 호출하는 쪽
try {
    ticketService.reserveTicket(10L);
} catch (Exception e) {
    e.printStackTrace(); // ❌ 예외를 콘솔에만 찍고 삼켜버림 (호출자는 정상 처리된 줄 앎)
}
```

---

###  Good: 비즈니스 런타임 예외 체계 + `@RestControllerAdvice` 전역 처리

#### 1) 커스텀 런타임 예외와 에러 코드 정의
```java
package com.example.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "요청한 티켓을 찾을 수 없습니다."),
    SEAT_ALREADY_RESERVED(HttpStatus.CONFLICT, "T002", "이미 예약 완료된 좌석입니다."),
    INVALID_PAYMENT_AMOUNT(HttpStatus.BAD_REQUEST, "P001", "결제 금액이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
}
```

```java
package com.example.common.exception;

// RuntimeException을 상속받아 throws 선언을 강제하지 않음
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
```

#### 2) 서비스 계층: 깔끔하게 예외 던지기
```java
@Service
public class TicketService {

    public Ticket getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TICKET_NOT_FOUND));
    }
}
```

#### 3) 전역 에러 핸들러 (`@RestControllerAdvice`)
컨트롤러 바깥에 위치하며, 애플리케이션 전역에서 발생하는 예외를 가로채 일관된 JSON 에러 응답으로 변환합니다.

```java
package com.example.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. 도메인 비즈니스 예외 처리 (4xx)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("Business Exception 발생: [{}] {}", errorCode.getCode(), e.getMessage());

        ErrorResponse body = new ErrorResponse(
                errorCode.getCode(),
                e.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    // 2. 알 수 없는 시스템 예외 처리 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnhandledException(Exception e) {
        log.error("예상치 못한 시스템 에러 발생", e);

        ErrorResponse body = new ErrorResponse(
                ErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                "서버에서 오류가 발생했습니다. 관리자에게 문의하세요.",
                Instant.now()
        );
        return ResponseEntity.status(500).body(body);
    }

    // 표준 에러 응답 DTO
    public record ErrorResponse(String code, String message, Instant timestamp) {}
}
```

---

## 4. 자주 밟는 지뢰와 베스트 프랙티스

### 💣 지뢰 1: Spring `@Transactional` 롤백의 기본 동작 함정
이것은 스프링을 다루는 모든 주니어 개발자가 반드시 밟는 지뢰입니다.

- **스프링의 `@Transactional`은 기본적으로 `RuntimeException`과 `Error`가 발생했을 때만 트랜잭션을 롤백합니다.**
- 만약 Checked Exception(`Exception`, `IOException` 등)이 발생하면, 스프링은 **예외가 발생했음에도 DB 작업을 COMMIT**해 버립니다!
- 따라서 비즈니스 예외는 반드시 `RuntimeException`을 상속받거나, 부득이하게 Checked Exception을 던질 때는 `@Transactional(rollbackFor = Exception.class)` 옵션을 반드시 명시해야 합니다.

### 💣 지뢰 2: 원인 예외(Cause) 유실 (Exception Chaining 누락)
외부 라이브러리나 저수준 에러를 커스텀 예외로 감싸서 다시 던질 때 원래 에러를 인자로 넘기지 않으면, 실제 버그의 근본 원인(Root Cause) 스택트레이스가 통째로 사라집니다.

```java
try {
    thirdPartyClient.call();
} catch (IOException e) {
    // ❌ 최악: 원래 e가 왜 터졌는지 원인이 유실됨
    throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR);

    //  원인 예외(Cause)를 체이닝하여 보존
    throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR, "PG사 통신 실패: " + e.getMessage(), e);
}
```

### 💣 지뢰 3: 흐름 제어(Flow Control)를 위한 예외 남발
- 자바에서 예외 객체가 생성될 때 JVM은 현재 스레드의 호출 스택 전체를 캡처하는 `fillInStackTrace()` 작업을 수행합니다.
- 이는 일반적인 객체 생성보다 **수십~수백 배 무거운 연산**입니다.
- 단순한 조건 분기("비밀번호 5회 틀림", "입력값 누락")를 정상 로직 대신 무분별한 `throw new Exception()`으로 처리하면 고부하 상황에서 CPU 낭비가 심해집니다.

---

## 5. 핵심 한 줄 요약

> **도메인 비즈니스 에러는 `RuntimeException` 기반의 일관된 예외 계층으로 설계하고, 컨트롤러 바깥의 `@RestControllerAdvice`에서 HTTP 상태 코드와 표준 JSON 에러 규격으로 일괄 매핑한다.**
