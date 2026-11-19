# AGENTS.md

이 문서는 이 레포지토리에서 작업하는 AI 에이전트(Claude Code, Codex, Cursor 등)가 따라야 할 규칙입니다.

## 1. 프로젝트 개요

- 문서 자동 정리 서비스 **가치(chagok)** 의 백엔드 서버
- 대회 제출용 프로젝트
- 기술 스택
  - Java 21, Spring Boot 4.1.2, Gradle 9.9.1
  - Spring Data JPA, MySQL
  - Spring Security, Spring RestClient
  - Lombok
- 기본 패키지: `gachi.chagok_server`

## 2. 언어

- 답변, 주석, 커밋 메시지는 **한국어**로 작성한다.
- 클래스, 메서드, 변수 같은 코드 식별자는 영어로 작성한다.

## 3. 빌드·검증

```bash
./gradlew build   # 빌드
./gradlew test    # 테스트
```

- 코드를 변경한 뒤에는 반드시 `./gradlew test`를 실행해 통과 여부를 확인하고, 결과를 보고한다.
- 테스트가 실패하면 실패 내용을 그대로 보고한다. 통과한 것처럼 말하지 않는다.

## 4. 패키지 구조

도메인형 구조를 사용한다.

```
gachi.chagok_server
├── domain
│   └── {도메인명}              # 예: document, user
│       ├── controller
│       ├── service
│       ├── repository
│       ├── entity
│       └── dto
└── global
    ├── config
    ├── exception
    ├── common
    └── security
```

- 특정 도메인에 속한 코드는 `domain/{도메인명}` 아래에 둔다.
- 여러 도메인이 함께 쓰는 코드(설정, 예외, 공통 응답, 보안)는 `global` 아래에 둔다.

## 5. 코딩 컨벤션

### 의존성 주입
- 생성자 주입만 사용한다(`@RequiredArgsConstructor`).
- 필드에 `@Autowired`를 붙이는 방식은 사용하지 않는다.

### 엔티티
- `@Setter`, `@Data`를 사용하지 않는다. 상태 변경은 의미 있는 이름의 메서드로 한다.
- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 선언한다.
- 연관관계의 fetch 전략은 모두 `FetchType.LAZY`로 한다.
- 생성·수정 시각이 필요한 엔티티는 `BaseTimeEntity`(`createdAt`, `updatedAt`)를 상속한다.

### DTO
- 요청·응답 DTO는 `record`로 작성한다.
- 엔티티를 컨트롤러 응답으로 직접 반환하지 않는다. 반드시 DTO로 변환한다.

### 트랜잭션
- 서비스 클래스에 `@Transactional(readOnly = true)`를 선언한다.
- 데이터를 변경하는 메서드에만 `@Transactional`을 추가로 붙인다.

## 6. API 규칙

- URL은 `/api/v1/...` 형식을 사용한다.
- 리소스 이름은 복수형 kebab-case로 쓴다. 예: `/api/v1/documents`, `/api/v1/document-folders`
- 모든 응답은 공통 래퍼 `ApiResponse<T>`로 감싼다.
  - 필드: `success`, `data`, `error`
- 예외 처리
  - 비즈니스 예외는 `BusinessException`을 던진다.
  - 에러 코드와 메시지는 `ErrorCode` enum에 정의한다.
  - 예외는 `@RestControllerAdvice` 전역 핸들러 한곳에서 처리한다.
- 요청 값 검증은 Bean Validation(`@Valid`, `@NotBlank` 등)으로 한다.
  - 현재 `spring-boot-starter-validation` 의존성이 없으므로, 처음 필요할 때 추가한다(7번 규칙의 사전 확인 대상).

## 7. 테스트

- 계층별 테스트 방식
  - 서비스: Mockito 기반 단위 테스트
  - 레포지토리: `@DataJpaTest`
  - 컨트롤러: `@WebMvcTest`
- 테스트 이름은 `@DisplayName`에 한글로 작성한다.
- 테스트 본문은 `// given`, `// when`, `// then` 구조로 작성한다.
- DB가 필요한 테스트는 Testcontainers로 MySQL을 띄워 실행한다.
  - 현재 Testcontainers 의존성이 없으므로, 처음 필요할 때 추가한다.

## 8. 설정·보안

- DB 비밀번호, API 키 같은 비밀값은 절대 커밋하지 않는다.
  - 설정 파일에는 `${ENV_VAR}` 형식으로 주입한다.
  - `application-local.yaml`은 `.gitignore`에 포함한다.
- 프로파일은 `local`, `dev`, `prod`를 사용한다.
- `prod` 프로파일의 `spring.jpa.hibernate.ddl-auto`는 `validate` 또는 `none`만 허용한다.

## 9. Git

- 이슈와 PR은 사용하지 않는다. **`main` 브랜치에 직접 커밋한다.**
- 커밋 메시지 형식: `type: 한글 설명`

  | type | 용도 |
  | --- | --- |
  | `feat` | 새 기능 |
  | `fix` | 버그 수정 |
  | `refactor` | 동작 변경 없는 구조 개선 |
  | `docs` | 문서 |
  | `test` | 테스트 추가·수정 |
  | `chore` | 빌드·설정 등 기타 |

  예: `feat: 문서 업로드 API 추가`
- 커밋은 하나의 목적 단위로 나눈다.
- 에이전트는 사용자가 요청할 때만 커밋하거나 push한다.

## 10. 에이전트 행동 규칙

- `build.gradle`에 의존성을 추가하기 전에 사용자에게 먼저 확인한다.
- 요청 범위를 벗어난 리팩터링이나 포맷 변경을 하지 않는다.
- 엔티티나 DB 스키마를 변경하면 변경 내용을 따로 알린다.
- 요구사항이 모호하면 추측하지 말고 질문한다.
