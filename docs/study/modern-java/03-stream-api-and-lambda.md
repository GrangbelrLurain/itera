# 03. Stream API와 람다 파이프라인

> **핵심 주제**: 컬렉션 데이터를 명령형(Imperative) 루프로 순회하지 않고, 선언형(Declarative) 함수형 파이프라인으로 처리하는 Java Stream API와 지연 평가(Lazy Evaluation) 메커니즘.

---

## 1. 왜 이 문법/패턴이 필요한가? (배경과 기존의 문제)

과거 자바에서 데이터를 다룰 때는 항상 `for` 루프와 `if` 분기문, 그리고 임시 리스트가 필요했습니다.

```java
// 과거 자바의 전형적인 명령형 데이터 처리
List<Ticket> vipTickets = new ArrayList<>();
for (Ticket ticket : allTickets) {
    if (ticket.getPrice().compareTo(BigDecimal.valueOf(100000)) >= 0) {
        if ("CONFIRMED".equals(ticket.getStatus())) {
            vipTickets.add(ticket);
        }
    }
}
```

이 방식은 코드가 길어질수록 다음과 같은 문제를 유발합니다:
1. **의도의 불명확성**: 루프 변수, 인덱스 제어, 임시 컬렉션 할당 등 기계적인 코드가 비즈니스 로직(필터링, 변환)을 가립니다.
2. **상태 변이(State Mutation)**: 외부 가변 리스트(`vipTickets`)에 계속해서 요소를 추가하므로 멀티스레드 환경에서 안전하지 않습니다.
3. **재사용 및 조합의 어려움**: 여러 단계의 복잡한 데이터 가공(그룹핑, 정렬, 페이징 등)을 조합하기가 매우 번거롭습니다.

Java 8에서 도입되고 최신 Java 21에서 더욱 진화한 **Stream API**는 SQL 쿼리를 작성하듯 **"무엇을(What) 할 것인가"**를 선언형으로 표현할 수 있게 해 줍니다.

---

## 2. 타 언어(특히 TypeScript/JavaScript)와의 1:1 비교

JavaScript/TypeScript의 배열 고차 함수(`filter`, `map`, `reduce`)와 매우 흡사하지만, **평가 시점(Evaluation Strategy)**에서 결정적인 차이가 있습니다.

| 구분 | JavaScript / TypeScript | Java Stream API |
| :--- | :--- | :--- |
| **적용 대상** | `Array` 프로토타입 메서드 | `Collection.stream()` 인터페이스 |
| **평가 방식** | **즉시 평가 (Eager Evaluation)** | **지연 평가 (Lazy Evaluation)** |
| **메모리 생성** | 체이닝 단계마다 매번 새로운 중간 배열 생성 | 중간 배열을 생성하지 않고 파이프라인 파이프로 연결 |
| **단락 연산 (Short-circuit)** | `find` 외에는 전체 순회 발생 | `findFirst()`, `limit()` 등이 하류 연산을 즉시 중단 |
| **결과 수집** | 바로 새 배열 반환 (`arr.map(...)`) | 종단 연산(`toList()`, `collect()`)을 호출해야 실제 실행 |
| **병렬 처리** | 싱글 스레드 (Worker 분기 필요) | `.parallelStream()`으로 손쉬운 병렬 처리 지원 |

### 지연 평가(Lazy Evaluation)의 마법
JavaScript에서는 `items.filter(...).map(...).slice(0, 5)`를 실행하면 1만 개의 아이템에 대해 필터 배열 1개, 맵 배열 1개가 완전히 생성된 뒤 5개를 자릅니다.

반면 Java의 Stream은 **종단 연산(Terminal Operation)이 호출되기 전까지는 아무런 연산도 수행하지 않습니다.** 5개가 채워지는 순간 즉시 스트림을 종료하므로 불필요한 연산을 극적으로 줄여줍니다.

---

## 3. 실전 코드 예제 (Good vs Bad)

### ❌ Bad: 중첩 for문과 명령형 리스트 조작
```java
public List<String> getVipCustomerEmails(List<Booking> bookings) {
    List<String> emails = new ArrayList<>();
    for (Booking booking : bookings) {
        if (booking.getTotalPrice().compareTo(BigDecimal.valueOf(200000)) >= 0) {
            Customer customer = booking.getCustomer();
            if (customer != null && customer.isEmailSubscribed()) {
                emails.add(customer.getEmail());
            }
        }
    }
    return emails;
}
```

---

###  Good: Java 21 선언형 스트림 파이프라인

```java
package com.example.booking.service;

import com.example.booking.domain.Booking;
import com.example.booking.domain.Customer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BookingReportService {

    private static final BigDecimal VIP_THRESHOLD = BigDecimal.valueOf(200_000);

    // 1. 기본 필터링 및 매핑 파이프라인
    public List<String> getVipCustomerEmails(List<Booking> bookings) {
        return bookings.stream()
                .filter(b -> b.getTotalPrice().compareTo(VIP_THRESHOLD) >= 0)
                .map(Booking::getCustomer)
                .filter(Customer::isEmailSubscribed)
                .map(Customer::getEmail)
                .distinct() // 중복 이메일 제거
                .toList();  // Java 16+ 불변 리스트 반환!
    }

    // 2. 심화: 상태별 그룹핑 (GroupBy)
    public Map<String, List<Booking>> groupBookingsByStatus(List<Booking> bookings) {
        return bookings.stream()
                .collect(Collectors.groupingBy(Booking::getStatus));
    }

    // 3. 심화: 고객 ID별 총 결제 금액 집계 (Reduce/Sum)
    public Map<Long, BigDecimal> calculateTotalSpentByCustomerId(List<Booking> bookings) {
        return bookings.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getCustomer().getId(),
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                Booking::getTotalPrice,
                                BigDecimal::add
                        )
                ));
    }
}
```

### 💡 Java 16+의 축복: `.collect(Collectors.toList())` vs `.toList()`
과거 자바에서는 스트림 결과를 리스트로 모을 때 다음처럼 길고 번거로운 코드를 작성해야 했습니다:
```java
// 과거 방식 (Java 8 ~ 15)
List<String> list = stream.collect(Collectors.toList()); // 가변 ArrayList 반환

// 최신 모던 자바 방식 (Java 16+)
List<String> list = stream.toList(); // 간결하고 안전한 불변(Unmodifiable) List 반환!
```
최신 자바에서는 특별히 가변 리스트가 필요한 경우가 아니라면 무조건 **`.toList()`**를 사용하는 것이 표준입니다.

---

## 4. 자주 밟는 지뢰와 베스트 프랙티스

### 💣 지뢰 1: 스트림의 1회성(Single-use) 특성
스트림은 데이터 컬렉션이 아니라 **"흘러가는 파이프"**입니다. 한 번 종단 연산으로 소비된 스트림을 다시 사용하면 예외가 터집니다.

```java
Stream<Booking> stream = bookings.stream().filter(Booking::isConfirmed);

long count = stream.count(); // 종단 연산 수행 완료!
List<Booking> list = stream.toList(); // ❌ IllegalStateException: stream has already been operated upon or closed!
```
- 스트림을 재사용하려 하지 말고, 필요할 때마다 컬렉션에서 새 스트림(`bookings.stream()`)을 열어야 합니다.

### 💣 지뢰 2: 무분별한 `parallelStream()` 사용
병렬 처리를 해준다는 말에 혹해 무심코 `.parallelStream()`을 쓰면 큰 장애로 이어질 수 있습니다.
- `parallelStream()`은 JVM 전체가 공유하는 공용 풀인 **`ForkJoinPool.commonPool()`**을 사용합니다.
- 만약 특정 웹 요청의 스트림 내부에서 I/O 블로킹(DB 조회, 외부 HTTP 호출)이 일어나면, 공용 스레드 풀이 고갈되어 **서버 내 다른 모든 웹 요청의 스트림 연산까지 일제히 멈추는 대형 참사**가 발생합니다.
- **원칙**: CPU 집약적인 순수 대용량 연산(예: 수십만 건의 메모리 내 수학 계산)이 아니라면 웹 애플리케이션에서는 기본 `stream()`을 사용하는 것이 안전합니다.

### 💣 지뢰 3: 람다 내부에서 외부 지역 변수 수정 불가 (Effectively Final)
JavaScript에서는 클로저가 외부 `let` 변수를 자유롭게 수정할 수 있지만, Java 람다에서는 **`final`이거나 실질적으로 변경되지 않는(`effectively final`) 변수만 참조**할 수 있습니다.

```java
int total = 0;
// ❌ 컴파일 에러: Variable used in lambda should be final or effectively final
items.forEach(item -> { total += item.getPrice(); });

//  올바른 해결책: 함수형 리듀싱 사용
int total = items.stream().mapToInt(Item::getPrice).sum();
```

---

## 5. 핵심 한 줄 요약

> **Stream API는 중간 연산의 지연 평가를 통해 메모리 낭비 없이 대용량 데이터를 가공하며, 가공 결과는 간결한 `toList()`를 통해 불변 컬렉션으로 수집한다.**
