# AUTH API 명세

> 원본: 노션 「API명세서」 > AUTH DB
>
> 공통 규칙(인증·형식·에러·페이지네이션)은 [00-common.md](00-common.md), 코드표는 [02-enums.md](02-enums.md)를 참고합니다.
> 이 문서의 API는 모두 **인증 없이** 호출합니다 (로그아웃은 Authorization 선택).

## 목록

| # | 이름 | Method | Path |
| --- | --- | --- | --- |
| 1 | [인증번호 발송 (문자)](#1-인증번호-발송-문자) | POST | `/auth/phone/verifications` |
| 2 | [인증번호 검증](#2-인증번호-검증) | POST | `/auth/phone/verifications/confirm` |
| 3 | [회원가입](#3-회원가입) | POST | `/auth/signup` |
| 4 | [로그인](#4-로그인) | POST | `/auth/login` |
| 5 | [카카오 로그인](#5-카카오-로그인) | POST | `/auth/kakao` |
| 6 | [구글 로그인](#6-구글-로그인) | POST | `/auth/google` |
| 7 | [토큰 재발급](#7-토큰-재발급) | POST | `/auth/token/refresh` |
| 8 | [로그아웃](#8-로그아웃) | POST | `/auth/logout` |
| 9 | [비밀번호 재설정 요청](#9-비밀번호-재설정-요청) | POST | `/auth/password/reset-requests` |
| 10 | [비밀번호 변경](#10-비밀번호-변경) | POST | `/auth/password/resets` |

---

## 1. 인증번호 발송 (문자)

`POST /auth/phone/verifications`

### 개요

- 회원가입할 전화번호로 6자리 인증번호를 문자(SMS)로 보냅니다.
- 화면: A01 "인증번호 받기", A02 "인증번호 다시 받기"

### 기술 설명/요구 사항

- 전화번호는 하이픈 없이 숫자만 보냅니다 (`01012345678`).
- 이미 가입된 전화번호면 보내지 않고 409를 줍니다.
- 인증번호는 숫자 6자리, 유효시간 3분입니다. A02의 남은 시간 타이머는 응답의 `expiresIn`으로 시작합니다.
- A02 안내 문구(010-\*\*\*\*-3576)는 앱이 입력한 전화번호를 가려서 보여 줍니다.
- 다시 받으면 이전 인증번호는 바로 무효가 됩니다.
- 재발송은 60초 뒤부터 가능하고, 같은 전화번호로 하루 10회까지 보낼 수 있습니다. 넘으면 429. 문자 발송 비용이 들어서 같은 IP에서 보내는 횟수도 제한합니다.
- 인증번호는 Redis에 TTL 3분으로 저장하고, 원문 대신 해시로 저장합니다.
- 문자 발송은 외부 SMS 서비스(확정 전)를 씁니다. 발송에 실패하면 502를 줍니다.
- 비밀번호 규칙(8자 이상, 숫자·특수문자 포함)과 비밀번호 확인 일치는 A01에서 앱이 먼저 검사합니다. 최종 검사는 회원가입 API에서 합니다.
- 비밀번호 재설정 코드는 이 API가 아니라 비밀번호 재설정 요청 API(`/auth/password/reset-requests`)로 보냅니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "phoneNumber": "01012345678"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| phoneNumber | string | O | 가입할 전화번호 (숫자만, 10–11자리) |

### Response

**200 OK**

```json
{
  "phoneNumber": "01012345678",
  "expiresIn": 180,
  "resendAvailableIn": 60
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| phoneNumber | string | 인증번호를 보낸 전화번호 |
| expiresIn | number | 인증번호 유효시간(초) |
| resendAvailableIn | number | 다시 받을 수 있을 때까지 남은 시간(초) |

**400 Bad Request**

```json
{ "code": "INVALID_PHONE_NUMBER", "message": "전화번호를 확인해 주세요." }
```

**409 Conflict**

```json
{ "code": "PHONE_ALREADY_EXISTS", "message": "이미 가입된 전화번호예요. 로그인해 주세요." }
```

**429 Too Many Requests**

```json
{ "code": "TOO_MANY_REQUESTS", "message": "잠시 후 다시 받아 주세요." }
```

**502 Bad Gateway**

```json
{ "code": "SMS_SEND_FAILED", "message": "문자를 보내지 못했어요. 잠시 후 다시 시도해 주세요." }
```

---

## 2. 인증번호 검증

`POST /auth/phone/verifications/confirm`

### 개요

- 문자로 받은 6자리 인증번호가 맞는지 확인하고, 다음 단계에서 쓸 `verificationToken`을 줍니다.
- 화면: A02 "인증하고 계속하기" (회원가입), L02 다음의 전화번호 인증 화면 "인증하고 계속하기" (비밀번호 재설정)

### 기술 설명/요구 사항

- `purpose`로 어떤 인증인지 구분합니다.
  - `SIGNUP`: 인증번호 발송 API로 보낸 번호를 검사합니다.
  - `PASSWORD_RESET`: 비밀번호 재설정 요청 API로 보낸 번호를 검사합니다.
- 맞으면 `verificationToken`(1회용, 10분)을 줍니다. SIGNUP은 회원가입 API에, PASSWORD_RESET은 비밀번호 변경 API에 그대로 넣습니다.
- 토큰은 phoneNumber·purpose와 묶여 있어서 다른 전화번호나 다른 용도로 쓸 수 없습니다.
- 5번 틀리면 그 인증번호는 무효가 되고 다시 받아야 합니다.
- 3분이 지나면 `CODE_EXPIRED`를 줍니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "phoneNumber": "01012345678",
  "code": "246813",
  "purpose": "SIGNUP"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| phoneNumber | string | O | 인증번호를 받은 전화번호 (숫자만) |
| code | string | O | 숫자 6자리 |
| purpose | string | O | `SIGNUP` / `PASSWORD_RESET` |

### Response

**200 OK**

```json
{
  "verificationToken": "vt_3f9c0e7a1b2d4c5e",
  "expiresIn": 600
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| verificationToken | string | 다음 단계에서 쓸 1회용 토큰 |
| expiresIn | number | 토큰 유효시간(초) |

**400 Bad Request**

```json
{ "code": "INVALID_CODE", "message": "인증번호가 맞지 않아요. (남은 횟수 3번)" }
```

```json
{ "code": "CODE_EXPIRED", "message": "시간이 만료됐어요. 인증번호를 다시 받아 주세요." }
```

**404 Not Found**

```json
{ "code": "VERIFICATION_NOT_FOUND", "message": "인증번호를 먼저 받아 주세요." }
```

**429 Too Many Requests**

```json
{ "code": "TOO_MANY_ATTEMPTS", "message": "5번 틀려서 인증번호가 무효가 됐어요. 다시 받아 주세요." }
```

---

## 3. 회원가입

`POST /auth/signup`

### 개요

- 전화번호로 계정을 만들고 바로 로그인 토큰을 줍니다.
- 흐름: 전화번호·이름·생년월일·비밀번호 입력 → 문자 인증 → 약관·개인정보 수집 동의 → 회원가입 → 온보딩 설문
- 화면: A01 처음 만나는 차곡 "인증번호 받기", A02 문자를 확인해 주세요, A03 "동의하고 가입 완료"

### 기술 설명/요구 사항

- 흐름: 인증번호 발송 → 인증번호 검증(`purpose=SIGNUP`) → 회원가입
- `verificationToken`을 받은 전화번호와 `phoneNumber`가 같아야 합니다.
- 전화번호는 하이픈 없이 숫자만 보냅니다 (`01012345678`). 앱이 입력값에서 하이픈을 지웁니다.
- 이름은 1–20자, 생년월일은 `YYYY-MM-DD`이며 오늘 이후 날짜는 안 됩니다. 생년월일은 만 14세 판단과 AI 법적 점검(나이)에 씁니다.
- 비밀번호: 8–64자, 숫자 1개 이상, 특수문자(`!`, `@`, `#` 등) 1개 이상. BCrypt로 저장합니다. 비밀번호 확인 일치는 A01에서 앱이 검사합니다 ("비밀번호가 다릅니다").
- A03 동의 항목: 전체 동의, 서비스 이용약관(필수), 개인정보 수집·이용(필수), 마케팅 수신(선택)
- 필수 동의(`termsOfService`, `privacyPolicy`)가 false면 400이며 가입되지 않습니다 (앱은 A03에서 동의 없이는 다음으로 못 가게 막음).
- `privacyPolicy`는 이름·생년월일·전화번호와 온보딩 설문(국적·거주 지역·주거 형태·복지 대상 여부)의 수집·이용을 함께 다룹니다.
- 만 14세 미만(가입일 기준 만 나이)이면 `guardianConsent`(보호자 동의)가 true여야 합니다. 아니면 400입니다.
  - 확정 전: A03 디자인에는 보호자 동의 항목이 없습니다. 개인정보보호법상 만 14세 미만은 보호자가 실제로 동의했는지 확인해야 해서, 체크박스만으로는 부족할 수 있습니다 (예: 보호자 휴대폰 본인인증). 방식이 정해지면 보호자 인증 값을 추가합니다.
- `overseasTransfer`(국외 이전, 외부 AI API)는 선택입니다. false면 AI가 외부 LLM에 프로필 정보를 보내지 않습니다.
  - 확정 전: A03 디자인에 국외 이전 항목이 없습니다. 보내지 않으면 false로 기록합니다.
- 동의는 약관 버전·동의 시각과 함께 동의 이력에 남깁니다 (약관 동의 기록 API와 같은 테이블).
- 기본 박스 5개(집 · 임대, 자동차, 세금, 병원 · 보험, 통신 · 구독)와 알림 설정 기본값을 만듭니다.
- 가입하면 바로 로그인 상태입니다. 토큰은 자동 로그인 상태(Refresh Token 14일)로 줍니다.
- 응답의 `onboardingCompleted`가 false이므로 앱은 온보딩 설문(O01)으로 이동합니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "phoneNumber": "01012345678",
  "name": "이차곡",
  "birthDate": "2001-03-14",
  "password": "chagok1234!",
  "verificationToken": "vt_3f9c0e7a1b2d4c5e",
  "consents": {
    "termsOfService": true,
    "privacyPolicy": true,
    "guardianConsent": false,
    "overseasTransfer": false,
    "marketing": false
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| phoneNumber | string | O | 전화번호 (숫자만, 10–11자리) |
| name | string | O | 이름 (1–20자) |
| birthDate | string(date) | O | 생년월일 |
| password | string | O | 8–64자, 숫자·특수문자 포함 |
| verificationToken | string | O | 인증번호 검증 API에서 받은 토큰 |
| `consents.termsOfService` | boolean | O | 서비스 이용약관 (필수) |
| `consents.privacyPolicy` | boolean | O | 개인정보 수집·이용 (필수, 온보딩 설문 포함) |
| `consents.guardianConsent` | boolean | 조건부 | 보호자 동의 (만 14세 미만이면 필수) |
| `consents.overseasTransfer` | boolean | X | 국외 이전 (외부 AI API, 선택. 확정 전) |
| `consents.marketing` | boolean | X | 마케팅 수신 (선택) |

### Response

**201 Created**

```json
{
  "userId": 1024,
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresIn": 3600,
  "refreshTokenExpiresIn": 1209600,
  "onboardingCompleted": false
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| userId | number | 사용자 ID |
| accessToken | string | API 호출용 토큰 |
| refreshToken | string | 토큰 재발급용 토큰 |
| accessTokenExpiresIn | number | Access Token 유효시간(초) |
| refreshTokenExpiresIn | number | Refresh Token 유효시간(초) |
| onboardingCompleted | boolean | 온보딩 설문 완료 여부 (false면 온보딩 설문으로 이동) |

**400 Bad Request**

```json
{ "code": "REQUIRED_CONSENT_MISSING", "message": "약관과 개인정보 수집에 동의해야 가입할 수 있어요." }
```

```json
{ "code": "GUARDIAN_CONSENT_REQUIRED", "message": "만 14세 미만은 보호자 동의가 필요해요." }
```

```json
{ "code": "INVALID_PASSWORD", "message": "비밀번호는 8자 이상, 숫자와 특수문자를 포함해 주세요." }
```

```json
{ "code": "INVALID_BIRTH_DATE", "message": "생년월일을 확인해 주세요." }
```

**401 Unauthorized**

```json
{ "code": "INVALID_VERIFICATION_TOKEN", "message": "전화번호 인증이 만료됐어요. 다시 인증해 주세요." }
```

**409 Conflict**

```json
{ "code": "PHONE_ALREADY_EXISTS", "message": "이미 가입된 전화번호예요." }
```

---

## 4. 로그인

`POST /auth/login`

### 개요

- 전화번호와 비밀번호로 로그인합니다.
- 화면: L01 다시 만나 반가워요 "로그인" (자동 로그인 체크 포함)

### 기술 설명/요구 사항

- 전화번호는 하이픈 없이 숫자만 보냅니다 (`01012345678`).
- `autoLogin=true`면 Refresh Token 14일, false면 1일입니다. false일 때 앱은 앱을 완전히 종료하면 저장한 토큰을 지웁니다.
- 가입하지 않은 전화번호인지 비밀번호가 틀렸는지 구분하지 않고 같은 에러를 줍니다 (계정 존재 여부 노출 방지).
- 같은 전화번호로 5번 연속 실패하면 10분 동안 로그인을 막습니다 (429).
- 카카오·구글로 가입한 계정은 비밀번호가 없어 이 API로 로그인할 수 없습니다 (같은 401).
- 응답의 `onboardingCompleted`가 false면 온보딩(O01)으로, true면 홈으로 이동합니다.
- 로그인 후 앱은 푸시 토큰 등록 API를 부릅니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "phoneNumber": "01012345678",
  "password": "chagok1234!",
  "autoLogin": true
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| phoneNumber | string | O | 전화번호 (숫자만) |
| password | string | O | 비밀번호 |
| autoLogin | boolean | X | 자동 로그인 (기본값 true) |

### Response

**200 OK**

```json
{
  "userId": 1024,
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresIn": 3600,
  "refreshTokenExpiresIn": 1209600,
  "onboardingCompleted": true
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| userId | number | 사용자 ID |
| accessToken | string | API 호출용 토큰 |
| refreshToken | string | 토큰 재발급용 토큰 |
| accessTokenExpiresIn | number | Access Token 유효시간(초) |
| refreshTokenExpiresIn | number | Refresh Token 유효시간(초). autoLogin=false면 86400 |
| onboardingCompleted | boolean | 온보딩 완료 여부 |

**401 Unauthorized**

```json
{ "code": "INVALID_CREDENTIALS", "message": "전화번호 또는 비밀번호가 맞지 않아요." }
```

**429 Too Many Requests**

```json
{ "code": "LOGIN_LOCKED", "message": "로그인을 5번 실패했어요. 10분 뒤에 다시 시도해 주세요." }
```

---

## 5. 카카오 로그인

`POST /auth/kakao`

### 개요

- 카카오 계정으로 로그인합니다. 처음이면 카카오에서 이름·생년월일·전화번호를 받아 계정을 만듭니다.
- 화면: L01 "kakao 로그인"

### 기술 설명/요구 사항

- 앱이 카카오 SDK로 받은 액세스 토큰을 보내면, BE가 카카오 사용자 정보 API(`/v2/user/me`)로 검증하고 카카오 회원번호로 사용자를 찾습니다.
- 처음 로그인하면 카카오 정보로 계정을 만듭니다.
  - 이름 ← `kakao_account.name`
  - 생년월일 ← `kakao_account.birthyear` + `kakao_account.birthday`
  - 전화번호 ← `kakao_account.phone_number` (`+82 10-1234-5678` → `01012345678`로 바꿔 저장)
- 이름·출생연도·생일·전화번호는 카카오 비즈 앱 전환과 동의항목 검수가 필요합니다. 사용자가 카카오 동의 화면에서 제공을 거부할 수도 있습니다. 못 받은 값은 `missingFields`로 알려 주고, 온보딩 설문에서 받습니다 (생년월일은 만 14세 판단에 필요).
- 새 사용자는 `consentRequired=true`입니다. 앱은 약관 동의(A03) → 약관 동의 기록 API → 온보딩 설문 순서로 이동합니다. 만 14세 미만이면 보호자 동의도 받습니다.
- 필수 동의 전에는 AUTH·USER 외 API가 403 `CONSENT_REQUIRED`를 줍니다. 동의하지 않고 나가면 가입이 끝나지 않은 것으로 보고, 24시간 안에 동의하지 않으면 계정을 지웁니다.
- 카카오 전화번호가 이미 전화번호 계정으로 가입돼 있으면 자동으로 합치지 않고 409를 줍니다.
- 구글 로그인은 같은 방식의 구글 로그인 API(`/auth/google`)를 씁니다.
- `autoLogin`은 L01 자동 로그인 체크 상태를 그대로 보냅니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "kakaoAccessToken": "kakao_xxxxxxxxxxxxxxxx",
  "autoLogin": true
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| kakaoAccessToken | string | O | 카카오 SDK 로그인으로 받은 액세스 토큰 |
| autoLogin | boolean | X | 자동 로그인 (기본값 true) |

### Response

**200 OK**

```json
{
  "userId": 1025,
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresIn": 3600,
  "refreshTokenExpiresIn": 1209600,
  "isNewUser": true,
  "consentRequired": true,
  "guardianConsentRequired": false,
  "missingFields": ["birthDate"],
  "onboardingCompleted": false
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| userId | number | 사용자 ID |
| accessToken / refreshToken | string | 토큰 |
| accessTokenExpiresIn / refreshTokenExpiresIn | number | 유효시간(초) |
| isNewUser | boolean | 이번에 계정을 새로 만들었는지 |
| consentRequired | boolean | true면 A03 약관 동의로 이동 |
| guardianConsentRequired | boolean | 만 14세 미만이라 보호자 동의가 필요한지 (생년월일을 못 받았으면 false, 온보딩에서 다시 판단) |
| missingFields | string[] | 카카오에서 받지 못한 값 (`name` / `birthDate` / `phoneNumber`). 온보딩 설문에서 받음 |
| onboardingCompleted | boolean | 온보딩 설문 완료 여부 |

**401 Unauthorized**

```json
{ "code": "INVALID_KAKAO_TOKEN", "message": "카카오 로그인이 만료됐어요. 다시 시도해 주세요." }
```

**409 Conflict**

```json
{ "code": "PHONE_ALREADY_EXISTS", "message": "이 전화번호로 가입한 계정이 있어요. 전화번호로 로그인해 주세요." }
```

**502 Bad Gateway**

```json
{ "code": "KAKAO_API_ERROR", "message": "카카오 서버와 연결하지 못했어요. 잠시 후 다시 시도해 주세요." }
```

---

## 6. 구글 로그인

`POST /auth/google`

### 개요

- 구글 계정으로 로그인합니다. 처음이면 구글에서 이름을 받아 계정을 만듭니다.
- 화면: L01 "Google 로그인"

### 기술 설명/요구 사항

- 앱이 구글 로그인 SDK로 받은 ID 토큰을 보내면, BE가 구글 공개키로 서명·`aud`(차곡 클라이언트 ID)·만료를 검증하고 구글 사용자 ID(`sub`)로 사용자를 찾습니다.
- 처음 로그인하면 구글 정보로 계정을 만듭니다.
  - 이름 ← ID 토큰의 `name`
  - 구글은 기본 범위로 생년월일·전화번호를 주지 않으므로 `missingFields`에 `birthDate`, `phoneNumber`가 들어갑니다. 온보딩 설문에서 생년월일을 받습니다 (만 14세 판단에 필요).
- 새 사용자는 `consentRequired=true`입니다. 앱은 약관 동의(A03) → 약관 동의 기록 API → 온보딩 설문 순서로 이동합니다. 만 14세 미만이면 보호자 동의도 받습니다.
- 필수 동의 전에는 AUTH·USER 외 API가 403 `CONSENT_REQUIRED`를 줍니다. 24시간 안에 동의하지 않으면 계정을 지웁니다.
- 나머지 규칙과 응답 형식은 카카오 로그인과 같습니다.
- `autoLogin`은 L01 자동 로그인 체크 상태를 그대로 보냅니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "googleIdToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
  "autoLogin": true
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| googleIdToken | string | O | 구글 로그인 SDK로 받은 ID 토큰 |
| autoLogin | boolean | X | 자동 로그인 (기본값 true) |

### Response

**200 OK**

```json
{
  "userId": 1026,
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresIn": 3600,
  "refreshTokenExpiresIn": 1209600,
  "isNewUser": true,
  "consentRequired": true,
  "guardianConsentRequired": false,
  "missingFields": ["birthDate", "phoneNumber"],
  "onboardingCompleted": false
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| userId | number | 사용자 ID |
| accessToken / refreshToken | string | 토큰 |
| accessTokenExpiresIn / refreshTokenExpiresIn | number | 유효시간(초) |
| isNewUser | boolean | 이번에 계정을 새로 만들었는지 |
| consentRequired | boolean | true면 A03 약관 동의로 이동 |
| guardianConsentRequired | boolean | 만 14세 미만이라 보호자 동의가 필요한지 (생년월일을 모르면 false, 온보딩에서 다시 판단) |
| missingFields | string[] | 구글에서 받지 못한 값 (`name` / `birthDate` / `phoneNumber`) |
| onboardingCompleted | boolean | 온보딩 설문 완료 여부 |

**401 Unauthorized**

```json
{ "code": "INVALID_GOOGLE_TOKEN", "message": "구글 로그인이 만료됐어요. 다시 시도해 주세요." }
```

**502 Bad Gateway**

```json
{ "code": "GOOGLE_API_ERROR", "message": "구글 서버와 연결하지 못했어요. 잠시 후 다시 시도해 주세요." }
```

---

## 7. 토큰 재발급

`POST /auth/token/refresh`

### 개요

- Access Token이 만료되면 Refresh Token으로 새 토큰을 받습니다.
- 화면: 직접 호출하는 화면은 없습니다. 모든 API에서 401 `TOKEN_EXPIRED`를 받으면 호출하고, 실패하면 L04 "다시 로그인해 주세요"로 갑니다.

### 기술 설명/요구 사항

- Refresh Token Rotation: 호출할 때마다 새 Refresh Token을 주고 이전 것은 폐기합니다.
- 새 Refresh Token은 다시 14일(자동 로그인을 껐으면 1일)입니다. 14일 동안 앱을 쓰지 않으면 L04가 뜹니다.
- 이미 폐기된 Refresh Token이 다시 들어오면 탈취로 보고 그 사용자의 모든 Refresh Token을 폐기합니다 (모든 기기 로그아웃).
- 여러 요청이 동시에 401을 받으면 앱은 재발급을 한 번만 호출하고, 나머지 요청은 기다렸다가 새 토큰으로 다시 보냅니다 (Rotation 때문에 동시에 부르면 하나는 실패함).
- 실패하면 앱은 저장한 토큰을 지우고 L04를 띄웁니다. 보관 중인 서류는 그대로입니다 (L04 "보관 중인 서류는 그대로예요").

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| refreshToken | string | O | 저장해 둔 Refresh Token |

### Response

**200 OK**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "accessTokenExpiresIn": 3600,
  "refreshTokenExpiresIn": 1209600
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| accessToken | string | 새 Access Token |
| refreshToken | string | 새 Refresh Token (이전 것은 폐기됨) |
| accessTokenExpiresIn | number | Access Token 유효시간(초) |
| refreshTokenExpiresIn | number | Refresh Token 유효시간(초) |

**401 Unauthorized**

```json
{ "code": "REFRESH_TOKEN_EXPIRED", "message": "로그인 유지 시간이 끝났어요. 다시 로그인해 주세요." }
```

```json
{ "code": "INVALID_REFRESH_TOKEN", "message": "다시 로그인해 주세요." }
```

---

## 8. 로그아웃

`POST /auth/logout`

### 개요

- 이 기기에서 로그아웃합니다.
- 화면: S04 "로그아웃"

### 기술 설명/요구 사항

- 보낸 Refresh Token을 폐기하고, Authorization의 Access Token은 만료될 때까지 블랙리스트(Redis)에 넣습니다.
- Access Token이 만료된 상태에서도 로그아웃할 수 있도록 Authorization은 선택입니다. 사용자는 Refresh Token으로 확인합니다.
- `deviceId`의 푸시 토큰을 지워서 이 기기로 납부 알림이 오지 않게 합니다.
- 서버의 서류·대화·납부 일정은 그대로 둡니다 (S04 "내 서류와 대화는 계정에 보관돼요"). 기기 안의 서류 목록 캐시·원본 이미지·로컬 알림은 앱이 지웁니다.
- 다른 기기의 로그인은 유지됩니다.
- 이미 폐기된 토큰이어도 204를 줍니다 (여러 번 불러도 같은 결과).

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |
| Authorization | X | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "deviceId": "8d1c3e0a-6b2f-4c1e-9a77-1f0c2b3d4e5f"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| refreshToken | string | O | 이 기기의 Refresh Token |
| deviceId | string | O | 푸시 토큰 등록 때 쓴 기기 ID |

### Response

**204 No Content**

응답 본문 없음

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "refreshToken은 필수예요." }
```

---

## 9. 비밀번호 재설정 요청

`POST /auth/password/reset-requests`

### 개요

- 비밀번호를 잊었을 때 가입 전화번호로 6자리 인증 코드를 문자로 보냅니다.
- 화면: L02 비밀번호를 잊으셨나요? "인증 코드 보내기"

### 기술 설명/요구 사항

- 흐름: 비밀번호 재설정 요청 → 전화번호 인증 화면에서 인증번호 검증(`purpose=PASSWORD_RESET`) → 비밀번호 변경(L03)
- 계정이 있는지 알려 주지 않도록, 가입하지 않은 전화번호여도 같은 200을 줍니다 (문자는 보내지 않음).
- 카카오·구글로 가입한 계정이면 코드 대신 "카카오(구글)로 로그인해 주세요" 안내 문자를 보냅니다 (계정에 전화번호가 있을 때만).
- 코드는 숫자 6자리, 유효시간 3분입니다. 다시 요청하면 이전 코드는 무효입니다 (인증 화면 "인증번호 다시 받기"도 이 API를 다시 부름).
- 60초 뒤부터 다시 요청할 수 있고, 같은 전화번호로 하루 10회까지입니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "phoneNumber": "01012345678"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| phoneNumber | string | O | 가입할 때 쓴 전화번호 (숫자만) |

### Response

**200 OK**

```json
{
  "expiresIn": 180,
  "resendAvailableIn": 60
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| expiresIn | number | 코드 유효시간(초) |
| resendAvailableIn | number | 다시 요청할 수 있을 때까지 남은 시간(초) |

**400 Bad Request**

```json
{ "code": "INVALID_PHONE_NUMBER", "message": "전화번호를 확인해 주세요." }
```

**429 Too Many Requests**

```json
{ "code": "TOO_MANY_REQUESTS", "message": "잠시 후 다시 요청해 주세요." }
```

---

## 10. 비밀번호 변경

`POST /auth/password/resets`

### 개요

- 인증을 마친 사용자의 비밀번호를 새 비밀번호로 바꿉니다 (로그인 전, 비밀번호 재설정).
- 화면: L03 새 비밀번호 만들기 "비밀번호 변경"

### 기술 설명/요구 사항

- `verificationToken`은 인증번호 검증 API에서 `purpose=PASSWORD_RESET`으로 받은 토큰이어야 하고, `phoneNumber`와 같은 전화번호여야 합니다.
- 비밀번호 규칙: 8–64자, 숫자 1개 이상, 특수문자 1개 이상. 새 비밀번호 확인 일치는 L03에서 앱이 검사합니다.
- 이전 비밀번호와 같으면 400 (L03 "이전과 다른 비밀번호로").
- 바꾸면 모든 기기의 Refresh Token을 폐기합니다. 앱은 로그인 화면(L01)으로 이동합니다.
- 본인이 바꾸지 않았을 때 알아챌 수 있도록 비밀번호 변경 안내 문자를 보냅니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "phoneNumber": "01012345678",
  "verificationToken": "vt_9a1e2c7b5d3f4e6a",
  "newPassword": "chagok5678!"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| phoneNumber | string | O | 가입 전화번호 (숫자만) |
| verificationToken | string | O | 인증번호 검증 API(`PASSWORD_RESET`)에서 받은 토큰 |
| newPassword | string | O | 새 비밀번호 (8–64자, 숫자·특수문자 포함) |

### Response

**204 No Content**

응답 본문 없음

**400 Bad Request**

```json
{ "code": "INVALID_PASSWORD", "message": "비밀번호는 8자 이상, 숫자와 특수문자를 포함해 주세요." }
```

```json
{ "code": "SAME_AS_PREVIOUS_PASSWORD", "message": "이전과 다른 비밀번호로 바꿔 주세요." }
```

**401 Unauthorized**

```json
{ "code": "INVALID_VERIFICATION_TOKEN", "message": "인증이 만료됐어요. 인증 코드를 다시 받아 주세요." }
```
