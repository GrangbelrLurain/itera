# 04. Interface와 생성자 기반 의존성 주입 (DI)

> **핵심 주제**: 객체지향 5대 원칙(SOLID)의 의존성 역전 원칙(DIP)을 실현하고, 테스트 용이성과 불변성을 극대화하는 모던 자바의 생성자 기반 의존성 주입(Constructor Injection).

---

## 1. 왜 이 문법/패턴이 필요한가? (배경과 기존의 문제)

만약 서비스 클래스 내부에서 필요한 협력 객체를 직접 `new` 키워드로 생성한다면 어떤 일이 벌어질까요?

```java
// ❌ 강한 결합(Tight Coupling)의 전형
public class TicketService {
    private TossPaymentGateway paymentGateway;

    public TicketService() {
        // 구체적인 구현 클래스에 강하게 결합됨!
        this.paymentGateway = new TossPaymentGateway("API_KEY_123");
    }
}
```

이 코드는 다음과 같은 치명적인 문제를 안고 있습니다:
1. **단위 테스트(Unit Test) 불가**: `TicketService`를 테스트하려 해도 실제 Toss 결제 서버와 통신하는 `TossPaymentGateway`가 강제로 실행됩니다. 가짜(Mock/Stub) 객체로 바꿔치기할 방법이 없습니다.
2. **변경에 취약**: 결제 대행사를 KakaoPay나 PG사로 교체하거나 테스트 환경용 인메모리 결제 모듈로 바꿀 때마다 `TicketService`의 소스 코드를 직접 뜯어고쳐야 합니다 (OCP 위반).
3. **제어권의 역전 부재**: 객체가 자신이 사용할 의존성의 생성 시점과 설정까지 모두 책임지는 기형적인 구조가 됩니다.

**인터페이스(역할)와 구현체(구현)를 분리**하고, 스프링과 같은 IoC(Inversion of Control) 컨테이너가 외부에서 구현체를 주입해 주면(DI, Dependency Injection) 결합도를 획기적으로 낮출 수 있습니다.

---

## 2. 타 언어(특히 TypeScript/NestJS)와의 1:1 비교

TypeScript(특히 NestJS) 환경에서도 의존성 주입을 널리 사용하지만, 언어의 런타임 특성으로 인해 몇 가지 중요한 차이가 있습니다.

| 구분 | TypeScript / NestJS | Modern Java / Spring Boot |
| :--- | :--- | :--- |
| **인터페이스의 실체** | 컴파일 후 JS에서 **완전히 소거(Erase)**됨 | **런타임 바이트코드에 클래스 파일로 영구 보존** |
| **주입 토큰(Token)** | 인터페이스로 주입 불가 → `@Inject('PAYMENT_GATEWAY')` 문자열/Symbol 필요 | 인터페이스 타입 자체로 자동 바인딩 (`PaymentGateway`) |
| **타입 검사 시점** | 런타임에는 덕 타이핑(Duck Typing) 형태 | 강력한 명목적 타입 검사 (Nominal Typing) |
| **주입 방식** | 생성자 주입 (`constructor(private readonly svc: Svc)`) | 생성자 주입 (`private final Svc svc;`) |
| **프레임워크 비의존 테스트**| `Test.createTestingModule(...)` 필요 | **순수 자바 생성자 호출 `new TicketService(mockGateway)`로 0.001초 만에 테스트 가능** |

TypeScript에서는 런타임에 인터페이스가 사라지므로 토큰이나 추상 클래스를 우회적으로 활용해야 하지만, Java는 **인터페이스가 런타임 1급 시민(First-class citizen)**이므로 리플렉션을 통해 완벽하고 안전하게 구현체를 탐색하고 주입합니다.

---

## 3. 실전 코드 예제 (Good vs Bad)

### ❌ Bad 1: `@Autowired` 필드 주입 (과거의 잘못된 관행)
```java
@Service
public class TicketService {
    @Autowired
    private PaymentGateway paymentGateway; // ❌ 필드 주입

    // 생성자가 없으므로 순수 자바 단위 테스트에서 paymentGateway를 주입할 방법이 없음!
    // 스프링 컨테이너를 띄우지 않고 new TicketService()를 하면 NPE 발생!
}
```
> **문제점**:
> - `final` 키워드를 붙일 수 없어 런타임에 의존성이 바뀔 위험이 있습니다.
> - 스프링 컨테이너 없이는 단위 테스트가 불가능하여 테스트 속도가 수십 배 느려집니다.
> - 순환 참조(Circular Dependency)가 발생해도 애플리케이션 구동 시점에 잡지 못하고 런타임 에러로 터집니다.

---

###  Good: 인터페이스 계약 + `private final` 생성자 주입

```java
// 1. 역할(인터페이스) 정의: 무엇을 할 것인가에만 집중
package com.example.payment.domain;

import java.math.BigDecimal;

public interface PaymentGateway {
    PaymentResult charge(String accountId, BigDecimal amount);
}
```

```java
// 2. 서비스 구현: 오직 인터페이스에만 의존하며, 불변 final 필드 사용
package com.example.ticket.service;

import com.example.payment.domain.PaymentGateway;
import com.example.payment.domain.PaymentResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class TicketService {

    // private final: 객체 생성 이후 의존성이 절대 변경되지 않음을 컴파일러가 보장
    private final PaymentGateway paymentGateway;

    // 스프링 4.3+부터는 생성자가 1개일 경우 @Autowired 생략 가능!
    public TicketService(PaymentGateway paymentGateway) {
        this.paymentGateway = Objects.requireNonNull(paymentGateway, "paymentGateway는 null일 수 없습니다.");
    }

    public void purchaseTicket(String accountId, BigDecimal price) {
        PaymentResult result = paymentGateway.charge(accountId, price);
        if (!result.isSuccess()) {
            throw new IllegalStateException("결제에 실패했습니다: " + result.message());
        }
    }
}
```

### 💡 단위 테스트에서의 압도적 우위
생성자 주입을 사용하면 무거운 스프링 컨테이너(`@SpringBootTest`)를 띄우지 않고, **순수 자바 코드(POJO)**로 밀리초(ms) 단위의 초고속 단위 테스트를 작성할 수 있습니다.

```java
class TicketServiceTest {

    @Test
    void 결제_실패_시_예외가_발생한다() {
        // 가짜(Mock) 구현체를 생성자로 직접 전달!
        PaymentGateway fakeGateway = (accountId, amount) -> new PaymentResult(false, "잔액 부족");
        TicketService service = new TicketService(fakeGateway);

        assertThrows(IllegalStateException.class, () -> {
            service.purchaseTicket("ACC-01", BigDecimal.valueOf(10000));
        });
    }
}
```

---

## 4. 자주 밟는 지뢰와 베스트 프랙티스

### 💣 지뢰 1: 구현체가 2개 이상일 때의 빈 충돌
동일한 인터페이스 `PaymentGateway`를 구현한 빈이 `TossPaymentGateway`와 `KakaoPaymentGateway` 2개 등록되어 있으면 스프링은 어떤 것을 주입해야 할지 몰라 기동 시점에 실패합니다.

```text
Parameter 0 of constructor in TicketService required a single bean, but 2 were found:
  - tossPaymentGateway: defined in file ...
  - kakaoPaymentGateway: defined in file ...
```

- **해결책 1**: 기본으로 사용할 빈에 **`@Primary`** 지정
  ```java
  @Component
  @Primary
  public class TossPaymentGateway implements PaymentGateway { ... }
  ```
- **해결책 2**: 주입받는 곳에서 **`@Qualifier("빈이름")`** 명시
  ```java
  public TicketService(@Qualifier("kakaoPaymentGateway") PaymentGateway paymentGateway) {
      this.paymentGateway = paymentGateway;
  }
  ```

### 💣 지뢰 2: 순환 참조 (Circular Dependency)
클래스 A가 B를 필요로 하고, B가 다시 A를 필요로 하는 경우 스프링 기동 시 `BeanCurrentlyInCreationException`이 발생합니다.
- 최신 스프링 부트는 순환 참조를 기본적으로 차단(Error)합니다.
- `@Lazy` 어노테이션으로 우회할 수 있지만, 이는 **설계가 꼬였다는 강력한 위험 신호(Code Smell)**입니다.
- 두 클래스가 공통으로 필요한 로직을 제3의 클래스 C로 분리하거나 이벤트를 발행하는 방식으로 설계를 리팩터링해야 합니다.

### 💣 지뢰 3: "1 인터페이스 + 1 구현체 (`*ServiceImpl`)"의 맹목적 복제
- 엔터프라이즈 레거시 프로젝트에서는 다형성이 필요 없는 단순 CRUD 서비스조차 `TicketService` 인터페이스와 `TicketServiceImpl` 클래스로 무조건 쪼개는 기계적 관행이 만연했습니다.
- **모던 프랙티스 (YAGNI 원칙)**:
  - 외부 연동(결제, 메시징, 메일)이나 여러 알고리즘 교체가 실제로 필요한 도메인은 **인터페이스**를 만듭니다.
  - 내부 상태를 다루는 단일 비즈니스 서비스는 **인터페이스 없이 곧바로 클래스로 정의**하고 필요할 때 리팩터링하는 것이 현대적인 실무 트렌드입니다.

---

## 5. 핵심 한 줄 요약

> **의존성은 언제나 `private final` 필드와 생성자 주입을 통해 전달받아 객체의 완전성을 보장하고, 프레임워크 없는 순수 단위 테스트가 가능하도록 설계한다.**
