# BE ↔ AI 서버 연동

> 원본: 노션 「API명세서」 > BE ↔ AI 서버 연동

## BE → AI 서버 (X-API-Key)

| AI API | BE가 부르는 때 | 관련 BE API |
| --- | --- | --- |
| 문서 분석 | 서류 업로드 직후, 서류 종류를 바꿨을 때 (`docTypeHint`) | POST `/documents`, PATCH `/documents/{id}/fields` |
| 쉬운말 요약 카드 | 분석이 끝난 뒤, 서류 내용을 고친 뒤 (비동기) | GET `/documents/{id}`의 `summaryCard` |
| 자주 묻는 질문 칩 | 새 채팅·채팅 화면 열 때 (내 보관함 서류 종류 기준) | POST `/chats`, GET `/chats/{id}/messages` |
| 서류 기반 AI 채팅 | 질문 보내기 | POST `/chats/{id}/messages` |
| 서류 비교 | 서류 비교 요청 | POST `/comparisons` |
| 확인 요청 문자 생성 | 확인 요청 문자 만들기 | POST `/comparisons/{id}/inquiry-messages` |

## AI 서버 → BE 내부 API (X-Internal-Key)

AI 서버는 BE DB에 직접 접근하지 않고, 필요한 데이터를 아래 내부 API로 가져갑니다. 사용자 토큰은 쓰지 않으며, 외부에서 부를 수 없게 막습니다.

| 내부 API | BE가 돌려주는 값 | 값을 만드는 APP API |
| --- | --- | --- |
| 문서 분석 결과 조회 | AI 문서 분석 응답 + 사용자 수정(`userEdited`) + 체크 상태(`checklist`) + 원문(`fullText`) | 서류 업로드, 서류 내용 수정, 체크 항목 변경 |
| 사용자 정보 조회 | 생년월일 + 온보딩 설문(국적·거주 지역·주거 형태·복지 대상 여부) + 동의 여부 | 회원가입·카카오·구글 로그인, 온보딩 설문 저장, 약관 동의 기록 |
| 최근 대화 조회 | 대화방의 최근 메시지 본문 | 질문 보내기 (BE가 질문·답변 저장) |

## 규칙

- ID: BE의 숫자 ID를 문자열로 바꿔 보냅니다 (예: `"520"`). AI 명세 예시의 `doc_8f2a1c` 같은 값은 예시입니다.
- AI 응답 값 이름은 바꾸지 않고 앱에 그대로 넘기는 것이 원칙입니다 (`docType`, `docCategory`, `priority`, `legalCheck`, `summaryCard`, `comparisons`, `suggestions`, `citations` 등). BE는 ID 변환과 화면용 필드(`box`, `deadlines`, `outdated` 등)만 붙입니다.
- AI 문서 분석 응답은 통째로 저장합니다. 앱 응답에 쓰고, 문서 분석 결과 조회 내부 API로 AI에 다시 줍니다.
- 내부 API는 AI가 보낸 `userId`가 리소스 주인과 다르면 403을 줍니다 (AI 명세).

## AI 명세에 요청할 것 (기획 흐름 기준)

| 요청 | 이유 (기획) | 관련 BE API |
| --- | --- | --- |
| 문서 분석 또는 쉬운말 요약 카드에 `userRelevance`(level, reasons) 추가 | AI 정리본에 "사용자 상황과 비교해 관련 있는지" 표시 | GET `/documents/{id}` |
| 자주 묻는 질문 칩이 `docTypes`(내 보관함 서류 종류)를 받기 | 칩은 내 박스의 파일 종류 기준 | POST `/chats` |
| 서류 기반 AI 채팅 `documentIds` 한도 5 → 20 | 박스 통째로 넣기 | PUT `/chats/{id}/documents` |
| 서류 기반 AI 채팅의 Body 설명 정리 (history·documentContext·userProfile을 BE가 보내지 않음) | AI가 내부 API로 직접 가져가는 구조 | POST `/chats/{id}/messages` |
| 사용자 정보 조회의 `sensitive`·`consents.sensitive` 제거 (또는 항상 null/false) | 온보딩에 민감정보 없음 | PUT `/user/me/profile` |
| 내부 API 경로 통일 (`/user/{userId}` vs `/internal/users/{userId}/profile`) | 문서 분석 페이지와 사용자 정보 조회 페이지가 다름 | - |

참고: 알림 간격은 AI `calendarEvents.remindBeforeDays` 대신 기한 종류별 기본값([코드표 > 기한 종류](02-enums.md#기한-종류-deadline-type) 참고)을 씁니다. 사용자가 추가·수정한 기한은 문서 분석 결과 조회의 `calendarEvents`에 `userEdited: true`로 넣습니다.
