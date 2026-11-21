# 화면별 API

> 원본: 노션 「API명세서」 > 화면별 API

| 화면 | API |
| --- | --- |
| 로딩(스플래시) | 저장한 토큰이 있으면 GET `/user/me` (401이면 POST `/auth/token/refresh`), 없으면 L01 |
| A01 처음 만나는 차곡 (전화번호·이름·생년월일·비밀번호) | POST `/auth/phone/verifications` |
| A02 문자를 확인해 주세요 | POST `/auth/phone/verifications/confirm` (SIGNUP), 다시 받기는 POST `/auth/phone/verifications` |
| A03 시작 전, 확인해 주세요 | POST `/auth/signup` (카카오·구글 신규 가입자는 POST `/user/me/consents`) |
| O01 국적 · O02 주거 지역 · O03 주거 형태 · O04 복지 | O04 "완료"에서 PUT `/user/me/profile` 한 번 (O02 주소 검색은 앱이 외부 주소 API로) |
| 마이페이지 | GET `/user/me`, PUT `/user/me/profile`(정보 수정) |
| L01 다시 만나 반가워요 | POST `/auth/login`, POST `/auth/kakao`, POST `/auth/google` |
| L02 비밀번호를 잊으셨나요? | POST `/auth/password/reset-requests` → 전화번호 인증 화면 POST `/auth/phone/verifications/confirm` (PASSWORD_RESET) |
| L03 새 비밀번호 만들기 | POST `/auth/password/resets` |
| L04 다시 로그인해 주세요 | POST `/auth/token/refresh`가 401일 때 |
| S01 설정 | GET `/user/me` (이름·전화번호·글자 크기·알림 켜짐). 앱 버전·이용약관·개인정보 처리방침은 앱/웹 페이지 (API 없음) |
| S02 글자 크기 | PATCH `/user/me/preferences` (fontSize) |
| S03 놓치지 않도록 알려 드려요 | GET·PATCH `/notifications/settings` (납부일 알림·기본 알림 시각), 마케팅 수신은 POST `/user/me/consents`. 기기 알림 권한은 앱에서 확인 |
| S04 로그아웃하시겠어요? | "예" → POST `/auth/logout` |
| S05 정말 탈퇴하시겠어요? | "탈퇴" → DELETE `/user/me` (Body 없음) |
| M00 홈 | GET `/home` (이번 주 미니 캘린더·가장 가까운 기한·최근 연 보관함·최근 알림·알림 배지) |
| 04 내 보관함 | GET `/boxes`, GET `/notifications` (알림 배지) |
| 05 새 박스 만들기 | POST `/boxes` |
| 06 박스 상세 | GET `/documents` (boxId) |
| 07 서류 원본 · 09 기한 없는 서류 | GET `/documents/{id}`, GET `/documents/{id}/original` |
| 08 서류 AI 정리본 | GET `/documents/{id}` (summaryCard), 체크는 PATCH `/documents/{id}/checklist/{checkKey}` |
| 07·08 "기한 알림 등록" | POST `/reminders` |
| 10 검색 · 11 검색 결과 | GET `/documents` (keyword, 필터 칩) |
| 12 캘린더 | GET `/deadlines`, 달력 아이콘은 POST `/reminders` · DELETE `/reminders/{id}` |
| 13 캘린더 날짜 선택 | GET `/deadlines` (from=to=고른 날) |
| 14 촬영 · 15 앨범 선택 | GET `/boxes` (박스 선택), POST `/documents` |
| 16 AI 처리 중 | GET `/documents/{id}` (analysisStep) |
| U01 어떤 서류를 담을까요? · U02 이 파일을 분석할까요? | POST `/documents` |
| U03 조금 더 선명하게 찍어 주세요 | GET `/documents/{id}` (`FAILED`) → POST `/documents` |
| U04 읽어 낸 내용을 확인해요 | GET `/documents/{id}`, POST `/documents/{id}/confirm`, PATCH `/documents/{id}/fields` |
| D01 서류 내용 수정 | PATCH `/documents/{id}/fields`, GET `/documents/{id}/original` |
| D02 납부일을 알려 드릴게요 | 알림이 있으면(대부분 자동 생성) PATCH `/reminders/{id}`, 없으면 POST `/reminders`. "다음 알림"은 저장 전 앱이 계산 |
| C01 서류 두 장을 비교해요 | GET `/documents`, POST `/comparisons` |
| C02 약속과 다른 금액이 있어요 | GET `/comparisons/{id}`, GET `/documents/{id}/original` |
| C03 이렇게 물어보면 어떨까요? | POST `/comparisons/{id}/inquiry-messages` |
| M01 내 서류 | GET `/documents`, GET `/notifications` (알림 배지) |
| M02 서류 이름 변경 | PATCH `/documents/{id}` |
| M03 이 서류를 삭제할까요? | DELETE `/documents/{id}` |
| M04 PDF로 내려받기 | GET `/documents/{id}/download` |
| H01 서류에 대해 물어보세요 | POST `/chats`, GET `/chats/{id}/messages`, POST `/chats/{id}/messages` |
| H02 대화에 서류 추가 | GET `/boxes`, GET `/documents`, PUT `/chats/{id}/documents` (기획: 보관함 서류·박스만, 외부 파일 업로드는 빠짐) |
| H03 지난 대화 | GET `/chats` |
| N01 알림 | GET `/notifications`, PATCH `/notifications/read-all` |
| N02 월세 납부 알림 | GET `/notifications/{id}`, PATCH `/notifications/{id}` |
| 앱 시작·로그인 직후 | PUT `/notifications/push-tokens/{deviceId}` |

## 기획 흐름 기준

내비게이션 바: 보관함 · AI챗봇 · 메인 화면 · 캘린더 · 마이페이지 / 오른쪽 위 알림 버튼

| 흐름 | API |
| --- | --- |
| 회원가입 (전화번호) | POST `/auth/phone/verifications` → POST `/auth/phone/verifications/confirm` → POST `/auth/signup` (이름·생년월일·동의 포함) |
| 회원가입 (카카오·구글) | POST `/auth/kakao` 또는 POST `/auth/google` → POST `/user/me/consents` |
| 만 14세 미만 (확정 전) | 보호자 동의 `guardianConsent` (회원가입 또는 약관 동의 기록) |
| 로그인 | POST `/auth/login`, POST `/auth/kakao`, POST `/auth/google` |
| 온보딩 설문 | PUT `/user/me/profile` (국적·거주 지역·주거 형태·복지 대상 여부) |
| 메인 화면 | GET `/home` (이번 주 미니 캘린더·최근 열어본 박스·최근 알림) |
| 보관함 | GET `/boxes`, 검색 GET `/documents`(keyword), 박스 생성 POST `/boxes`, 이름 수정 PATCH `/boxes/{id}`, 삭제 DELETE `/boxes/{id}` (안의 파일까지) |
| 박스 안 | GET `/boxes/{id}`, GET `/documents`(boxId), 새 파일 POST `/documents` (기기 파일·촬영) |
| 서류 (원본 / AI 정리본) | GET `/documents/{id}` (summaryCard·userRelevance·종류·중요도·비용·기한), GET `/documents/{id}/original` |
| AI챗봇 | 채팅 기록 GET `/chats`, 새 채팅 POST `/chats`, GET `/chats/{id}/messages`, 서류·박스 넣기 PUT `/chats/{id}/documents`, 질문 POST `/chats/{id}/messages` |
| 캘린더 | GET `/deadlines`, 추가 POST `/deadlines`, 수정 PATCH `/deadlines/{id}`, 삭제 DELETE `/deadlines/{id}` |
| 마이페이지 · 설정 | GET `/user/me`, 정보 수정 PUT `/user/me/profile`, 글자 크기 PATCH `/user/me/preferences`, 알림 GET·PATCH `/notifications/settings`, POST `/auth/logout`, DELETE `/user/me` |
| 알림 (오른쪽 위) | GET `/notifications`, GET·PATCH `/notifications/{id}`, PATCH `/notifications/read-all`. 납부·만료 기한 종류별 간격으로 자동 발송 |
