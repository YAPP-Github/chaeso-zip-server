# OpenAPI / API Client 가이드

프론트는 **OpenAPI Generator**로 API client를 자동 생성합니다. 그 입력이 되는 OpenAPI 스펙은
백엔드 코드(어노테이션)에서 자동 생성됩니다. 아래 컨벤션만 지키면 스펙 품질이 유지됩니다.

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

### 요청 DTO는 검증 어노테이션을 단다
`@NotBlank`, `@Size`, `@NotNull` 등은 자동으로 `required`/`maxLength` 등으로 스펙에 반영됩니다.

```java
@Schema(example = "채소", requiredMode = Schema.RequiredMode.REQUIRED)
@NotBlank @Size(max = 100)
String name
```

### enum은 Java enum으로 둔다
String 상수 대신 Java `enum`을 쓰면 스펙에 `enum` 값이 그대로 노출되어 프론트가 타입으로 분기할 수 있습니다.

### 에러 응답
- 어디서나 날 수 있는 **500은 자동으로 모든 API에 부착**됩니다 (`CommonResponsesCustomizer`).
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
  "data": null,
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
  "data": null,
  "error": {
    "code": "AUTH-002",
    "message": "이미 사용 중인 이메일입니다.",
    "fieldErrors": []
  }
}
```

### 참고할 정답 예시
`sample` 도메인(`SampleController`, `SampleResponse`, `CreateSampleRequest`)이 위 컨벤션을 모두 반영한
레퍼런스입니다. 새 도메인은 이 구조를 따라가면 됩니다.

---

## 2. 프론트 전달용 — 무엇을 했고 어떻게 최신화하는가

### 백엔드에서 한 작업
- 모든 응답/에러 스키마에 `required`·`nullable`·타입을 명시해 **codegen 타입이 정확**하도록 정리.
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