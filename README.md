# chagok_server
문서 자동 정리 서비스 차곡 서버 레포지토리입니다

- 기술 스택: Java 21, Spring Boot 4.1.2, Gradle, Spring Data JPA(MySQL), Spring Security(JWT), Spring RestClient, AWS SDK S3, Apache PDFBox
- API 명세: [docs/api](docs/api/README.md) (노션 「API명세서」 정리본)
- 작업 규칙: [AGENTS.md](AGENTS.md)

## 빌드·테스트

```bash
./gradlew build   # 빌드 + 테스트
./gradlew test    # 테스트만
```

테스트는 H2와 목(Mockito)으로 돌며 실제 MySQL·S3·AI 서버·FCM은 필요 없습니다.

## 설정

`src/main/resources/application.yaml`은 비밀값을 환경변수로 받습니다. 로컬 값은 `application-local.yaml`(커밋 제외)에 둡니다.

| 환경변수 | 용도 |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL |
| `JWT_SECRET` | JWT 서명 키 |
| `SMS_HASH_SECRET`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` | 인증번호 문자 발송 |
| `KAKAO_CLIENT_ID`, `KAKAO_APP_ID`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI` | 카카오 로그인 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | 구글 로그인 |
| `CHAGOK_INTERNAL_KEY` | AI 서버 → BE 내부 API 인증 (`X-Internal-Key`) |
| `S3_BUCKET`, `AWS_REGION`(기본 `ap-northeast-2`) | 서류 원본·페이지 이미지 저장소. 자격 증명은 AWS 기본 제공자 체인 |
| `AI_SERVER_URL`, `AI_SERVER_API_KEY` | BE → AI 서버 호출 (`X-API-Key`) |
| `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY` | FCM 푸시 발송 (Firebase 서비스 계정 키 JSON의 `project_id`, `client_email`, `private_key`). 하나라도 비면 푸시를 로그로만 남김 |

테이블 생성 방식(`ddl-auto`)은 `application.yaml`에 없습니다. 로컬 프로파일에서 지정하거나 DDL을 따로 실행해야 합니다.

## API 구현 현황

명세의 앱용 API는 모두 구현했습니다. URL은 AGENTS.md 규칙에 따라 `/api/v1` 접두사를 붙이고, 응답은 `ApiResponse<T>`로 감쌉니다(노션 명세는 접두사·래퍼가 없음).

| 도메인 | 경로 | 구현 | 비고 |
| --- | --- | --- | --- |
| auth | `/auth/**` | 10/10 | 명세 대조는 이번 정리에서 다시 하지 않았습니다 |
| user | `/user/**` | 6/6 | 내 정보 조회, 글자 크기, 프로필, 약관 동의, **회원 탈퇴**, 내부 조회(AI 서버용) |
| box | `/boxes/**` | 5/5 | 가입 시 기본 박스 5개 생성 |
| document | `/documents/**` | 10/10 | 업로드는 비동기 AI 분석. AI 서버용 내부 조회(`X-Internal-Key`)는 서류 상세 조회에 포함 |
| deadline·reminder | `/deadlines/**`, `/reminders/**` | 7/7 | 10분마다 도는 알림 스케줄러 포함 |
| home | `/home` | 1/1 | |
| notification | `/notifications/**` | 7/7 | FCM 푸시 발송 포함 (`FCM_*` 설정이 있을 때) |
| comparison | `/comparisons/**` | 3/3 | 비교 요청·결과 조회·확인 요청 문자. 결과 조회는 AI 서버용 내부 호출도 받음 |
| chat | `/chats/**` | 5/5 | 메시지 조회는 AI 서버용 내부 호출도 받음. 스트리밍(SSE) 포함 |

FCM 푸시는 firebase-admin 없이 FCM HTTP v1 API를 RestClient로 호출합니다. 알림 저장이 커밋된 뒤 비동기로 보내고, FCM이 토큰 만료(UNREGISTERED)를 알려 주면 그 토큰을 지웁니다. 실제 FCM 서버로는 아직 보내 보지 못했습니다(테스트는 모의 서버).

BE → AI 서버 호출(문서 분석, 요약 카드, 채팅, 질문 칩, 서류 비교, 문자 생성)은 `domain/ai`의 `AiServerClient`로 구현했습니다.

## 아직 안 된 것·확정이 필요한 것

| 항목 | 상태 |
| --- | --- |
| HEIC 업로드 | Java 기본 이미지 라이브러리가 읽지 못해 `UNSUPPORTED_FILE_TYPE`로 거부합니다 |
| AI 서버 서류 인덱스 삭제 | 서류·박스 삭제, 회원 탈퇴 때 요청하지 않습니다 (명세에 API 없음) |
| 카카오 연결 끊기·구글 토큰 철회 | 회원 탈퇴 때 호출하지 않습니다 (로그인 토큰을 저장하지 않고 카카오 어드민 키 설정이 없음) |
| 회원 탈퇴 비밀번호 재확인 | 명세가 확정 전이라 넣지 않았습니다 |
| AI 채팅 서류 한도 | 명세 목표는 20개이나 AI가 5개만 받아 5개로 막습니다 |
| AI 채팅 `INDEX_NOT_FOUND` 복구 | 서류 분석을 다시 요청하지 않고 409만 줍니다 (다시 분석하면 사용자가 고친 값이 덮어써짐) |
| 질문 횟수 제한(분당 10회) | 서버 한 대의 메모리에서만 셉니다. 서버를 여러 대로 늘리면 공유 저장소가 필요합니다 |
| 기한 수정 PATCH | `recurrence`·`endDate`를 `null`로 지워 반복을 끄는 것은 지원하지 않습니다 |

AI 서버와 경로·요청 형식을 맞춰야 하는 곳은 [docs/api/README.md](docs/api/README.md)의 "노션 원본 안에서 서로 다른 부분" 표에 있습니다. 구현은 아래처럼 정했습니다.

- 문서 분석: `POST /ai/documents/{id}/analysis`, ID는 숫자
- 요약 카드: GET+Body 대신 `POST /ai/documents/summary-card`
- 채팅: `POST /ai/chats/{id}/answer`, Body는 `userId`·`documentIds`(복수)·`question`
- 질문 칩: `GET /ai/chat/suggestions` (서류 ID 필수)
