# 06. 동등성(`==` vs `.equals()`)과 래퍼 클래스 언박싱 지뢰

> **핵심 주제**: 자바의 원시 타입(Primitive)과 참조 타입(Reference)의 차이, 메모리 주소 비교(`==`)와 논리적 동등성(`.equals()`)의 구분, 그리고 자동 언박싱(Unboxing) 과정에서 숨어 있는 NPE 함정을 파헤칩니다.

---

## 1. 왜 이 문법/패턴이 필요한가? (배경과 기존의 문제)

자바는 성능을 위한 **원시 타입(Primitive Type: `int`, `long`, `boolean`, `double` 등)**과 객체 지향을 위한 **참조 타입(Reference Type: `Integer`, `Long`, `String`, 일반 클래스 등)**이 공존하는 이중적인 타입 체계를 가지고 있습니다.

이로 인해 타 언어 개발자가 자바를 처음 접할 때 가장 빈번하게 버그를 일으키는 두 가지 개념이 존재합니다:
1. **동일성 (Identity, `==`)**: 두 변수가 **완전하게 같은 힙 메모리 주소**를 가리키고 있는가?
2. **동등성 (Equality, `.equals()`)**: 두 객체가 서로 다른 메모리에 존재하더라도 **내부 데이터(값)가 논리적으로 같은가?**

자바의 `==` 연산자는 객체를 비교할 때 무조건 **메모리 주소값만 비교**합니다. 따라서 객체의 내용을 비교하기 위해 `==`를 사용하면 겉보기엔 그럴싸해 보여도 심각한 런타임 버그가 발생합니다.

---

## 2. 타 언어(특히 TypeScript/JavaScript)와의 1:1 비교

JavaScript/TypeScript의 일치 연산자(`===`)와 자바의 연산자를 대조해 보면 다음과 같습니다.

| 구분 | TypeScript / JavaScript | Modern Java |
| :--- | :--- | :--- |
| **원시값 비교** | `1 === 1` (true) | `1 == 1` (true - 원시 비트 비교) |
| **문자열 비교** | `"A" === "A"` (true - 값 자체 비교) | `"A" == "A"` (⚠️ 주소 비교이므로 상황에 따라 다름!)<br> `"A".equals("A")` ( true - 내용 비교) |
| **객체 래퍼** | `Number(10)`, `10` 구분 적음 | `int` (스택 메모리 값) vs `Integer` (힙 메모리 래퍼 객체) |
| **null 가능성** | 숫자도 `number \| null` 가능 | 원시형 `int`는 null 불가(기본값 0), 래퍼 `Integer`만 null 가능 |
| **자동 변환** | 암묵적 형변환 (`==` 금지, `===` 권장) | 컴파일러가 자동 박싱/언박싱 (`int` ↔ `Integer`) 수행 중 NPE 위험 |

TypeScript에서는 문자열을 `str1 === str2`로 손쉽게 값 비교하지만, 자바에서 `str1 == str2`를 쓰면 **문자열의 내용이 같아도 메모리 주소가 달라 false가 되는 참사**가 일어납니다.

---

## 3. 실전 코드 예제 (Good vs Bad)

### ❌ Bad 1: 문자열을 `==`로 비교하는 실수
```java
public boolean isTicketConfirmed(String status) {
    // ⚠️ 위험: status가 힙의 다른 곳에 생성된 문자열이면 false가 반환됨!
    return status == "CONFIRMED"; 
}
```
> **왜 테스트할 때는 통과했을까?**  
> 자바는 소스 코드에 하드코딩된 리터럴 문자열을 JVM 내부의 **문자열 상수 풀(String Constant Pool)**에 캐싱합니다. 따라서 테스트 코드에서 `isTicketConfirmed("CONFIRMED")`를 넘기면 주소가 우연히 같아 true가 나옵니다.  
> 하지만 운영 서버에서 DB나 HTTP JSON 요청을 파싱해 동적으로 생성된 `new String("CONFIRMED")`가 들어오는 순간 주소가 달라져 `false`로 오작동합니다.

---

### ❌ Bad 2: `Integer / Long` 래퍼 객체를 `==`로 비교하는 실수
```java
Long bookingId1 = 1000L;
Long bookingId2 = 1000L;

if (bookingId1 == bookingId2) { // ❌ false!!
    System.out.println("동일한 예약입니다.");
}
```
> `Long`은 객체이므로 `==`는 두 객체의 메모리 주소를 비교합니다. `1000L`은 서로 다른 객체이므로 `false`가 됩니다!

---

### ❌ Bad 3: 자동 언박싱(Auto-Unboxing)으로 인한 숨겨진 NPE
```java
public class TicketOrder {
    private Integer quantity; // 래퍼 클래스는 null이 될 수 있음!

    public Integer getQuantity() { return quantity; }
}

TicketOrder order = new TicketOrder(); // quantity는 null 상태

// ❌ 런타임 NullPointerException 발생!
// 원시 타입 int에 대입하기 위해 JVM이 내부적으로 order.getQuantity().intValue()를 호출함!
int finalCount = order.getQuantity(); 
```

---

###  Good: `.equals()`, "리터럴 앞치기", 그리고 `Objects.equals()`

```java
package com.example.common.util;

import java.util.Objects;

public class EqualityBestPractice {

    // 1. "리터럴 앞치기" 테크닉 (Yoda Conditions)
    // status가 null이어도 NPE가 발생하지 않고 안전하게 false를 반환함!
    public boolean isConfirmed(String status) {
        return "CONFIRMED".equals(status);
    }

    // 2. Objects.equals(a, b) 활용 (둘 중 하나가 null이어도 안전함)
    public boolean isSameTicketId(Long id1, Long id2) {
        // 내부 구현: (id1 == id2) || (id1 != null && id1.equals(id2))
        return Objects.equals(id1, id2);
    }

    // 3. 언박싱 전 안전한 기본값 방어
    public int getSafeQuantity(Integer quantity) {
        return (quantity != null) ? quantity : 0;
    }
}
```

---

## 4. 자주 밟는 지뢰와 베스트 프랙티스

### 💣 지뢰 1: Java의 캐싱 범위 (-128 ~ 127) 함정
자바는 성능 최적화를 위해 **-128부터 127까지의 `Integer` 및 `Long` 객체를 미리 메모리에 캐싱**해 둡니다.

```java
Integer a = 100;
Integer b = 100;
System.out.println(a == b); //  true (캐싱된 동일 메모리 주소 공유!)

Integer x = 200;
Integer y = 200;
System.out.println(x == y); // ❌ false (-128~127 범위를 벗어나 새 객체 생성됨!)
```
- 로컬 개발 환경에서 ID가 1, 2, 3일 때는 `id1 == id2`가 `true`로 잘 동작하다가, **운영 서버에서 데이터가 쌓여 ID가 128을 넘어가는 순간 장애**가 터지는 전설적인 버그의 주범입니다.
- **절대 규칙**: 숫자 래퍼 객체(`Long`, `Integer`)는 무조건 **`Objects.equals(a, b)`** 또는 **`a.equals(b)`**로 비교하십시오.

### 💣 지뢰 2: 삼항 연산자의 자동 언박싱 트랩
```java
boolean condition = false;
Integer obj = null;

// ❌ NullPointerException 발생!
// 삼항 연산자의 한쪽이 원시형(0)이면, 컴파일러는 전체 표현식의 타입을 원시형 int로 맞추기 위해 obj를 강제 언박싱함!
Integer result = condition ? 0 : obj; 
```
- 삼항 연산자에서 한쪽은 원시형(`0`), 다른 쪽은 래퍼 객체(`obj`)를 반환하도록 작성하면, 조건이 false일 때 `null`인 `obj`를 `int`로 바꾸려다 NPE가 터집니다.
- 양쪽의 타입을 반드시 일치시켜야 합니다 (`0` 대신 `Integer.valueOf(0)` 사용).

### 💣 지뢰 3: `equals()`만 재정의하고 `hashCode()`를 빼먹는 실수
일반 클래스에서 동등성을 비교하기 위해 `equals()`를 직접 구현할 때 `hashCode()`를 함께 재정의하지 않으면:
- `HashSet`, `HashMap` 같은 해시 기반 자료구조에서 데이터가 중복 저장되거나, 분명히 저장해 둔 키를 찾지 못하는(`map.get(key) == null`) 기괴한 현상이 발생합니다.
- (앞서 다룬 **Java 21 `record`**를 사용하면 컴파일러가 둘을 완벽하게 세트로 자동 생성해 주므로 이 문제를 원천 예방할 수 있습니다!)

---

## 5. 핵심 한 줄 요약

> **자바의 `==`는 오직 메모리 주소만 비교하므로 문자열과 래퍼 객체는 반드시 `.equals()`나 `Objects.equals()`로 비교하고, 래퍼 클래스를 원시형으로 다룰 때는 항상 null 언박싱 NPE를 경계한다.**
