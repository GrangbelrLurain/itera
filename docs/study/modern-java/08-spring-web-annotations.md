# 08. Spring Web REST API 핵심 애노테이션

> **핵심 주제**: HTTP 요청(Path, Query, Body)을 자바 타입과 DTO record로 안전하게 바인딩하는 Spring Web 핵심 애노테이션의 조합과 실무 모범 사례.

---

## 1. 왜 이 문법/패턴이 필요한가? (배경과 기존의 문제)

과거 Java 서블릿(Servlet) 환경에서 REST API를 개발하려면 모든 HTTP 요청을 원시 문자열로 직접 꺼내서 파싱해야 했습니다.

```java
// 과거 서블릿 방식의 끔찍한 파싱 코드
public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String idParam = req.getParameter("ticketId");
    if (idParam == null) {
        resp.sendError(400, "ticketId is required");
        return;
    }
    Long ticketId = Long.parseLong(idParam); // NumberFormatException 위험!
    // JSON 문자열을 BufferedReader로 읽어서 ObjectMapper로 수동 역직렬화...
}
```

이 방식은 수동 타입 변환, null 체크, HTTP 응답 헤더 설정 등 반복적이고 에러가 발생하기 쉬운 코드가 전체의 80%를 차지했습니다.

Spring Web(Spring MVC)은 **선언적 애노테이션(Declarative Annotations)**을 통해 이 과정을 완전히 자동화했습니다. 개발자는 비즈니스 입력값의 타입(`Long`, `record`, `int` 등)만 정의하면, 스프링이 요청 파싱, 타입 캐스팅, 유효성 검증(Validation)까지 한 번에 처리해 줍니다.

---

## 2. 타 언어(특히 Express / NestJS)와의 1:1 비교

TypeScript 생태계의 Express 및 NestJS와 비교하면 스프링의 애노테이션 체계는 NestJS와 매우 높은 유사성을 보입니다.

| 역할 | Express | NestJS | Spring Web (Spring Boot) |
| :--- | :--- | :--- | :--- |
| **컨트롤러 선언** | `router = Router()` | `@Controller('tickets')` | **`@RestController` + `@RequestMapping("/tickets")`** |
| **경로 변수 (Path)** | `req.params.id` | `@Param('id') id: string` | **`@PathVariable Long id`** |
| **쿼리 파라미터** | `req.query.page` | `@Query('page') page: number`| **`@RequestParam(defaultValue = "1") int page`** |
| **요청 본문 (JSON)** | `req.body` | `@Body() dto: CreateDto` | **`@RequestBody @Valid CreateRequest request`** |
| **상태 코드 지정** | `res.status(201)` | `@HttpCode(201)` | **`@ResponseStatus(HttpStatus.CREATED)`** |
| **응답 헤더/상태 제어**| `res.status().json()` | `res.status().send()` | **`ResponseEntity.created(uri).body(data)`** |

NestJS가 TypeScript의 데코레이터를 이용해 스프링의 구조를 모방하여 설계되었기 때문에, NestJS를 경험한 개발자라면 스프링 웹 컨트롤러 코드를 거의 직관적으로 이해할 수 있습니다.

---

## 3. 실전 코드 예제 (Good vs Bad)

### ❌ Bad: HttpServletRequest 수동 제어 및 상태 코드 누락
```java
@Controller // ⚠️ @RestController가 아님!
@RequestMapping("/api/tickets")
public class BadTicketController {

    @PostMapping("/create")
    public void createTicket(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String title = request.getParameter("title");
        String priceStr = request.getParameter("price");
        
        // ❌ 수동 파싱 및 검증
        if (title == null || priceStr == null) {
            response.setStatus(400);
            return;
        }
        
        // ❌ 생성 성공 시 201이 아닌 기본 200 OK로 반환되거나 뷰 템플릿 탐색 에러 발생
        response.getWriter().write("{\"success\":true}");
    }
}
```

---

###  Good: 모던 자바 21 `record`와 Spring Web RESTful 컨트롤러

```java
package com.example.ticket.controller;

import com.example.ticket.dto.TicketCreateRequest;
import com.example.ticket.dto.TicketResponse;
import com.example.ticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController // 1. @Controller + @ResponseBody의 결합: 모든 메서드의 반환값을 JSON으로 직렬화
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    // 2. 단건 조회: @PathVariable 활용
    // GET /api/v1/tickets/101
    @GetMapping("/{ticketId}")
    public TicketResponse getTicket(@PathVariable Long ticketId) {
        return ticketService.findById(ticketId);
    }

    // 3. 목록 페이징 및 필터링: @RequestParam 활용 (기본값 설정)
    // GET /api/v1/tickets?status=AVAILABLE&page=1&size=20
    @GetMapping
    public List<TicketResponse> searchTickets(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ticketService.search(status, page, size);
    }

    // 4. 생성 요청: @RequestBody + @Valid + 201 Created 응답
    // POST /api/v1/tickets
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // HTTP 201 응답 상태 코드 명시
    public TicketResponse createTicket(@RequestBody @Valid TicketCreateRequest request) {
        return ticketService.create(request);
    }

    // 5. 생성 후 Location 헤더를 함께 반환해야 하는 REST 표준 준수 시
    @PostMapping("/with-location")
    public ResponseEntity<TicketResponse> createWithLocation(@RequestBody @Valid TicketCreateRequest request) {
        TicketResponse created = ticketService.create(request);
        URI location = URI.create("/api/v1/tickets/" + created.ticketId());
        return ResponseEntity.created(location).body(created);
    }

    // 6. 삭제 요청: 204 No Content
    // DELETE /api/v1/tickets/101
    @DeleteMapping("/{ticketId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTicket(@PathVariable Long ticketId) {
        ticketService.delete(ticketId);
    }
}
```

```java
// 요청 DTO: Bean Validation(@NotNull, @NotBlank, @Min) 결합
package com.example.ticket.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TicketCreateRequest(
    @NotBlank(message = "공연명은 필수입니다.")
    String title,

    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 1000, message = "티켓 가격은 최소 1,000원 이상이어야 합니다.")
    BigDecimal price,

    @Min(value = 1, message = "수량은 1개 이상이어야 합니다.")
    int quantity
) {}
```

---

## 4. 자주 밟는 지뢰와 베스트 프랙티스

### 💣 지뢰 1: `@Controller` vs `@RestController`를 혼동하여 발생하는 404
- `@Controller`는 원래 전통적인 웹 뷰(JSP, Thymeleaf, Freemarker 등 HTML 파일)의 이름을 반환하기 위한 애노테이션입니다.
- 메서드에서 `return new TicketResponse(...)`를 반환했는데 클래스가 `@Controller`로 되어 있으면, 스프링은 `TicketResponse`라는 이름의 HTML 템플릿 파일을 찾으려다 **`Circular view path` 또는 404 에러**를 뿜습니다.
- **REST API 백엔드를 작성할 때는 반드시 `@RestController`**를 사용해야 하며, 이는 내부에 `@ResponseBody`가 기본 탑재되어 있어 객체를 Jackson JSON으로 바로 변환해 줍니다.

### 💣 지뢰 2: `@PathVariable` 파라미터 이름 생략 시 500 에러
```java
// ⚠️ 과거 자바 컴파일러 환경에서의 위험:
@GetMapping("/{ticketId}")
public TicketResponse getTicket(@PathVariable Long ticketId) { ... }
```
- 자바 컴파일러가 바이트코드를 생성할 때 `-parameters` 플래그(메서드 매개변수 이름을 보존하는 옵션)가 꺼져 있으면, 스프링은 URL의 `{ticketId}`와 변수명 `ticketId`가 같은지 알지 못해 런타임에 에러를 발생시킵니다.
- 최신 빌드 도구(Gradle/Maven)는 기본적으로 `-parameters`를 켜두지만, 안전성을 위해 변수명이 다르거나 레거시 환경인 경우 `@PathVariable("ticketId") Long id` 처럼 명시해 주는 습관이 안전합니다.

### 💣 지뢰 3: `@RequestBody`에 역직렬화(Deserialization) 에러
- 클라이언트가 보낸 JSON 문자열을 자바 객체로 변환할 때 스프링은 기본 라이브러리로 **Jackson**을 사용합니다.
- 일반 클래스로 DTO를 만들 때는 **기본 생성자(No-args constructor)가 없으면** Jackson이 객체를 인스턴스화하지 못해 `InvalidDefinitionException`이 터집니다.
- **최신 권장**: 본문 예제처럼 **Java 21 `record`**를 DTO로 사용하면 Jackson이 Canonical Constructor를 자동으로 인식하므로 기본 생성자 없이도 깔끔하게 JSON 매핑이 완료됩니다.

### 💣 지뢰 4: 올바른 HTTP Status Code 매핑 원칙
- **리소스 생성 성공**: `200 OK`가 아니라 **`201 Created`** (`@ResponseStatus(HttpStatus.CREATED)`)
- **수정 / 조회 성공**: `200 OK`
- **삭제 성공 (반환 본문 없음)**: `200 OK`에 빈 JSON을 주는 것보다 **`204 No Content`**
- **클라이언트 입력값 검증 실패**: `400 Bad Request`
- **조회 대상 리소스 부재**: `404 Not Found`
- **동시성 충돌 / 중복 생성 시도**: `409 Conflict`

---

## 5. 핵심 한 줄 요약

> **`@RestController`와 Java 21 `record` DTO를 `@Valid`와 함께 조합하면, 수동 파싱 없이 타입 세이프하고 선언적인 RESTful API를 가장 간결하게 완성할 수 있다.**
