# 07. 멀티스레드 동시성과 `ConcurrentHashMap`

> **핵심 주제**: Node.js 싱글스레드 모델과 대비되는 Java의 멀티스레드 런타임 환경, Check-Then-Act 경합 조건(Race Condition), 그리고 `ConcurrentHashMap`의 원자적 연산을 통한 안전한 인메모리 동시성 제어.

---

## 1. 왜 이 문법/패턴이 필요한가? (배경과 기존의 문제)

Node.js(Express/Fastify) 개발자는 기본적으로 싱글 스레드 이벤트 루프 위에서 코드를 작성합니다. 비동기 I/O 대기는 발생하지만, **동일한 메모리 주소의 변수를 두 개의 스레드가 동시에 수정하는 일(Memory Race Condition)**은 거의 걱정하지 않습니다.

하지만 **Spring Boot(Spring MVC)는 요청 하나당 스레드 하나를 할당하는 멀티스레드 모델(Thread-per-request)**입니다.
톰캣(Tomcat)의 스레드 풀(기본 200개)에서 생성된 수많은 스레드가 **동일한 JVM 힙 메모리를 병렬로 공유**합니다.

```java
// ❌ 멀티스레드 환경에서 일반 HashMap을 공유 상태로 사용하는 참사
public class TicketInventoryService {
    // 모든 요청 스레드가 공유하는 인메모리 맵
    private Map<String, Integer> stockMap = new HashMap<>(); 

    public void deductStock(String ticketId) {
        Integer current = stockMap.get(ticketId);
        if (current > 0) {
            stockMap.put(ticketId, current - 1);
        }
    }
}
```

일반 `HashMap`을 멀티스레드 환경에서 공유하면 다음과 같은 치명적인 문제가 발생합니다:
1. **데이터 유실(Lost Updates)**: 두 스레드가 동시에 같은 재고(10개)를 읽고 9개로 덮어써서 1건의 결제가 증발합니다.
2. **CPU 100% 무한 루프 버그**: 내부 버킷의 리사이징(Rehashing) 과정에서 링크드 리스트가 꼬여 순환 참조가 발생하면 서버 CPU가 100%로 치솟으며 서버가 뻗습니다.

---

## 2. 타 언어(특히 TypeScript/Node.js)와의 1:1 비교

| 구분 | Node.js (TypeScript) | Modern Java (Spring MVC) |
| :--- | :--- | :--- |
| **기본 동시성 모델** | 단일 이벤트 루프 (Single-threaded Event Loop) | **스레드 풀 기반 멀티스레드 (Multi-threaded)** |
| **메모리 경합(Data Race)**| 원칙적으로 메모리 동시 접근 충돌 없음 | **수십 개 스레드가 동일한 메모리 힙 공간을 동시 수정** |
| **연산의 원자성** | `count++`가 안전함 (싱글스레드 실행 컨텍스트) | `count++`는 읽기-수정-쓰기 3단계로 쪼개져 깨짐! |
| **동시성 컬렉션** | 일반 `Map` / 객체 리터럴 사용 | **`ConcurrentHashMap`, `AtomicInteger` 등 필수** |
| **I/O 블로킹 처리** | `async / await` 논블로킹 강제 | 스레드가 대기하거나 Java 21 가상 스레드(Virtual Thread) 활용 |

Java에서는 "단순한 변수 읽기 후 쓰기"조차 찰나의 순간에 다른 스레드가 끼어들 수 있으므로, **원자적(Atomic) 연산**을 보장하는 특수 자료구조를 사용해야 합니다.

---

## 3. 실전 코드 예제 (Good vs Bad)

### ❌ Bad 1: Check-Then-Act (확인 후 실행)의 찰나의 틈
```java
// ❌ ConcurrentHashMap을 썼지만 동시성이 깨지는 전형적인 코드
public class SeatReservation {
    private Map<String, String> reservations = new ConcurrentHashMap<>();

    public boolean reserveSeat(String seatNo, String userId) {
        // [1] 확인: 빈 자리인가?
        if (!reservations.containsKey(seatNo)) {
            // ⚠️ 찰나의 틈(Race Condition) 발생! 
            // 스레드 A와 B가 동시에 containsKey()를 false로 통과함!
            
            // [2] 실행: 좌석 할당
            reservations.put(seatNo, userId); 
            return true; // 두 사용자 모두 예약 성공했다고 응답! (이중 예약 버그)
        }
        return false;
    }
}
```
> `ConcurrentHashMap`은 개별 메서드(`containsKey`, `put`)의 스레드 안전성만 보장할 뿐, **"확인하고 넣는" 두 연산 사이의 시간차**까지 막아주지는 못합니다.

---

### ❌ Bad 2: 메서드 전체에 `synchronized`를 걸어버리는 성능 병목
```java
public synchronized boolean reserveSeat(String seatNo, String userId) { ... }
```
> A좌석을 예약하려는 사용자와 B좌석을 예약하려는 사용자가 서로 아무 관련이 없음에도, 모든 사용자가 한 줄로 서서 대기하므로 초당 처리량(TPS)이 곤두박질칩니다.

---

###  Good: `ConcurrentHashMap.putIfAbsent()`를 활용한 원자적 락 획득

`putIfAbsent(key, value)`는 **"키가 없을 때만 원자적으로 값을 넣는다"**를 단 1회의 CPU 명령 레벨(CAS, Compare-And-Swap) 수준으로 처리합니다.

```java
package com.example.reservation.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemorySeatLockService {

    // 좌석 번호 -> 락 점유 정보(사용자ID, 만료시각 등)
    private final ConcurrentHashMap<String, LockInfo> seatLocks = new ConcurrentHashMap<>();

    public record LockInfo(String userId, Instant lockedAt) {}

    /**
     * 좌석 선점 락 획득 시도
     * @return 락 획득 성공 시 true, 다른 사용자가 이미 점유 중이면 false
     */
    public boolean tryLockSeat(String seatNo, String userId) {
        LockInfo newLock = new LockInfo(userId, Instant.now());

        //  핵심: putIfAbsent의 반환값 명세를 활용!
        // - 키가 없었으면: 맵에 newLock을 넣고 "null"을 반환
        // - 키가 이미 있었으면: 기존에 존재하던 "기존의 값(existing)"을 반환
        LockInfo existing = seatLocks.putIfAbsent(seatNo, newLock);

        // existing이 null이라는 것은 "내가 최초로 값을 성공적으로 넣었다"는 것을 의미!
        boolean acquired = (existing == null);
        return acquired;
    }

    public void releaseSeat(String seatNo, String userId) {
        // 내가 잡은 락인 경우에만 안전하게 해제
        seatLocks.computeIfPresent(seatNo, (key, current) -> {
            if (current.userId().equals(userId)) {
                return null; // null을 반환하면 맵에서 해당 엔트리가 원자적으로 제거됨
            }
            return current; // 다른 사람이 점유 중이면 유지
        });
    }
}
```

### 💡 "왜 `existing == null`로 검사하는가?"
`putIfAbsent`의 자바 공식 명세(Javadoc)는 다음과 같습니다:
- **반환값**: *"해당 키에 이미 매핑되어 있던 이전 값, 또는 매핑이 없었으면 `null`"*

따라서 반환된 `existing` 변수가 `null`인지를 검사하는 것은 **"원자적으로 키가 없는 상태임을 확인하고 내 값을 성공적으로 선점했는가?"**를 100% 안전하게 판별하는 가장 우아한 자바의 표준 이디엄(Idiom)입니다.

---

## 4. 자주 밟는 지뢰와 베스트 프랙티스

### 💣 지뢰 1: `computeIfAbsent()` 내에서 무거운 작업(DB/네트워크 I/O) 수행 금지
```java
// ❌ 매우 위험한 코드
map.computeIfAbsent(ticketId, id -> {
    // ⚠️ computeIfAbsent는 해당 버킷(Bucket)의 노드에 락을 걸고 실행됩니다!
    return paymentGateway.callExternalRestApi(id); // 외부 통신이 3초 걸리면 다른 요청까지 멈춤!
});
```
- `ConcurrentHashMap`의 `compute`, `computeIfAbsent` 람다 블록 내부에서는 오직 **가벼운 메모리 내 객체 생성이나 간단한 계산만 수행**해야 합니다. 외부 I/O나 데이터베이스 쿼리를 던지면 심각한 스레드 경합과 데드락을 유발합니다.

### 💣 지뢰 2: 단일 서버 인메모리 락 vs 분산 서버(Scale-Out)의 한계
- `ConcurrentHashMap`은 **단일 JVM 인스턴스(단 1대의 서버) 내에서만 동시성을 보장**합니다.
- 만약 서비스를 로드밸런서 뒤에 2대 이상 띄우는 순간(Scale-out), 서버 A의 `ConcurrentHashMap`과 서버 B의 `ConcurrentHashMap`은 서로 전혀 알지 못하므로 이중 예약이 발생합니다.
- **실무 권장사항**:
  - 로컬 캐시, 애플리케이션 내부 메트릭 카운터, 임시 멱등성 버퍼: `ConcurrentHashMap` 사용
  - 다중 서버 환경의 티켓/좌석 재고 선점: **Redis 분산락(Redisson)** 또는 **데이터베이스 비관적 락(`SELECT ... FOR UPDATE`)**을 사용하십시오.

---

## 5. 핵심 한 줄 요약

> **멀티스레드 환경에서는 일반 `HashMap`을 절대 공유하지 말아야 하며, `ConcurrentHashMap.putIfAbsent()`의 반환값이 `null`인지 확인하는 패턴을 통해 원자적인 동시성 제어를 달성한다.**
