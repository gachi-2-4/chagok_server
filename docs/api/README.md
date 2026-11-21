# 차곡 API 명세서

> 원본: 노션 「TEAM 가치 > API명세서」 (2026-11-21 기준)
>
> APP ↔ BE API 명세입니다. 화면 번호(A01, U04, 04–16 등)는 피그마 화면 이름입니다. BE ↔ AI 서버 API(X-API-Key·X-Internal-Key 인증)도 각 도메인 문서 안에 함께 있습니다.

## 문서 구성

### 공통

| 문서 | 내용 |
| --- | --- |
| [00-common.md](00-common.md) | 공통 규칙: 인증, 형식, 에러 응답, 페이지네이션 |
| [01-ai-integration.md](01-ai-integration.md) | BE ↔ AI 서버 연동: 호출 관계, 내부 API, 규칙, AI 명세에 요청할 것 |
| [02-enums.md](02-enums.md) | 코드표 (Enum) |
| [03-screen-map.md](03-screen-map.md) | 화면별 API, 기획 흐름 기준 API |

### 도메인별

| 문서 | 노션 DB | API 수 | 경로 |
| --- | --- | --- | --- |
| [auth.md](auth.md) | AUTH | 10 | `/auth/**` |
| [user.md](user.md) | USER | 6 (내부 API 1 포함) | `/user/**` |
| [box.md](box.md) | DOCUMENT (분리) | 5 | `/boxes/**` |
| [document.md](document.md) | DOCUMENT | 12 (AI 서버 API 2 포함) | `/documents/**`, `/ai/documents/**` |
| [comparison.md](comparison.md) | COMPARISON | 4 (AI 서버 API 2 포함) | `/comparisons/**`, `/ai/comparisons/**` |
| [chat.md](chat.md) | CHAT | 7 (AI 서버 API 2 포함) | `/chats/**`, `/ai/chat(s)/**` |
| [deadline.md](deadline.md) | NOTIFICATION (분리) | 7 | `/deadlines/**`, `/reminders/**` |
| [home.md](home.md) | NOTIFICATION (분리) | 1 | `/home` |
| [notification.md](notification.md) | NOTIFICATION | 7 | `/notifications/**` |

합계 59개 API (노션 각 DB의 행 수와 같음: AUTH 10 + USER 6 + DOCUMENT 17 + COMPARISON 4 + CHAT 7 + NOTIFICATION 15). 노션의 "템플릿" DB(`GET /api/template`)는 페이지 작성용 양식이라 옮기지 않았습니다.

노션 DB와 문서가 다른 곳:

- DOCUMENT DB → `box.md`(박스) + `document.md`(서류)로 나눔
- NOTIFICATION DB → `deadline.md`(기한·기한 알림) + `home.md`(메인 화면) + `notification.md`(알림 목록·설정·푸시 토큰)로 나눔

서버 패키지 구조(`domain/{도메인명}`)에 맞추기 위해 나눴고, 각 문서 머리에 원래 노션 DB를 적어 두었습니다.

## 각 API 문서 형식

노션 페이지 구조를 그대로 따릅니다.

1. 개요 (화면 포함)
2. 기술 설명/요구 사항
3. Request: Header / Query Parameter / Path Variable / Body
4. Response: 성공 응답 예시·필드 표, 에러 응답 예시

노션에서 비어 있던 표(값이 `-`뿐인 Query Parameter·Path Variable)는 "없음"으로 적었습니다. 노션 원본끼리 서로 맞지 않는 부분은 지우거나 고치지 않고, 그 자리에 `> 참고:`로 표시했습니다.

## 구현 전에 확인할 것

### 레포 규칙(AGENTS.md)과 노션 명세가 다른 부분

| 항목 | AGENTS.md | 노션 명세 |
| --- | --- | --- |
| URL 접두사 | `/api/v1/...` | 접두사 없이 `/auth/login`, `/documents` 등 |
| 응답 래퍼 | 모든 응답을 `ApiResponse<T>`(`success`, `data`, `error`)로 감쌈 | 성공은 데이터 그대로, 에러는 `{ "code", "message" }` (AI 서버와 같은 형식) |
| 리소스 이름 | 복수형 kebab-case | `/user/me`(단수)를 씀. 본문 일부는 `/users/me`(복수)로 적혀 있음 |

### 노션 원본 안에서 서로 다른 부분 (각 문서에 `> 참고:`로 표시)

| 문서 | 내용 |
| --- | --- |
| [user.md](user.md) | 사용자 정보 조회 내부 API 경로 `/user/{userId}`(빨간색 표시) vs `/internal/users/{userId}/profile` |
| [document.md](document.md) | `analysisStep` 값 목록이 코드표와 서류 상세 조회에서 다름 (`UPLOADED`, `LEGAL_CHECK` 유무) |
| [document.md](document.md) | AI 문서 분석 경로 `/ai/documents/{id}/analysis` vs 서류 업로드 설명의 `/api/ai/documents/analyze` |
| [document.md](document.md) | AI에 보내는 ID 형식: 숫자(AI 페이지) vs 문자열(연동 규칙·업로드 API) |
| [document.md](document.md) | 쉬운말 요약 카드가 `GET`인데 JSON Body를 받음 |
| [comparison.md](comparison.md) | AI 서류 비교 요청 값(`comparisonId` 포함 여부, ID 타입) |
| [comparison.md](comparison.md) | 앱용 `POST /comparisons/{id}/inquiry-messages`는 노션에 별도 페이지가 없음 |
| [chat.md](chat.md) | AI 채팅 경로 `/ai/chats/{id}/answer`(빨간색 표시) vs 본문의 `/ai/chats/{id}/messages`, Body 값(`documentId` vs `documentIds`) |
| [chat.md](chat.md) | 질문 칩 경로 `/ai/chat/suggestions` vs 본문의 `/ai/chats/{id}/suggested-questions`, 서류 변경 시 다시 받는지 여부 |
| [deadline.md](deadline.md) | `priority.keyDeadline.actionDate`로 기한을 따로 만드는지 여부 (서류 상세 조회와 다름) |

### 노션에 별도 페이지가 없는 내부 API

[01-ai-integration.md](01-ai-integration.md)의 내부 API 중 아래 두 개는 별도 페이지 없이 앱 API가 X-Internal-Key 호출도 받는 방식으로 적혀 있습니다.

- 문서 분석 결과 조회 → [document.md > 서류 상세 조회](document.md#4-서류-상세-조회)의 "AI 서버 호출"
- 최근 대화 조회 → [chat.md > 대화 메시지 조회](chat.md#3-대화-메시지-조회)의 "AI 서버 호출"
