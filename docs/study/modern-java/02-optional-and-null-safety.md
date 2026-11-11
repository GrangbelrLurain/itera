# 02. `Optional<T>`와 Null 안전성

> **핵심 주제**: `NullPointerException(NPE)`을 원천 차단하고, "값이 없을 수도 있다"는 비즈니스 계약을 코드 시그니처로 표현하는 `Optional<T>`의 올바른 활용법.

---

## 1. 왜 이 문법/패턴이 필요한가? (배경과 기존의 문제)

컴퓨터 과학자 토니 호어(Tony Hoare)는 1965년 자신이 null 참조를 발명한 것을 두고 **"10억 달러짜리 실수(Billion-Dollar Mistake)"**라고 회고했습니다.

과거 자바 코드에서는 어떤 메서드가 `null`을 반환할지 알 수 없었기 때문에, 개발자는 언제 터질지 모르는 `NullPointerException`(이하 NPE)의 공포 속에서 다음과 같은 방어적 코드를 작성해야 했습니다:

```java
// 과거 자바의 전형적인 null 체크 피라미드
public String getCityOfUser(Long userId) {
    User user = userRepository.findById(userId);
    if (user != null) {
        Profile profile = user.getProfile();
        if (profile != null) {
            Address address = profile.getAddress();
            if (address != null) {
                return address.getCity();
            }
        }
    }
    return "UNKNOWN";
}
```

이 방식은 코드를 지저분하게 만들 뿐만 아니라, 개발자가 단 한 줄이라도 null 검사를 깜빡하면 운영 환경에서 즉시 500 내부 서버 에러로 이어졌습니다.

Java 8에서 도입된 `Optional<T>`는 **"이 메서드는 결과값이 존재하지 않을 수 있습니다"**라는 사실을 반환 타입 자체로 명시하여, 호출자에게 null 가능성을 처리하도록 강제하는 표준 컨테이너입니다.

---

## 2. 타 언어(특히 TypeScript)와의 1:1 비교

TypeScript는 언어 차원에서 `null`과 `undefined`를 유니온 타입으로 다루며 옵셔널 체이닝(`?.`)을 지원하지만, Java는 객체 지향 래퍼 컨테이너 방식을 사용합니다.

| 구분 | TypeScript | Modern Java (`Optional<T>`) |
| :--- | :--- | :--- |
| **타입 표현** | `User \| undefined` 또는 `User \| null` | `Optional<User>` |
| **옵셔널 체이닝** | `user?.profile?.address?.city` | `userOpt.flatMap(User::getProfile).flatMap(Profile::getAddress).map(Address::getCity)` |
| **기본값 제공** | `city ?? "UNKNOWN"` | `opt.orElse("UNKNOWN")` 또는 `opt.orElseGet(() -> "UNKNOWN")` |
| **없을 때 예외 발생**| `if (!user) throw new NotFoundError();` | `userOpt.orElseThrow(() -> new NotFoundException())` |
| **런타임 오버헤드** | 없음 (컴파일 타임에 검사 후 소거) | 래퍼 객체(인스턴스)가 1개 생성되는 미세한 힙 오버헤드 존재 |

TypeScript의 `user?.name`이 매우 직관적이고 가볍다면, Java의 `Optional`은 **함수형 파이프라인(`map`, `filter`, `flatMap`)과 결합하여 풍부한 표현력**을 제공합니다.

---

## 3. 실전 코드 예제 (Good vs Bad)

### ❌ Bad 1: `isPresent()` + `get()`의 결합 (최악의 안티패턴)
```java
Optional<Product> productOpt = productRepository.findById(productId);

// 과거의 if (obj != null)과 다를 바 없는 코드
if (productOpt.isPresent()) {
    Product product = productOpt.get();
    processProduct(product);
} else {
    throw new ProductNotFoundException("상품을 찾을 수 없습니다.");
}
```
> `Optional`을 도입해 놓고 기존의 if-else null 체크를 그대로 흉내 낸 코드입니다. 가독성도 떨어지고 `Optional`의 함수형 장점을 전혀 살리지 못합니다.

---

### ❌ Bad 2: 파라미터나 필드로 Optional을 받는 행위
```java
public class BookingService {
    // ❌ 필드로 Optional 선언 금지 (직렬화 불가, 메모리 낭비)
    private Optional<DiscountPolicy> discountPolicy;

    // ❌ 메서드 파라미터로 Optional 받기 금지
    public void createBooking(Long userId, Optional<String> couponCode) {
        // 호출하는 쪽에서 매번 Optional.ofNullable(code)를 감싸서 넘겨야 함
    }
}
```

---

###  Good: 함수형 체이닝과 `orElseThrow()`를 활용한 클린 코드

```java
package com.example.ticket.service;

import com.example.ticket.domain.Ticket;
import com.example.ticket.exception.TicketNotFoundException;
import com.example.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    // 1. 단건 조회 시 없으면 즉시 예외 발생 (Fail-Fast)
    public Ticket getTicketOrThrow(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException("티켓이 존재하지 않습니다: " + ticketId));
    }

    // 2. 체이닝을 활용한 우아한 데이터 변환 및 필터링
    public BigDecimal calculateDiscountedPrice(Long ticketId) {
        return ticketRepository.findById(ticketId)
                // 티켓이 예약 가능한 상태인 경우만 통과
                .filter(Ticket::isAvailable)
                // 할인 금액 계산 (매핑)
                .map(ticket -> ticket.getPrice().multiply(BigDecimal.valueOf(0.9)))
                // 조건에 맞지 않거나 티켓이 없으면 기본가 0원 반환
                .orElse(BigDecimal.ZERO);
    }

    // 3. 존재 여부에 따른 조건부 실행 (ifPresentOrElse - Java 9+)
    public void notifyTicketOwner(Long ticketId) {
        ticketRepository.findById(ticketId)
                .ifPresentOrElse(
                        ticket -> sendPushNotification(ticket.getUserId(), "티켓 상태가 갱신되었습니다."),
                        () -> logWarning("알림 대상 티켓을 찾을 수 없습니다: " + ticketId)
                );
    }
}
```

---

## 4. 자주 밟는 지뢰와 베스트 프랙티스

### 💣 지뢰 1: `orElse()` vs `orElseGet()`의 치명적인 성능 차이
이 차이는 실무에서 수많은 장애를 일으키는 대표적인 함정입니다.

```java
// ❌ 위험: orElse()에 메서드 호출을 인자로 넘김
User user = userRepository.findById(userId)
        .orElse(createDefaultUser()); // ⚠️ findById 성공 여부와 상관없이 무조건 항상 실행됨!

//  올바른 코드: orElseGet()에 람다(Supplier)를 넘김
User user = userRepository.findById(userId)
        .orElseGet(() -> createDefaultUser()); //  user가 비어있을 때만 비로소 지연 실행됨!
```
- **`orElse(T other)`**: 이미 평가된 값(Eager)을 받습니다. 따라서 인자로 전달한 메서드는 **Optional에 값이 있든 없든 무조건 실행**됩니다. 만약 그 메서드가 DB 쿼리나 외부 API 호출이라면 심각한 성능 저하나 부작용(Side-effect)이 일어납니다.
- **`orElseGet(Supplier<? extends T> supplier)`**: 함수를 전달받아 **값이 null일 때만 지연 평가(Lazy)**하여 실행합니다.

### 💣 지뢰 2: `Optional.get()` 무지성 호출
- `Optional` 내부가 비어있을 때 `.get()`을 호출하면 즉시 `NoSuchElementException`이 발생합니다.
- 코드를 작성할 때 `.get()`은 거의 쓰지 않는 것이 원칙이며, 반드시 `.orElse()`, `.orElseGet()`, `.orElseThrow()` 중 하나를 사용해야 합니다.

### 💣 지뢰 3: 컬렉션을 `Optional`로 감싸는 바보짓 (`Optional<List<T>>`)
- 컬렉션(List, Set, Map)은 그 자체로 "요소가 0개인 빈 상태(Empty)"를 완벽하게 표현할 수 있습니다.
- `Optional<List<Ticket>>`은 `null`이 될 수도 있고, 빈 `Optional`일 수도 있고, 비어있는 `List`일 수도 있는 3중 혼란을 초래합니다.
- **원칙**: 컬렉션을 반환할 때는 절대 `Optional`을 쓰지 말고, 결과가 없으면 `List.of()` 또는 `Collections.emptyList()`로 빈 컬렉션을 반환하십시오.

---

## 5. 핵심 한 줄 요약

> **`Optional<T>`는 "결과가 없을 수도 있는 메서드의 반환 타입"으로만 사용하며, `orElseGet()`과 `orElseThrow()`를 활용해 안전하고 지연된 파이프라인을 구성한다.**
