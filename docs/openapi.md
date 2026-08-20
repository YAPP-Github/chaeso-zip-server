# OpenAPI / API Client 가이드

프론트는 **openapi-typescript(openapi-ts)** 또는 **OpenAPI Generator**로 API 타입과 client를 자동 생성합니다.
그 입력이 되는 OpenAPI 스펙은 백엔드 코드(어노테이션)에서 자동 생성됩니다.
아래 컨벤션만 지키면 스펙 품질이 유지됩니다.

---

## 1. 백엔드 개발 체크리스트

새 API / DTO를 추가·수정할 때 아래를 지켜주세요.

### operationId는 명시한다 (필수)
메서드명이 그대로 operationId가 되어, 다른 컨트롤러와 겹치면 codegen에서 `create_1`처럼 불안정해집니다.

```java
@Operation(operationId = "createSample", summary = "샘플 생성")  // 도메인+동작
```

> CI의 `OpenApiContractTest`가 operationId 중복을 자동으로 잡습니다.

### 응답 DTO 필드는 required를 명시한다 (중요)
명시하지 않으면 프론트 타입이 전부 optional(`id?: number`)로 생성됩니다. 항상 존재하는 필드는 `REQUIRED`로.

```java
@Schema(description = "샘플 식별자", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
Long id,
```

### 값이 없을 수 있는 응답 필드는 `nullable = true`로 표현한다 (필수)
응답에서 키를 생략하지 않습니다. 값이 없으면 **키는 그대로 실리고 값만 `null`** 입니다.
그래야 스펙(`nullable`)과 실제 응답이 같아지고, 프론트가 "키가 없는 경우"와 "값이 null인 경우"를
따로 처리하지 않아도 됩니다.

```java
// 항상 실리고 값이 없으면 null → 프론트 타입은 iconUrl: string | null
@Schema(description = "심볼 로고 이미지 URL",
    requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
String iconUrl,
```

- **응답 DTO**: 값이 없을 수 있으면 `REQUIRED` + `nullable = true`. 키가 항상 있으니 `required`에 남습니다.
- **요청 DTO**: 생략해도 되는 입력은 `NOT_REQUIRED`(+ null도 허용하면 `nullable = true`).
  요청은 키를 안 보낼 수 있으므로 응답과 규칙이 다릅니다.
- 빈 목록은 `null`이 아니라 `[]`로 줍니다.
- `@JsonInclude(NON_NULL)`을 붙이거나 `spring.jackson.default-property-inclusion`을 건드리지 마세요.
  null을 지우면 스펙과 응답이 어긋납니다.

`$ref`로 참조되는 객체 필드(예: `Prefill`)에도 같은 규칙을 씁니다. 다만 OpenAPI 3.0은 `$ref` 옆에
다른 키워드를 둘 수 없어, swagger-core가 `nullable`을 참조 대상 컴포넌트로 밀어 넣습니다(그러면 그
스키마를 쓰는 모든 자리가 nullable이 됩니다). `NullableSchemaConfig`가 이런 필드를
`allOf: [$ref] + nullable`로 바꾸고 컴포넌트에 새어 들어간 `nullable`을 걷어냅니다. 작성자는 평소대로
`nullable = true`만 달면 됩니다.

`ApiResponse` 래퍼의 `data`/`error`/`code`는 래퍼 종류마다 채워지는 칸이 달라
`ResponseWrapperSchemaCustomizer`가 스키마별로 `required`/`nullable`을 붙입니다. 필드에 직접 달지 마세요.

> `OpenApiContractTest`가 nullable 표기와 컴포넌트 오염 여부를 검사합니다.

### 요청 DTO는 검증 어노테이션을 단다
`@NotBlank`, `@Size`, `@NotNull` 등은 자동으로 `required`/`maxLength` 등으로 스펙에 반영됩니다.

```java
@Schema(example = "채소", requiredMode = Schema.RequiredMode.REQUIRED)
@NotBlank @Size(max = 100)
String name
```

### enum은 Java enum으로 둔다
String 상수 대신 Java `enum`을 쓰면 스펙에 `enum` 값이 그대로 노출되어 프론트가 타입으로 분기할 수 있습니다.

### 성공 응답은 `ApiResponse<T>`의 `T`까지 연결한다

실제 응답이 `ApiResponse<TokenResponse>`여도 성공 응답 annotation을 raw `ApiResponse.class`로
지정하면 제네릭 타입 `T`가 사라집니다. 이 경우 openapi-typescript가 `data`를 `unknown`으로 생성합니다.

```java
// 잘못된 예: data의 실제 타입을 알 수 없다.
@ApiResponse(responseCode = "200",
    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
ApiResponse<TokenResponse> signupGoogle(
    @Valid @RequestBody GoogleSignupRequest request);
```

examples와 메서드 반환형을 함께 사용할 때는
@Schema(implementation = ApiResponse.class)를 사용하지 않고,
`useReturnTypeSchema = true`로 반환형 schema를 병합합니다.

```java
@ApiResponse(
    responseCode = "200",
    description = "가입 성공, 토큰 발급",
    useReturnTypeSchema = true,
    content = @Content(
        examples = @ExampleObject(name = "SIGNUP_SUCCESS", value = SIGNUP_SUCCESS_EXAMPLE)
    )
)
ApiResponse<TokenResponse> signupGoogle(
    @Valid @RequestBody GoogleSignupRequest request);
```

생성된 OpenAPI 구조가 만족해야 할 구체적인 조건은 아래 "OpenAPI 계약 테스트" 항목을 참고하세요.

### 에러 응답
- 어디서나 날 수 있는 **500은 자동으로 모든 API에 부착**됩니다 (`CommonResponsesCustomizer`).
- `@SecurityRequirement(name = "bearerAuth")`가 선언된 API에는 공통 **401(C-004)이 자동으로 부착**됩니다.
   endpoint 전용 401 응답이 있으면 직접 선언한 응답이 우선합니다.
- 도메인별 4xx(400/404 등)는 컨트롤러에 직접 선언합니다.
- 모든 에러 응답은 공통 래퍼 `ApiResponse<Void>` 형태입니다.
- 프론트는 비즈니스 에러를 `error.code` 기준으로 매핑합니다. 예: `AUTH-002`.
- 입력값 검증 실패는 `C-001`과 `error.fieldErrors[]`로 내려갑니다.
- 서버 `error.message`와 `fieldErrors[].reason`은 fallback 문구입니다. 최종 사용자 표시 문구는 프론트에서 `error.code` 또는 `fieldErrors[].field` 기준으로 매핑할 수 있습니다.
- 각 API는 발생 가능한 4xx 응답과 대표 `@ExampleObject`를 선언합니다.

```java
@ApiResponses({
    @ApiResponse(responseCode = "404", description = "샘플 없음",
        content = @Content(schema = @Schema(implementation = ApiResponse.class)))
})
```

검증 실패 예시:

```json
{
  "success": false,
  "error": {
    "code": "C-001",
    "message": "입력값이 올바르지 않습니다.",
    "fieldErrors": [
      {
        "field": "email",
        "value": "",
        "reason": "이메일을 입력해 주세요"
      }
    ]
  }
}
```

비즈니스 에러 예시:

```json
{
  "success": false,
  "error": {
    "code": "AUTH-002",
    "message": "이미 사용 중인 이메일입니다.",
    "fieldErrors": []
  }
}
```

### 본문이 없는 응답

메서드 반환형이 `ResponseEntity<ApiResponse<T>>`여도 204 응답은 본문이 없습니다.
`content`를 생략하면 Springdoc이 반환형 schema를 204에도 자동으로 넣을 수 있으므로 빈 `@Content`를 명시합니다.

```java
@ApiResponse(responseCode = "204", description = "조회 결과 없음", content = @Content)
```

### 참고할 정답 예시
`sample` 도메인(`SampleController`, `SampleResponse`, `CreateSampleRequest`)이 위 컨벤션을 모두 반영한
레퍼런스입니다. 새 도메인은 이 구조를 따라가면 됩니다.

### OpenAPI 계약 테스트
`OpenApiContractTest`는 `/v3/api-docs`를 직접 읽어 API client 생성에 필요한 계약을 검증합니다.

```bash
./gradlew test --tests chaeso.zip.server.docs.OpenApiContractTest
```

- 본문이 있는 2xx 응답은 raw `ApiResponse`가 아닌 구체 wrapper를 참조해야 한다
- void가 아닌 wrapper의 `data`에는 구체 `$ref`, 타입 또는 배열 항목 schema가 있어야 한다
- 페이지 응답의 `data.content.items`도 구체 schema를 참조해야 한다
- examples가 있는 응답에는 schema도 함께 있어야 한다
- 모든 응답 본문은 `application/json`이어야 하며 `*/*`가 남으면 실패한다
- `SecurityConfig` 인증 대상 API는 `bearerAuth`를 선언하고, 401에는 성공 DTO가 아닌 공통 오류 Schema를 사용해야 한다
- 204 응답에는 `content/schema`가 없어야 한다
- 값이 없을 수 있는 응답 필드는 `nullable`로 노출하고, 그 `nullable`이 공유 컴포넌트로 새지 않아야 한다

---

## 2. 프론트 전달용 — 무엇을 했고 어떻게 최신화하는가

### 백엔드에서 한 작업
- 모든 응답/에러 스키마에 `required`·`nullable`·타입을 명시해 **codegen 타입이 정확**하도록 정리.
  값이 없는 필드도 키는 항상 실리고 값만 `null`입니다. 타입은 `field: T | null`로 생성됩니다.
- 공통 응답 래퍼 `ApiResponse<T>` / 에러 `ErrorResponse` 구조를 스펙에 노출.
  - 성공: `ApiResponseSampleResponse`, 목록: `ApiResponseListSampleResponse` 형태로 타입 생성됨.
- operationId를 `도메인+동작`으로 안정화 (예: `createSample`, `getSampleById`).
- 공통 에러 응답(500) 및 status code 문서화.

### 스펙을 받는 곳 (codegen 입력)
**개발 서버**의 엔드포인트를 직접 입력으로 사용합니다. (CLI fetch라 CORS 불필요)
보안상 **운영(prod)에서는 비공개**이며, 개발 서버에서만 열립니다.

```
스펙(JSON):  http://<개발 서버 호스트>/v3/api-docs
문서(UI):    http://<개발 서버 호스트>/swagger-ui/index.html
```

> 노출 여부는 `SWAGGER_ENABLED` 환경변수로 제어합니다 (기본 `false`).
> 개발 서버 `.env` 에 `SWAGGER_ENABLED=true` 를 두면 노출되고, 운영은 미설정(=비공개)으로 둡니다.
> 로컬은 `docker-compose.yml` 에서 기본 `true`, IDE 직접 실행 시 `SWAGGER_ENABLED=true` 지정.

openapi-typescript 예시:
```bash
npx openapi-typescript \
  http://<개발 서버 호스트>/v3/api-docs \
  -o ./src/api/schema.d.ts
```

OpenAPI Generator 예시:
```bash
openapi-generator-cli generate \
  -i http://<배포 호스트>/v3/api-docs \
  -g typescript-axios \
  -o ./src/api
```

### 최신 반영 방법
- 흐름: **BE를 `main`에 배포 → `/v3/api-docs` 자동 갱신 → 프론트가 위 명령으로 재생성.**
- "배포된 것 = 최신 스펙"입니다. 별도 파일/버전 관리가 없으므로, **BE 배포 후 재-codegen** 규칙만 지키면 됩니다.