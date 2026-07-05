---
name: pr-review
description: chaeso-zip-server(Spring Boot 3 / Java 21) PR을 계층 아키텍처·정확성·보안·JPA/Flyway 관점에서 리뷰한다. 로컬에서 현재 diff를 점검하거나 CI(claude-review.yml)에서 PR 자동 리뷰의 정책 단일 소스로 사용한다.
---

# PR 리뷰 스킬 (chaeso-zip-server)

이 스킬은 **리뷰 정책의 단일 소스(single source of truth)** 다. 로컬에서 직접 호출하든
CI 파이프라인(`.github/workflows/claude-review.yml`)이 호출하든 동일한 기준으로 리뷰한다.
실행 방식(코멘트 게시 등)은 호출하는 쪽에서 지시하며, 이 문서는 **무엇을 어떤 기준으로 보는가**만 정의한다.

## 프로젝트 컨텍스트

- Spring Boot 3 / Java 21 / Gradle (`build.gradle`, 버전 카탈로그 `gradle/libs.versions.toml`)
- JPA(Hibernate) + PostgreSQL, 스키마 마이그레이션은 **Flyway** (`src/main/resources/db/migration`)
- 계층 아키텍처를 **ArchUnit**으로 강제: `src/test/java/chaeso/zip/server/architecture/LayerDependencyTest.java`
- 관측: Actuator + Micrometer/Prometheus + Sentry
- API 문서: springdoc-openapi, 테스트 문서: Spring REST Docs

## 리뷰 절차

1. **변경 범위 파악**: PR 제목/본문/연결 이슈를 읽고 의도를 이해한다. 그런 다음 diff 전체를 본다.
2. **맥락 읽기**: 변경된 파일뿐 아니라 인접 파일(호출부, 같은 패키지의 형제 클래스, 관련 테스트)을 읽어
   실제 동작과 규칙 위반 여부를 확인한다. **추측하지 말고 코드를 직접 확인**한다.
3. **체크리스트 적용**: 아래 항목을 순서대로 점검한다.
4. **심각도 분류 후 보고**: 확신이 있는 문제 위주로, 심각도와 함께 보고한다.

**관점**: 10년차 시니어 백엔드 엔지니어로서 판단한다.

- **근거 기반**: 일반론이 아니라 *이 저장소*의 실제 구조와 이미 자리잡은 컨벤션
  (ArchUnit 계층 규칙, DTO 배치, 공통 응답/예외 처리, Flyway 관행, 패키지 구조)을 기준으로 본다.
  지적할 때는 근거가 되는 **파일·규칙·기존 패턴을 명시**한다.
- **유지보수 관점**: 결합도, 중복, 숨은 복잡도, 6개월 뒤 이 코드를 유지하는 비용을 함께 따진다.

## 리뷰 체크리스트

### 1. 계층 아키텍처 (ArchUnit 규칙 — 위반은 빌드 실패로 직결)

의존 방향: **presentation → application → domain** (역방향 금지).

- `@RestController` → `..presentation..` 패키지에만
- `@Service` → `..application..` 패키지에만
- `@Entity` → `..domain..` 패키지에만, `*Repository` → `..domain..`
- DTO 위치: `*Request` → presentation, `*Command`/`*Response` → application, 공통 응답 래퍼는 `..common..`
- presentation은 **엔티티를 직접 참조 금지** → application DTO를 거친다
- domain은 application/presentation 및 `org.springframework.web..`에 의존 금지

> 위 규칙 위반은 `./gradlew build`에서 ArchUnit 테스트로 잡힌다. 리뷰에서 미리 지적해 빌드 실패를 예방한다.

### 2. 정확성 / 버그

- null 처리, `Optional` 오용(`get()` 남용), 경계 조건, off-by-one
- 분기 누락, 잘못된 비교(`equals` vs `==`), 부호/타입 변환 실수
- 컬렉션 변경 중 순회, 의도치 않은 가변 상태 공유
- 예외를 삼키거나(`catch` 후 무시) 너무 광범위하게 잡는지

### 3. JPA / 영속성

- **N+1 쿼리**: 연관관계 지연 로딩을 루프에서 접근 → fetch join / `@EntityGraph` / 배치 사이즈 검토
- 연관관계 기본 fetch 전략(`@ManyToOne`/`@OneToOne` 기본 EAGER) 명시 여부
- `@Transactional` 경계: 읽기 전용은 `readOnly = true`, 쓰기 작업 누락 여부, 클래스/메서드 범위
- 영속성 컨텍스트 밖에서 지연 로딩 접근(LazyInitializationException) 위험
- 양방향 연관관계의 연관관계 편의 메서드 / 무한 순환(`toString`, 직렬화) 위험
- 엔티티 PK는 **UUID 타입** (`BaseEntity` 참조). `Long`/`IDENTITY` 전략 사용은 컨벤션 위반
- `equals`/`hashCode` 구현 적절성

### 4. Flyway 마이그레이션

- 새 마이그레이션은 **추가만**: 이미 적용된 `V*__*.sql` 파일은 수정/삭제 금지(체크섬 깨짐)
- 버전 번호 충돌/건너뜀 없는지, 네이밍 컨벤션(`V{n}__{설명}.sql`) 준수
- 운영 데이터에 파괴적 변경(컬럼 삭제/타입 변경/NOT NULL 추가) 시 데이터 마이그레이션·기본값 동반 여부
- 큰 테이블 인덱스 추가 등 락/다운타임 위험

### 5. 보안

- 입력 검증(`@Valid`/Bean Validation), 신뢰 경계에서의 검증 누락
- 인가/인증 우회, 권한 체크 누락, IDOR(다른 사용자 리소스 접근)
- SQL 인젝션(문자열 연결 쿼리), 민감정보 로깅/응답 노출
- 비밀값 하드코딩, 설정/시크릿이 코드·로그에 노출되는지

### 6. API / 예외 처리

- 응답은 공통 래퍼(`..common..response..`) 일관 사용, HTTP 상태코드 적절성
- 도메인/애플리케이션 예외가 공통 예외 처리(`@RestControllerAdvice`)로 일관되게 매핑되는지
- API 변경의 하위 호환성, springdoc 문서/REST Docs 스니펫 영향

### 7. 테스트

- 새 로직에 대한 테스트 존재 여부, 의미 있는 단언(assertion)인지
- 경계/예외 케이스 커버, 테스트가 구현 세부에 과결합되지 않았는지

### 8. 컨벤션 / 가독성

- 네이밍, 불필요한 복잡도, 중복(기존 유틸/공통 코드 재사용 가능 여부)
- Lombok 사용 적절성, 매직 넘버/하드코딩 상수화
- 죽은 코드, 디버그 로그/주석 잔여물

## 심각도 기준

- **🔴 Blocker**: 머지 시 버그/장애/보안 문제 또는 빌드(ArchUnit/테스트) 실패 — 반드시 수정
- **🟠 Major**: 명확한 결함이나 위험. 수정 강력 권장
- **🟡 Minor**: 개선하면 좋은 품질/가독성 문제
- **⚪ Nit**: 취향/사소한 제안 (선택)

## 출력 원칙

- **한국어**로, 간결하고 구체적으로. 각 지적에 **이유**와 **권장 수정 방향**(가능하면 코드 예시)을 함께 제시한다.
- 위치는 `파일:라인`으로 명확히 한다.
- **노이즈 최소화**: 확신이 낮으면 단정하지 말고 질문 형태로. 사소한 취향(Nit)은 과하게 쏟아내지 않는다.
- 문제가 없으면 "유의미한 문제를 찾지 못했습니다"라고 솔직하게 보고한다. 억지로 지적을 만들지 않는다.
- **전체 요약 + 심각도별 항목**으로 구조화한다. 가능하면 라인별 지적은 인라인으로 남긴다.
