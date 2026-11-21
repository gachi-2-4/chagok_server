# USER API 명세

> 원본: 노션 「API명세서」 > USER DB
>
> 공통 규칙(인증·형식·에러·페이지네이션)은 [00-common.md](00-common.md), 코드표는 [02-enums.md](02-enums.md)를 참고합니다.
> 필수 약관 동의 전에도 USER API는 403 `CONSENT_REQUIRED` 제한 없이 호출할 수 있습니다 (공통 규칙).

## 목록

| # | 이름 | Method | Path | 호출 주체 |
| --- | --- | --- | --- | --- |
| 1 | [내 정보 조회](#1-내-정보-조회) | GET | `/user/me` | APP |
| 2 | [온보딩 설문 저장](#2-온보딩-설문-저장) | PUT | `/user/me/profile` | APP |
| 3 | [약관 동의 기록](#3-약관-동의-기록) | POST | `/user/me/consents` | APP |
| 4 | [글자 크기 변경](#4-글자-크기-변경) | PATCH | `/user/me/preferences` | APP |
| 5 | [회원 탈퇴](#5-회원-탈퇴) | DELETE | `/user/me` | APP |
| 6 | [사용자 정보 조회 (내부)](#6-사용자-정보-조회-내부-api) | GET | `/user/{userId}` | AI 서버 (X-Internal-Key) |

---

## 1. 내 정보 조회

`GET /user/me`

### 개요

- 로그인한 사용자의 정보와 설정을 조회합니다.
- 화면: S01 설정 (이름·전화번호, 글자 크기 "보통", 알림 설정 "켜짐"), 마이페이지, 앱 시작(로딩 화면) 시 글자 크기 적용·온보딩 완료 여부 확인

### 기술 설명/요구 사항

- S01·마이페이지에 이름·전화번호·생년월일과 온보딩 설문 값(`profile`)을 보여 줍니다. 전화번호는 숫자만 주고 하이픈(010-4184-5036)은 앱이 넣습니다. 설문 값은 온보딩 설문 저장 API로 고칩니다.
- `onboardingCompleted`가 false면 온보딩 설문으로 보냅니다.
- `consentRequired`가 true면(카카오 신규 가입 후 동의 전) A03 약관 동의로 보냅니다.
- `fontSize`는 앱 전체 글자 크기입니다 (글자 크기 변경 API). S01 "글자 크기" 오른쪽 값입니다.
- `notificationEnabled`는 납부일 알림(`deadlineAlertEnabled`)이 켜져 있으면 true입니다 (S01 "알림 설정 켜짐/꺼짐").
- 카카오·구글 계정은 `phoneNumber`·`birthDate`가 null일 수 있습니다 (소셜에서 못 받았고 온보딩 전).

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**: 없음

### Response

**200 OK**

```json
{
  "userId": 1024,
  "name": "이차곡",
  "birthDate": "2001-03-14",
  "phoneNumber": "01041845036",
  "loginType": "PHONE",
  "onboardingCompleted": true,
  "consentRequired": false,
  "profile": {
    "nationality": "KR",
    "region": { "sido": "서울특별시", "sigungu": "마포구" },
    "housingType": "MONTHLY_RENT",
    "welfareType": "NONE",
    "updatedAt": "2026-11-18T10:50:00+09:00"
  },
  "fontSize": "NORMAL",
  "consents": {
    "termsOfService": true,
    "privacyPolicy": true,
    "guardianConsent": false,
    "overseasTransfer": false,
    "marketing": false
  },
  "notificationEnabled": true,
  "createdAt": "2026-11-14T10:21:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| userId | number | 사용자 ID |
| name | string | 이름 |
| birthDate | string(date) | 생년월일 |
| phoneNumber | string | 전화번호 숫자만 (카카오·구글 계정은 null 가능) |
| loginType | string | `PHONE` / `KAKAO` / `GOOGLE` |
| onboardingCompleted | boolean | 온보딩 설문 완료 여부 |
| consentRequired | boolean | 필수 동의가 필요한지 |
| profile | object | 온보딩 설문 값 (국적·거주 지역·주거 형태·복지 대상 여부). 온보딩 전이면 null |
| fontSize | string | 글자 크기 `NORMAL` 보통 / `LARGE` 크게 / `EXTRA_LARGE` 아주 크게 |
| consents | object | 동의 종류별 현재 동의 여부 |
| notificationEnabled | boolean | 납부일 알림이 켜져 있는지 |
| createdAt | string | 가입 일시 |

**401 Unauthorized**

```json
{ "code": "UNAUTHORIZED", "message": "다시 로그인해 주세요." }
```

---

## 2. 온보딩 설문 저장

`PUT /user/me/profile`

### 개요

- 온보딩 설문 답(국적·거주 지역·주거 형태·복지 대상 여부)을 저장합니다. 마이페이지에서 정보를 고칠 때도 씁니다.
- 화면: 첫 설문 O01 국적 → O02 주거 지역 → O03 주거 형태 → O04 복지 (O04에서 "완료"를 누를 때 한 번 호출), 마이페이지 정보 수정
  - 피그마에서는 복지 설문도 "O03"으로 적혀 있어 여기서는 O04로 부릅니다.

### 기술 설명/요구 사항

- 가입 흐름: 회원가입(전화번호) 또는 카카오·구글 로그인 → 약관·개인정보 동의 → 온보딩 설문(이 API)
- 설문 4개 화면은 앱이 답을 모아 두었다가 마지막 "완료"에서 한 번에 보냅니다. 처음 저장하면 `onboardingCompleted=true`가 됩니다.
- 네 항목 모두 필수입니다. 각 화면에 "해당 없음"/선택지가 있어 빈 값이 없습니다.
- 직업(`occupation`) 문항은 디자인에서 빠져 받지 않습니다.
- 거주 지역(O02)은 도로명주소/지번주소 검색 결과에서 시·도, 시·군·구만 보냅니다. 상세 주소는 받지 않습니다. 주소 검색은 앱이 주소 검색 API(예: 행정안전부 도로명주소 API, 카카오 우편번호 서비스)로 직접 합니다.
- 생년월일은 가입 때 받습니다. 카카오·구글에서 받지 못한 경우(`missingFields`에 `birthDate`)에만 여기서 받고, 이미 있으면 바꿀 수 없습니다 (만 14세 판단 근거라서).
  - 확정 전: 소셜 가입자의 생년월일 입력 화면이 디자인에 없습니다.
- 생년월일 기준 만 14세 미만인데 보호자 동의(`guardianConsent`)가 없으면 403입니다.
- 저장한 값은 AI 서버가 사용자 정보 조회 내부 API로 가져가 문서 분석의 법적 위험 점검, AI 정리본의 "내 상황과 관련 있는지", AI 채팅에 씁니다. 민감정보(`sensitive`)는 받지 않으므로 항상 null로 줍니다.
- 수집 근거는 가입 때 받은 개인정보 수집·이용 동의(`privacyPolicy`)입니다.
- PUT이라서 보낸 값으로 통째로 바꿉니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "nationality": "KR",
  "region": { "sido": "서울특별시", "sigungu": "마포구" },
  "housingType": "DORMITORY",
  "welfareType": "BASIC_LIVELIHOOD",
  "birthDate": null
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| nationality | string | O | O01 국적 `KR` 내국인 / `FOREIGN` 외국인 |
| region.sido / region.sigungu | string | O | O02 거주 지역 시·도 / 시·군·구 |
| housingType | string | O | O03 주거 형태 `MONTHLY_RENT` 월세 / `JEONSE` 전세 / `OWNED` 자가 / `DORMITORY` 기숙사·고시원 / `WITH_PARENTS` 부모님 집 |
| welfareType | string | O | O04 복지 `BASIC_LIVELIHOOD` 기초생활수급 대상자 / `NEAR_POVERTY` 차상위계층 대상자 / `OTHER` 기타 / `NONE` 해당 없음 |
| birthDate | string(date) | 조건부 | 소셜 로그인에서 생년월일을 못 받았을 때만 |

값의 종류는 AI 사용자 정보 조회 응답과 같습니다.

### Response

**200 OK**

```json
{
  "nationality": "KR",
  "region": { "sido": "서울특별시", "sigungu": "마포구" },
  "housingType": "DORMITORY",
  "welfareType": "BASIC_LIVELIHOOD",
  "birthDate": "2001-03-14",
  "onboardingCompleted": true,
  "updatedAt": "2026-11-18T10:50:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| onboardingCompleted | boolean | 온보딩 완료 여부 (처음 저장하면 true) |
| updatedAt | string | 마지막 수정 일시 (AI 사용자 정보 조회에도 같은 값) |
| 나머지 | - | 저장된 값 |

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "모든 질문에 답해 주세요." }
```

```json
{ "code": "BIRTH_DATE_LOCKED", "message": "생년월일은 바꿀 수 없어요." }
```

**403 Forbidden**

```json
{ "code": "GUARDIAN_CONSENT_REQUIRED", "message": "만 14세 미만은 보호자 동의가 필요해요." }
```

---

## 3. 약관 동의 기록

`POST /user/me/consents`

### 개요

- 약관·개인정보 동의와 철회를 기록합니다.
- 화면: 카카오·구글 신규 가입자의 A03 "동의하고 가입 완료", S03 마케팅 수신 토글

### 기술 설명/요구 사항

- 전화번호 가입자의 동의는 회원가입 API에서 같이 기록합니다. 카카오·구글 신규 가입자는 소셜 로그인 뒤 이 API로 기록합니다.
- A03 동의 항목(디자인): 서비스 이용약관(필수), 개인정보 수집·이용(필수), 마케팅 수신(선택). 보호자 동의·국외 이전은 디자인에 없어 확정 전입니다.
- 보낸 항목만 기록합니다. 이력 테이블에 쌓고(동의 종류·동의 여부·약관 버전·시각), 가장 최근 기록을 현재 상태로 봅니다.
- 동의 종류

| key | 내용 | 필수 | AI 사용자 정보 조회 consents |
| --- | --- | --- | --- |
| termsOfService | 서비스 이용약관 | 필수 | - |
| privacyPolicy | 개인정보 수집·이용 (이름·생년월일·전화번호 + 온보딩 설문) | 필수 | required, optional |
| guardianConsent | 보호자 동의 | 만 14세 미만이면 필수 | guardianConsent |
| overseasTransfer | 국외 이전 (외부 AI API) | 선택 (A03 디자인에 없음, 확정 전) | overseasTransfer |
| marketing | 마케팅 수신 | 선택 | - |

- AI 사용자 정보 조회의 `consents.sensitive`는 항상 false입니다 (민감정보는 받지 않음).
- 필수 동의를 하지 않으면 가입이 끝나지 않습니다. 필수 동의는 철회할 수 없고(탈퇴로만 가능) false를 보내면 400입니다.
- 필수 동의가 모두 끝나면 `consentRequired=false`가 되고 403 `CONSENT_REQUIRED` 제한이 풀립니다. 만 14세 미만은 `guardianConsent`까지 있어야 풀립니다.
  - 확정 전: 보호자 동의 확인 방식(체크박스 / 보호자 본인인증)은 정해지지 않았습니다.
- `overseasTransfer=false`면 AI는 외부 LLM에 프로필 정보를 보내지 않습니다 (AI 명세).
- 마케팅 수신 동의·철회는 처리 결과를 알려야 하므로(정보통신망법), 앱은 응답의 `updatedAt`으로 "2026.11.18 마케팅 수신 동의를 철회했어요" 같은 안내를 띄웁니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "termsOfService": true,
  "privacyPolicy": true,
  "guardianConsent": false,
  "overseasTransfer": true,
  "marketing": false
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| termsOfService | boolean | X | 서비스 이용약관 (true만 가능) |
| privacyPolicy | boolean | X | 개인정보 수집·이용 (true만 가능) |
| guardianConsent | boolean | X | 보호자 동의 (만 14세 미만) |
| overseasTransfer | boolean | X | 국외 이전 (외부 AI API) |
| marketing | boolean | X | 마케팅 수신 |

### Response

**200 OK**

```json
{
  "consents": {
    "termsOfService": { "agreed": true, "version": "2026-11-01", "updatedAt": "2026-11-18T10:02:11+09:00" },
    "privacyPolicy": { "agreed": true, "version": "2026-11-01", "updatedAt": "2026-11-18T10:02:11+09:00" },
    "guardianConsent": { "agreed": false, "version": null, "updatedAt": null },
    "overseasTransfer": { "agreed": true, "version": "2026-11-01", "updatedAt": "2026-11-18T10:02:11+09:00" },
    "marketing": { "agreed": false, "version": "2026-11-01", "updatedAt": "2026-11-18T10:02:11+09:00" }
  },
  "consentRequired": false
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| consents.{종류}.agreed | boolean | 현재 동의 여부 |
| consents.{종류}.version | string | 동의한 약관 버전 (기록이 없으면 null) |
| consents.{종류}.updatedAt | string | 마지막으로 동의·철회한 일시 |
| consentRequired | boolean | 필수 동의가 아직 필요한지 |

**400 Bad Request**

```json
{ "code": "REQUIRED_CONSENT_WITHDRAWAL", "message": "필수 동의는 철회할 수 없어요." }
```

---

## 4. 글자 크기 변경

`PATCH /user/me/preferences`

### 개요

- 앱 전체 글자 크기를 저장합니다.
- 화면: S02 글자 크기 "이 크기로 저장" (보통 / 크게 / 아주 크게 + 미리 보기)

### 기술 설명/요구 사항

- `fontSize`는 세 단계입니다: `NORMAL` 보통(기본) / `LARGE` 크게 / `EXTRA_LARGE` 아주 크게
- 실제 배율(예: 1.0 / 1.3 / 1.6)은 앱이 정합니다. 서버는 단계만 저장합니다.
- 미리 보기는 앱이 그리고, "이 크기로 저장"을 누를 때만 호출합니다.
- 서버에 저장해 다른 기기에서도 같은 크기로 보이게 합니다 (S02 "저장하면 차곡 앱 전체에 선택한 글자 크기가 적용돼요"). 앱 시작 시 내 정보 조회의 `fontSize`를 적용합니다.
- 이전 명세의 `uiScale`(1.0–2.0 조절 바)은 S02 디자인의 3단계로 바뀌었습니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "fontSize": "LARGE"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| fontSize | string | O | `NORMAL` / `LARGE` / `EXTRA_LARGE` |

### Response

**200 OK**

```json
{
  "fontSize": "LARGE",
  "updatedAt": "2026-11-18T11:00:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| fontSize | string | 저장된 글자 크기 |
| updatedAt | string | 저장 일시 |

**400 Bad Request**

```json
{ "code": "INVALID_FONT_SIZE", "message": "글자 크기를 골라 주세요." }
```

---

## 5. 회원 탈퇴

`DELETE /user/me`

### 개요

- 계정과 모든 데이터를 삭제합니다.
- 화면: S01 "회원 탈퇴" → S05 정말 탈퇴하시겠어요? "탈퇴"

### 기술 설명/요구 사항

- S05는 확인 모달만 있습니다 ("탈퇴하면 보관한 서류와 기록이 모두 삭제되고 복구할 수 없어요"). 비밀번호 재입력·소셜 재로그인 없이 Access Token만으로 탈퇴합니다. Body는 없습니다.
  - 확정 전: 휴대폰을 잃어버렸을 때 다른 사람이 탈퇴시킬 수 있으므로, 비밀번호 재확인을 넣을지 정해야 합니다. 넣으면 Body에 `password`(전화번호 계정)를 추가합니다.
- 삭제하는 데이터
  - 서류, 원본 파일(스토리지), 분석 결과, 비교 결과
  - AI 대화 내역, AI 서버의 벡터 인덱스 (AI 서버에 삭제 요청)
  - 납부 일정, 알림, 알림 설정, 푸시 토큰, Refresh Token
- 카카오 계정은 카카오 연결 끊기(unlink), 구글 계정은 토큰 철회(revoke)도 호출합니다.
- 응답 전에 계정을 비활성화하고 토큰을 모두 폐기합니다. 파일·벡터 인덱스 삭제는 비동기 작업으로 이어서 처리합니다. 앱은 204를 받으면 저장한 토큰을 지우고 로그인 화면(L01)으로 갑니다.
- 복구할 수 없습니다. 법령상 보관이 필요한 기록(약관 동의 이력 등)만 분리 보관하고 기간이 지나면 파기합니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**: 없음

### Response

**204 No Content**

응답 본문 없음

**401 Unauthorized**

```json
{ "code": "UNAUTHORIZED", "message": "다시 로그인해 주세요." }
```

---

## 6. 사용자 정보 조회 (내부 API)

`GET /user/{userId}`

> 노션 원본에서 이 경로는 빨간색으로 표시되어 있습니다. [BE ↔ AI 서버 연동 > AI 명세에 요청할 것](01-ai-integration.md#ai-명세에-요청할-것-기획-흐름-기준)의 "내부 API 경로 통일 (`/user/{userId}` vs `/internal/users/{userId}/profile`)" 항목과 관련됩니다.
>
> 담당: BE, AI

### 개요

- AI 서버 전용 내부 API입니다. 앱에서는 호출하지 않습니다.
- userId로 사용자 정보를 조회합니다. **응답 형식은 내 정보 조회(GET /users/me)와 같고**, AI에 필요 없는 개인정보만 뺍니다.
- 사용처(AI): 문서 분석(법적 위험 점검, 내 상황 관련 여부 userRelevance), 서류 기반 AI 채팅

### 기술 설명/요구 사항

- 조회 로직은 GET /users/me와 같은 것을 쓰고, 입구(컨트롤러)만 따로 둡니다. 사용자 토큰이 없으므로 경로의 userId로 사용자를 찾습니다.
- 인증: AI 서버 전용 키 X-Internal-Key로만 호출할 수 있어야 합니다.
  - 경로가 /internal 아래가 아니라 앱 API와 같은 곳에 열려 있으므로, **사용자 토큰(Bearer)으로 호출하면 403**을 줍니다. 그렇지 않으면 앱 사용자가 다른 사람의 생년월일·지역을 조회할 수 있습니다.
  - 가능하면 게이트웨이·보안 설정에서 AI 서버 IP만 이 경로를 호출하도록 막습니다.
- **GET /users/me에서 빼는 값**: name, phoneNumber, loginType, fontSize, notificationEnabled, createdAt (AI에 필요 없는 개인정보·앱 설정)
- 온보딩 전이면 GET /users/me처럼 onboardingCompleted = false, profile = null로 줍니다(404 아님). AI는 이 경우 법적 점검과 userRelevance만 건너뛰고 분석은 계속합니다.
- birthDate가 null이면(카카오 가입 후 온보딩 전) AI는 나이 기준 점검만 건너뜁니다.
- profile.region은 O02 도로명주소 검색 결과에서 시·도, 시·군·구만 줍니다. 상세 주소는 넣지 않습니다.
- consents.overseasTransfer가 false면 AI는 외부 LLM API에 사용자 정보를 보내지 않습니다.
- AI 서버는 이 정보를 저장하지 않고 요청 처리에만 씁니다.

> 참고: 원본 본문은 `GET /users/me`(복수형)로 적혀 있지만, 실제 내 정보 조회 API 경로는 `GET /user/me`입니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| X-Internal-Key | O | AI 서버 전용 내부 키 (AI 서버만 보유) |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| userId | number | O | 1024 |

**Body**: 없음

### Response

**200 OK**

```json
{
  "userId": 1024,
  "birthDate": "2001-03-14",
  "onboardingCompleted": true,
  "consentRequired": false,
  "profile": {
    "nationality": "KR",
    "region": { "sido": "서울특별시", "sigungu": "마포구" },
    "housingType": "MONTHLY_RENT",
    "welfareType": "NONE",
    "updatedAt": "2026-11-18T10:50:00+09:00"
  },
  "consents": {
    "termsOfService": true,
    "privacyPolicy": true,
    "guardianConsent": false,
    "overseasTransfer": true,
    "marketing": false
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| userId | number | 사용자 ID |
| birthDate | string(date) \| null | 생년월일 (나이 계산용) |
| onboardingCompleted | boolean | 온보딩 설문 완료 여부 |
| consentRequired | boolean | 필수 동의 전인지 (true면 AI는 사용자 정보를 쓰지 않음) |
| profile | object \| null | 온보딩 설문 값. 온보딩 전이면 null |
| profile.nationality | string | KR(내국인) / FOREIGN(외국인) |
| profile.region | object | sido, sigungu (지역별 최우선변제 기준 등) |
| profile.housingType | string | MONTHLY_RENT(월세) / JEONSE(전세) / OWNED(자가) / DORMITORY(기숙사·고시원) / WITH_PARENTS(부모님 집) |
| profile.welfareType | string | BASIC_LIVELIHOOD(기초생활수급) / NEAR_POVERTY(차상위계층) / OTHER(기타) / NONE(해당 없음) |
| profile.updatedAt | string(datetime) | 설문 마지막 수정 시각 |
| consents | object | GET /users/me의 consents 그대로. AI는 guardianConsent, overseasTransfer를 사용 |

**401 Unauthorized**

```json
{ "code": "INVALID_INTERNAL_KEY", "message": "내부 키가 올바르지 않습니다." }
```

**403 Forbidden**

사용자 토큰(Bearer)으로 호출했을 때

```json
{ "code": "FORBIDDEN", "message": "접근 권한이 없습니다." }
```

**404 Not Found**

```json
{ "code": "USER_NOT_FOUND", "message": "사용자를 찾을 수 없습니다." }
```

**500 Internal Server Error**

```json
{ "code": "INTERNAL_ERROR", "message": "사용자 정보 조회 중 오류가 발생했습니다." }
```
