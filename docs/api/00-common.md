# 공통 규칙

> 원본: 노션 「API명세서」 > 공통 규칙
>
> APP ↔ BE API 명세입니다. 화면 번호(A01, U04, 04–16 등)는 피그마 화면 이름입니다. BE ↔ AI 서버 API(X-API-Key·X-Internal-Key 인증)도 각 도메인 문서 안에 함께 있습니다.

## 인증

- 인증이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더를 보냅니다.
- 인증이 필요 없는 API: 회원가입, 로그인, 카카오·구글 로그인, 전화번호 인증번호 발송·검증, 비밀번호 재설정 요청·변경, 토큰 재발급
- 계정 식별자는 전화번호입니다. 하이픈 없이 숫자만 주고받습니다 (`01012345678`). 화면 표기(010-1234-5678)는 앱에서 합니다.
- Access Token 1시간, Refresh Token 14일 (자동 로그인을 끄면 1일)
- Access Token이 만료되면 401 `TOKEN_EXPIRED`를 줍니다. 앱은 토큰 재발급 후 원래 요청을 다시 보냅니다. 재발급도 401이면 L04 "다시 로그인해 주세요" 화면으로 갑니다.
- 카카오·구글로 처음 가입한 사용자가 필수 약관에 동의하기 전에는 AUTH·USER 외 API가 403 `CONSENT_REQUIRED`를 줍니다.

## 형식

- Content-Type: `application/json` (서류 업로드만 `multipart/form-data`)
- 일시: ISO-8601, KST 오프셋 포함 `2026-11-24T09:00:00+09:00`
- 날짜 `2026-11-25` / 시각 `09:00` / 월 `2026-11`
- 금액: 원 단위 정수 `500000`. 화면 표기(500,000원)는 앱에서 합니다.
- ID: 숫자(Long). AI 서버와 주고받을 때는 문자열로 바꿉니다 ([BE ↔ AI 서버 연동](01-ai-integration.md) 참고).
- 값이 없으면 필드를 빼지 않고 `null`을 보냅니다.

## 에러 응답

AI 서버와 같은 형식입니다. `message`는 화면에 그대로 띄울 수 있는 문장입니다.

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "서류를 찾을 수 없어요." }
```

| HTTP | code | 설명 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 필수 값 누락·형식 오류 (API마다 더 구체적인 code가 있음) |
| 401 | `UNAUTHORIZED` | 토큰이 없거나 잘못됨 |
| 401 | `TOKEN_EXPIRED` | Access Token 만료 → 토큰 재발급 |
| 403 | `CONSENT_REQUIRED` | 필수 약관 미동의 |
| 404 | `*_NOT_FOUND` | 없는 리소스. 다른 사용자의 리소스도 403 대신 404로 응답해 존재 여부를 숨깁니다. |
| 422 | `NOT_COMPARABLE` | AI가 비교할 수 없다고 판단 (서류 비교·문자 생성) |
| 429 | `TOO_MANY_REQUESTS` | 요청 횟수 제한 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 오류 |
| 502 | `AI_ERROR` | AI 서버 오류 |
| 504 | `AI_TIMEOUT` | AI 서버 응답 시간 초과 |

## 페이지네이션

목록 API는 `page`(0부터)와 `size`(기본 20, 최대 50)를 받고 아래 형식으로 응답합니다.

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 3, "hasNext": false }
```
