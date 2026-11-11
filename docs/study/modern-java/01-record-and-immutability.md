# 01. `record`를 활용한 불변 데이터 모델링

> **핵심 주제**: 모던 자바(Java 16+ 정식 도입, Java 21 표준)의 `record`를 활용해 불변(Immutable) 데이터 객체를 간결하고 안전하게 정의하는 방법과 실무 패턴.

---

## 1. 왜 이 문법/패턴이 필요한가? (배경과 기존의 문제)

과거의 자바에서 데이터 전달 객체(DTO, VO)를 하나 만들려면 수십 줄의 기계적인 코드(Boilerplate)가 필요했습니다.

```java
// 과거 자바의 전형적인 DTO (JavaBean 패턴)
public class TicketDto {
    private String ticketId;
    private Long userId;
    private BigDecimal price;

    public TicketDto() {} // 기본 생성자
    public TicketDto(String ticketId, Long userId, BigDecimal price) { ... }
    
    // 수많은 Getter / Setter
    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }
    // equals(), hashCode(), toString() 오버라이드 30~50줄 추가...
}
```

이 방식은 세 가지 심각한 문제를 야기했습니다:
1. **가변성(Mutability)으로 인한 상태 오염**: 누군가 비즈니스 로직 중간에 `setPrice(BigDecimal.ZERO)`를 호출하면 객체의 상태가 언제 어디서 바뀌었는지 추적하기 어렵고, 멀티스레드 환경에서 치명적인 동시성 버그가 발생합니다.
2. **압도적인 코드 피로도**: 핵심 비즈니스 필드는 3개인데 코드는 70줄에 달해 가독성이 극도로 떨어집니다.
3. **Lombok(`@Getter`, `@Value`, `@AllArgsConstructor`) 의존성**: 어노테이션 프로세서를 통한 바이트코드 조작에 과도하게 의존하게 되어 IDE 호환성 문제나 빌드 도구 설정의 복잡도를 증가시켰습니다.

Java는 언어 차원에서 **"불변 데이터의 투명한 보관함"**을 정의할 수 있도록 `record` 키워드를 도입했습니다.

---

## 2. 타 언어(특히 TypeScript)와의 1:1 비교

TypeScript 개발자가 자바의 `record`를 이해하는 가장 좋은 방법은 **`readonly` 프로퍼티로만 구성된 타입 + 런타임 클래스**로 바라보는 것입니다.

| 구분 | TypeScript | Modern Java (`record`) |
| :--- | :--- | :--- |
| **선언 방식** | `type Ticket = { readonly id: string; readonly price: number; }` | `public record Ticket(String id, BigDecimal price) {}` |
| **불변성 보장** | 컴파일 타임에만 체크 (런타임에는 일반 JS 객체) | **컴파일 및 런타임 수준의 완전한 불변성 (final 필드)** |
| **타이핑 시스템** | 구조적 타이핑 (Structural Typing - 형태만 같으면 호환) | 명목적 타이핑 (Nominal Typing - 정확한 타입 이름 일치 필요) |
| **메서드 지원** | 객체 리터럴에는 불가 (별도 유틸 함수 필요) | **일반 클래스처럼 비즈니스 메서드, 검증 로직 포함 가능** |
| **동등성 비교** | `obj1 === obj2`는 메모리 주소 비교 (내용 비교 별도 구현 필요) | **필드 값 기반의 `equals()` 및 `hashCode()` 자동 생성** |
| **문자열 출력** | `JSON.stringify(obj)` | 필드와 값이 예쁘게 포맷팅된 `toString()` 자동 생성 |

TypeScript에서는 런타임에 타입이 완전히 소거(Erase)되어 런타임 불변성을 보장하려면 `Object.freeze()`를 호출해야 하지만, Java의 `record`는 **바이트코드 수준에서 모든 필드가 `private final`로 선언**되며 `set` 메서드 자체가 존재하지 않습니다.

---

## 3. 실전 코드 예제 (Good vs Bad)

### ❌ Bad: 가변 JavaBean 스타일 DTO
```java
public class BookingRequest {
    private String bookingId;
    private Long customerId;
    private int ticketCount;

    public BookingRequest() {}

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public int getTicketCount() { return ticketCount; }
    public void setTicketCount(int ticketCount) { this.ticketCount = ticketCount; }
}
```
> **문제점**:
> - 인스턴스 생성 후 어디서든 setter로 값을 변조할 수 있습니다.
> - `equals()`, `hashCode()`가 구현되어 있지 않아 `Set`에 넣거나 Map의 Key로 쓰면 중복 판별이 불가능합니다.

---

###  Good: Java 21 `record`와 Compact Constructor(간결한 생성자)
```java
package com.example.booking.dto;

import java.math.BigDecimal;
import java.util.Objects;

public record BookingRequest(
    String bookingId,
    Long customerId,
    int ticketCount,
    BigDecimal unitPrice
) {
    // 1. Compact Constructor: 인자 목록을 생략하고 유효성 검증(Validation) 수행
    public BookingRequest {
        Objects.requireNonNull(bookingId, "bookingId는 필수입니다.");
        Objects.requireNonNull(customerId, "customerId는 필수입니다.");
        if (ticketCount <= 0) {
            throw new IllegalArgumentException("티켓 수량은 1장 이상이어야 합니다. 입력값: " + ticketCount);
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("단가는 0원 이상이어야 합니다.");
        }
        // 필드 할당(this.bookingId = bookingId;)은 컴파일러가 마지막에 자동 수행!
    }

    // 2. 도메인 계산 편의 메서드 추가 가능
    public BigDecimal calculateTotalAmount() {
        return unitPrice.multiply(BigDecimal.valueOf(ticketCount));
    }

    // 3. Wither 패턴 (불변 복사본 생성)
    public BookingRequest withTicketCount(int newTicketCount) {
        return new BookingRequest(this.bookingId, this.customerId, newTicketCount, this.unitPrice);
    }
}
```

#### 컴파일러가 자동으로 생성해 주는 것들:
1. 모든 필드에 대응하는 `private final` 멤버 변수
2. 게터 메서드: `getBookingId()`가 아니라 필드명과 동일한 **`bookingId()`**, `customerId()`
3. 모든 필드를 비교하는 **`equals()`** 및 **`hashCode()`**
4. `BookingRequest[bookingId=BK-101, customerId=1, ticketCount=2, unitPrice=50000]` 형태의 가독성 높은 **`toString()`**

---

## 4. 자주 밟는 지뢰와 베스트 프랙티스

### 💣 지뢰 1: 얕은 불변성(Shallow Immutability) - 가변 컬렉션 참조
`record` 자체는 불변이지만, 내부 필드가 가변 객체(예: `List`, `Map`, `Date`)라면 내부 요소가 변조될 수 있습니다.

```java
// ❌ 위험한 코드: 외부에서 list를 조작할 수 있음
public record OrderDto(String orderId, List<String> items) {}

List<String> items = new ArrayList<>();
items.add("ITEM-1");
OrderDto order = new OrderDto("ORD-01", items);
items.add("ITEM-2"); // order 내부의 items도 변경됨!

//  베스트 프랙티스: Compact Constructor에서 방어적 복사(Defensive Copy) 수행
public record OrderDto(String orderId, List<String> items) {
    public OrderDto {
        // List.copyOf()는 Java 10+에서 제공하는 완전한 불변 리스트 복사 메서드
        items = (items == null) ? List.of() : List.copyOf(items);
    }
}
```

### 💣 지뢰 2: record는 상속(`extends`)할 수 없다
- `record`는 이미 암묵적으로 `java.lang.Record`를 상속받고 있으므로, 다른 클래스를 `extends`할 수 없습니다 (Java는 단일 상속만 지원).
- 또한 `record`는 기본적으로 `final` 클래스이므로 다른 클래스가 이를 상속할 수도 없습니다.
- **해결책**: 다형성이 필요하다면 **인터페이스 구현(`implements`)**을 사용하십시오.
  ```java
  public sealed interface PaymentRequest permits CardPaymentRequest, CashPaymentRequest {}
  public record CardPaymentRequest(String cardNumber, BigDecimal amount) implements PaymentRequest {}
  public record CashPaymentRequest(String receiptNumber, BigDecimal amount) implements PaymentRequest {}
  ```

### 💣 지뢰 3: JPA `@Entity`로 record를 직접 쓰지 말 것
- JPA(Hibernate) 사양은 엔티티에 기본 생성자(No-args constructor)와 프록시 생성을 위한 상속 가능성(Non-final), 그리고 지연 로딩을 위한 필드 수정을 요구합니다.
- 따라서 JPA Entity는 일반 클래스로 작성하고, **컨트롤러 계층의 API Request/Response DTO, 서비스 계층 간 데이터 전달 객체, CQRS Query 반환 모델**에 `record`를 적극 활용하는 것이 실무 표준입니다.

---

## 5. 핵심 한 줄 요약

> **`record`는 불변성, 필드 기반 동등성, 간결성을 바이트코드 레벨에서 보장하므로, 모든 DTO·API 스펙·값 객체(VO)의 최우선 기본 선택지(Default Choice)로 사용한다.**
