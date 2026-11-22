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

테스트는 H2와 목(Mockito)으로 돌며 실제 MySQL·S3·AI 서버·FCM·카카오는 필요 없습니다. 외부 서비스 호출은 모의 서버로만 확인했고, 실제 서비스와 붙여 본 적은 없습니다.

## 설정

`src/main/resources/application.yaml`은 비밀값을 환경변수로 받습니다. 로컬 값은 `application-local.yaml`(커밋 제외)에 둡니다.

| 환경변수 | 용도 |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL |
| `JWT_SECRET` | JWT 서명 키 |
| `SMS_HASH_SECRET`, `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM` | 인증번호 문자 발송 |
| `SMS_PROVIDER`(선택, 기본 `twilio`) | `log`로 두면 문자를 보내지 않고 인증번호를 서버 로그에 남기고 발송 API 응답의 `code` 필드에도 포함(시연·테스트용, 운영 금지). 이때 Twilio 값은 필요 없음 |
| `KAKAO_CLIENT_ID`, `KAKAO_APP_ID`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI` | 카카오 로그인 |
| `KAKAO_ADMIN_KEY`(선택) | 회원 탈퇴 때 카카오 연결 끊기. 비어 있으면 호출하지 않음 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI` | 구글 로그인 |
| `CHAGOK_INTERNAL_KEY` | AI 서버 → BE 내부 API 인증 (`X-Internal-Key`) |
| `S3_BUCKET`, `AWS_REGION`(기본 `ap-northeast-2`) | 서류 원본·페이지 이미지 저장소. 자격 증명은 AWS 기본 제공자 체인 |
| `AI_SERVER_URL`, `AI_SERVER_API_KEY` | BE → AI 서버 호출 (`X-API-Key`) |
| `FCM_PROJECT_ID`, `FCM_CLIENT_EMAIL`, `FCM_PRIVATE_KEY`(선택) | FCM 푸시 발송 (Firebase 서비스 계정 키 JSON의 `project_id`, `client_email`, `private_key`). 하나라도 비면 푸시를 로그로만 남김 |

테이블 생성 방식(`ddl-auto`)은 `application.yaml`에 없습니다. 로컬 프로파일에서 지정하거나 DDL을 따로 실행해야 합니다.

## Docker Compose로 실행

MySQL 8.4, Redis 7, 앱을 한 번에 띄웁니다. 로컬 개발·테스트용이며 운영 배포 설정이 아닙니다.

```bash
cp .env.example .env              # 예시 값이 들어 있어 그대로도 뜹니다. 필요한 값만 고치세요
docker compose up -d mysql redis  # DB·Redis만 (앱은 IDE에서 실행, DB는 localhost:3306)
docker compose up -d --build      # 앱까지 전부 (http://localhost:8080)
docker compose down               # 중지 (데이터 유지). 데이터까지 지우려면 down -v
```

- `.env`는 커밋하지 않습니다(`.gitignore`). 환경변수 목록과 예시 값은 [.env.example](.env.example)에 있습니다.
- DB·Redis 포트는 내 컴퓨터(127.0.0.1)에서만 열립니다. 포트가 겹치면 `.env`의 `MYSQL_PORT`, `REDIS_PORT`, `APP_PORT`를 바꾸세요.
- compose에서는 테이블을 자동으로 만듭니다(`DDL_AUTO=update`). 운영에서는 `validate` 또는 `none`만 쓰세요.
- Redis는 띄워 두기만 하고 앱은 아직 쓰지 않습니다(의존성 없음).
- S3·AI 서버·FCM은 예시 값이 더미입니다. 서류 업로드를 실제로 쓰려면 `S3_BUCKET`과 AWS 자격 증명을 채우세요. 호스트에서 돌리는 AI 서버는 `http://host.docker.internal:8000`으로 접속합니다.

## 구성

도메인형 패키지(`gachi.chagok_server`)입니다.

| 위치 | 내용 |
| --- | --- |
| `domain/auth`, `domain/user` | 가입·로그인·토큰, 내 정보·프로필·약관 동의·회원 탈퇴 |
| `domain/box`, `domain/document` | 박스, 서류 업로드·분석 반영·조회·수정·삭제 |
| `domain/deadline`, `domain/notification`, `domain/home` | 기한·기한 알림, 알림 목록·설정·푸시, 메인 화면 |
| `domain/comparison`, `domain/chat` | 서류 비교·확인 요청 문자, AI 채팅 |
| `domain/ai` | BE → AI 서버 호출 클라이언트(`AiServerClient`) |
| `global` | 공통 응답·예외, 보안(JWT·내부 키), 설정, S3 저장소 |

주기 작업은 두 가지입니다. 10분마다 기한 알림을 보내고(`ReminderScheduler`), 매시 정각에 분석에 실패한 지 24시간이 지난 서류를 지웁니다(`DocumentCleanupScheduler`).

## API 구현 현황

명세의 앱용 API는 모두 구현했습니다. URL은 AGENTS.md 규칙에 따라 `/api/v1` 접두사를 붙이고, 응답은 `ApiResponse<T>`로 감쌉니다(노션 명세는 접두사·래퍼가 없음).

| 도메인 | 경로 | 구현 | 비고 |
| --- | --- | --- | --- |
| auth | `/auth/**` | 10/10 | 이전 작업분이라 명세와 다시 대조하지 않았습니다 |
| user | `/user/**` | 6/6 | 내 정보 조회, 글자 크기, 프로필, 약관 동의, 회원 탈퇴, 내부 조회(AI 서버용) |
| box | `/boxes/**` | 5/5 | 가입 시 기본 박스 5개 생성 |
| document | `/documents/**` | 10/10 | 업로드는 비동기 AI 분석 |
| deadline·reminder | `/deadlines/**`, `/reminders/**` | 7/7 | |
| home | `/home` | 1/1 | |
| notification | `/notifications/**` | 7/7 | |
| comparison | `/comparisons/**` | 3/3 | 비교 요청, 결과 조회, 확인 요청 문자 |
| chat | `/chats/**` | 5/5 | 질문 보내기는 스트리밍(SSE)도 지원 |

### AI 서버용 내부 API

AI 서버는 사용자 토큰 없이 `X-Internal-Key` 헤더로 아래 API를 호출합니다. 키가 없거나 틀리면 403이고, 이 헤더로는 아래 경로 외의 API를 부를 수 없습니다.

| 경로 | 용도 |
| --- | --- |
| `GET /api/v1/user/{userId}` | 사용자 정보 |
| `GET /api/v1/documents/{id}?userId=` | 서류 분석 결과 (OCR 원문 `fullText` 포함) |
| `GET /api/v1/comparisons/{id}?userId=` | 비교 결과 |
| `GET /api/v1/chats/{id}/messages?userId=` | 최근 대화 |

### 동작 메모

- **서류 분석**: 업로드하면 202를 주고, 커밋 뒤 비동기로 AI 서버를 부릅니다. AI 서버 오류·시간 초과는 한 번 더 시도합니다.
- **AI 채팅 서류 복구**: AI가 서류를 잃어버리면(`INDEX_NOT_FOUND`) 서류 분석을 다시 요청합니다. 이때 사용자가 고친 항목·납부일·기한·알림 설정은 새 결과에 이어 붙이고, 끝나면 원래 서류 상태로 되돌리며, 실패해도 서류를 실패 처리하지 않습니다. 앱에는 409 `DOCUMENT_NOT_INDEXED`로 잠시 기다리게 합니다.
- **푸시**: firebase-admin 없이 FCM HTTP v1 API를 RestClient로 호출합니다. 알림 저장이 커밋된 뒤 비동기로 보내고, FCM이 토큰 만료(`UNREGISTERED`)를 알려 주면 그 토큰을 지웁니다.
- **회원 탈퇴**: 서류·파일·비교·대화·알림·토큰·프로필·소셜 연결을 한 트랜잭션으로 지웁니다. 사용자 행은 개인정보만 비우고 남겨 약관 동의 이력을 보관하며, 같은 전화번호로 다시 가입할 수 있습니다. 저장소 파일과 카카오 연결 끊기(`KAKAO_ADMIN_KEY`가 있을 때)는 커밋 뒤 비동기로 처리합니다.

### AI 서버와 맞춘 경로·형식

명세 안에서 서로 다르게 적힌 곳([docs/api/README.md](docs/api/README.md)의 "노션 원본 안에서 서로 다른 부분" 표)은 아래처럼 정했습니다. AI 팀과 확정이 필요합니다.

- 문서 분석: `POST /ai/documents/{id}/analysis`, ID는 숫자
- 요약 카드: GET+Body 대신 `POST /ai/documents/summary-card`
- 채팅: `POST /ai/chats/{id}/answer`, Body는 `userId`·`documentIds`(복수)·`question`
- 질문 칩: `GET /ai/chat/suggestions` (서류 ID 필수)

## 제한 사항

| 항목 | 상태 |
| --- | --- |
| HEIC 업로드 | Java 기본 이미지 라이브러리가 읽지 못해 `UNSUPPORTED_FILE_TYPE`로 거부합니다. 앱이 JPEG로 바꿔 올려야 합니다 (서버 변환은 하지 않기로 함) |
| AI 서버 서류 인덱스 삭제 | 서류·박스 삭제, 회원 탈퇴 때 요청하지 않습니다 (명세에 API 없음) |
| 구글 토큰 철회 | 회원 탈퇴 때 호출하지 않습니다 (로그인 때 받은 토큰을 저장하지 않음) |
| 회원 탈퇴 비밀번호 재확인 | 넣지 않기로 했습니다 (명세 기본값대로 Body 없이 토큰만으로 탈퇴) |
| AI 채팅 서류 한도 | 명세 목표는 20개이나 AI가 5개만 받아 5개로 막습니다. AI가 늘리면 `ChatLinkResolver.MAX_DOCUMENTS`를 바꿉니다 |
| 질문 횟수 제한(분당 10회) | 서버 한 대의 메모리에서만 셉니다 (서버 1대 운영 기준으로 유지하기로 함). 서버를 여러 대로 늘리면 공유 저장소가 필요합니다 |
