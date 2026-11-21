# COMPARISON API 명세

> 원본: 노션 「API명세서」 > COMPARISON DB
>
> 공통 규칙(인증·형식·에러·페이지네이션)은 [00-common.md](00-common.md), 코드표(비교 결과 status, 문자 tone)는 [02-enums.md](02-enums.md), AI 연동 규칙은 [01-ai-integration.md](01-ai-integration.md)를 참고합니다.

## 목록

| # | 이름 | Method | Path | 호출 주체 |
| --- | --- | --- | --- | --- |
| 1 | [서류 비교 요청](#1-서류-비교-요청) | POST | `/comparisons` | APP |
| 2 | [비교 결과 조회](#2-비교-결과-조회) | GET | `/comparisons/{id}` | APP |
| 3 | [서류 비교 (AI 서버 API)](#3-서류-비교-ai-서버-api) | POST | `/ai/comparisons` | BE → AI 서버 (X-API-Key) |
| 4 | [확인 요청 문자 생성 (AI 서버 API)](#4-확인-요청-문자-생성-ai-서버-api) | POST | `/ai/comparisons/{id}/inquiry-messages` | BE → AI 서버 (X-API-Key) |
| - | 확인 요청 문자 만들기 (BE, 노션 페이지 없음) | POST | `/comparisons/{id}/inquiry-messages` | APP (C03) |

---

## 1. 서류 비교 요청

`POST /comparisons`

> 담당: BE

### 개요

- 계약서와 고지서(또는 광고)를 비교해 약속과 다른 금액·조건을 찾습니다.
- 화면: C01 서류 두 장을 비교해요 "차이 확인하기" → C02 약속과 다른 금액이 있어요

### 기술 설명/요구 사항

- `compareType`
  - `BILL` (고지서와 비교): 계약 금액과 실제 청구 금액 비교 → 초과 추정액. 예) 통신 서비스 계약서 월 33,000원 ↔ 11월 고지서 45,000원
  - `AD` (광고와 비교): 광고에서 약속한 조건과 계약서 실제 조건 비교. 예) 광고 "평생 월 29,900원" ↔ 계약서 "13개월부터 월 49,900원"
- BE 검사
  - 기준 서류는 `docCategory=CONTRACT`, 비교 서류는 BILL이면 `docCategory=BILL`, AD면 `docCategory=AD`여야 합니다.
  - 두 서류 모두 내 서류이고 `CONFIRMED`여야 합니다.
- BE가 AI 서류 비교 API를 부릅니다. 보내는 값은 `userId`, `baseDocumentId`, `targetDocumentId`, `compareType`(문자열 ID)뿐입니다. AI가 문서 분석 결과 조회 내부 API로 두 서류의 최신 값(사용자 수정 반영)을 직접 가져갑니다. 타임아웃은 25초입니다 (AI 20초 + 여유).
- 같은 서비스·같은 기간이 아니면 AI가 422 `NOT_COMPARABLE`을 주고, BE도 422로 그대로 줍니다 (C01 "같은 서비스와 같은 기간의 서류를 골라 주세요").
- AI는 비교 결과를 저장하지 않습니다. BE가 AI 응답을 그대로 저장하고, 비교 결과 ID·서류 정보·`outdated`를 붙여 줍니다. 부를 때마다 새 비교 결과를 만듭니다.
- 저장하면 알림 목록에 `COMPARISON_COMPLETED` 알림("{기준 서류 이름} 비교가 완료됐어요", 읽음 상태)을 남깁니다. 푸시는 보내지 않습니다. M00 최근 알림에서 지난 비교 결과로 다시 들어갈 수 있게 하는 용도입니다.
- C02 화면
  - 초과 추정액 카드 → `highlight` (차이가 없으면 null)
  - 비교 표 → `comparisons[]` (base / target / difference)
  - 확인 필요 안내 → `notices[]`
  - "원본 근거 보기" → `comparisons[].evidence` (page·bbox) + 서류 원본 조회
  - 추정 안내 → `disclaimer` (반드시 표시)

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
  "baseDocumentId": 488,
  "targetDocumentId": 533,
  "compareType": "BILL"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| baseDocumentId | number | O | 기준 서류 (계약서) |
| targetDocumentId | number | O | 비교할 서류 (고지서·광고) |
| compareType | string | O | `BILL` / `AD` |

### Response

**201 Created**

```json
{
  "comparisonId": 77,
  "compareType": "BILL",
  "status": "MISMATCH",
  "headline": "약속과 다른 금액이 있어요",
  "baseDocument": { "documentId": 488, "title": "통신 서비스 계약서", "docType": "SERVICE_CONTRACT", "boxName": "통신 · 구독" },
  "targetDocument": { "documentId": 533, "title": "11월 통신요금 고지서", "docType": "BILL", "boxName": "통신 · 구독" },
  "highlight": {
    "label": "이번 달 초과 추정액",
    "amount": 12000,
    "value": "12,000원",
    "formula": "청구액 45,000원 - 약정액 33,000원"
  },
  "comparisons": [
    {
      "title": "통신요금 비교",
      "itemKey": "monthlyFee",
      "base": { "label": "계약서", "value": "월 33,000원", "amount": 33000 },
      "target": { "label": "11월 고지서", "value": "45,000원", "amount": 45000 },
      "difference": { "label": "차이", "value": "+12,000원", "amount": 12000 },
      "evidence": [
        { "documentId": 488, "text": "월 이용료 33,000원(VAT 포함)", "page": 1, "bbox": [0.10, 0.40, 0.55, 0.03] },
        { "documentId": 533, "text": "당월 청구금액 45,000원", "page": 1, "bbox": [0.52, 0.30, 0.40, 0.04] }
      ]
    }
  ],
  "notices": [
    {
      "severity": "WARNING",
      "title": "확인 필요 · 약정 금액보다 높아요.",
      "description": "추가 사용료나 할인 종료 여부를 확인해 주세요.",
      "possibleReasons": ["EXTRA_USAGE", "DISCOUNT_ENDED"]
    }
  ],
  "disclaimer": "추정 결과이며 실제 과오납 여부는 발급처 확인이 필요해요.",
  "outdated": false,
  "createdAt": "2026-11-18T10:30:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| comparisonId | number | 비교 결과 ID (BE) |
| status | string | `MISMATCH`(약속과 다름) / `MATCH`(일치) / `NEEDS_CHECK`(비교 항목이 불분명) |
| headline | string | 결과 화면 제목 |
| baseDocument / targetDocument | object | 비교한 두 서류 (BE) |
| highlight | object | 강조 카드 (label, amount, value, formula). 차이가 없으면 null |
| comparisons[] | array | 항목별 비교 (title, itemKey, base, target, difference, evidence) |
| comparisons[].difference | object | 금액 차이. AD에서 조건 차이면 value만 (예: "평생 → 12개월") |
| notices[] | array | 이상 징후 안내 (severity `WARNING` / `INFO`, title, description, possibleReasons) |
| disclaimer | string | 추정 결과 안내 (반드시 표시) |
| outdated | boolean | 비교한 뒤 서류가 수정되면 true (BE) |

`status`·`headline`·`highlight`·`comparisons`·`notices`·`disclaimer`는 AI 서류 비교 응답 그대로입니다. AI의 문서 ID(문자열)는 BE가 숫자 ID로 바꿔서 줍니다.

**400 Bad Request**

```json
{ "code": "INVALID_COMPARE_PAIR", "message": "계약서와 고지서(또는 광고)를 골라 주세요." }
```

```json
{ "code": "SAME_DOCUMENT", "message": "서로 다른 서류를 골라 주세요." }
```

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "서류를 찾을 수 없어요." }
```

**409 Conflict**

```json
{ "code": "DOCUMENT_NOT_READY", "message": "분석과 확인이 끝난 서류만 비교할 수 있어요." }
```

**422 Unprocessable Entity**

```json
{ "code": "NOT_COMPARABLE", "message": "같은 서비스와 같은 기간의 서류를 골라 주세요.", "reason": "DIFFERENT_PROVIDER" }
```

**502 Bad Gateway**

```json
{ "code": "AI_ERROR", "message": "비교 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요." }
```

**504 Gateway Timeout**

```json
{ "code": "AI_TIMEOUT", "message": "비교 시간이 오래 걸리고 있어요. 다시 시도해 주세요." }
```

---

## 2. 비교 결과 조회

`GET /comparisons/{id}`

### 개요

- 저장된 비교 결과를 다시 조회합니다.
- 화면: C02 약속과 다른 금액이 있어요 (다시 열기), C03 "비교 결과로 돌아가기"

### 기술 설명/요구 사항

- 응답은 서류 비교 요청(`POST /comparisons`)의 201 응답과 같은 형식입니다 (AI 서류 비교 응답 + BE 필드).
- 비교한 뒤 두 서류 중 하나라도 내용을 고치면 `outdated=true`입니다. 앱은 "서류 내용이 바뀌었어요. 다시 비교해 주세요."를 띄우고, 다시 비교는 서류 비교 요청 API로 새로 만듭니다.
- 비교에 쓴 서류를 삭제하면 비교 결과도 삭제되어 404입니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 77 |

**Body**: 없음

### Response

**200 OK**

서류 비교 요청의 201 응답과 같습니다.

```json
{
  "comparisonId": 77,
  "compareType": "BILL",
  "status": "MISMATCH",
  "headline": "약속과 다른 금액이 있어요",
  "baseDocument": { "documentId": 488, "title": "통신 서비스 계약서", "docType": "SERVICE_CONTRACT", "boxName": "통신 · 구독" },
  "targetDocument": { "documentId": 533, "title": "11월 통신요금 고지서", "docType": "BILL", "boxName": "통신 · 구독" },
  "highlight": { "label": "이번 달 초과 추정액", "amount": 12000, "value": "12,000원", "formula": "청구액 45,000원 - 약정액 33,000원" },
  "comparisons": [
    {
      "title": "통신요금 비교",
      "itemKey": "monthlyFee",
      "base": { "label": "계약서", "value": "월 33,000원", "amount": 33000 },
      "target": { "label": "11월 고지서", "value": "45,000원", "amount": 45000 },
      "difference": { "label": "차이", "value": "+12,000원", "amount": 12000 },
      "evidence": [{ "documentId": 488, "text": "월 이용료 33,000원(VAT 포함)", "page": 1, "bbox": [0.10, 0.40, 0.55, 0.03] }]
    }
  ],
  "notices": [
    { "severity": "WARNING", "title": "확인 필요 · 약정 금액보다 높아요.", "description": "추가 사용료나 할인 종료 여부를 확인해 주세요.", "possibleReasons": ["EXTRA_USAGE", "DISCOUNT_ENDED"] }
  ],
  "disclaimer": "추정 결과이며 실제 과오납 여부는 발급처 확인이 필요해요.",
  "outdated": true,
  "createdAt": "2026-11-18T10:30:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| outdated | boolean | true면 "다시 비교해 주세요" 안내 |
| 나머지 | - | 서류 비교 요청 응답 표 참고 |

**404 Not Found**

```json
{ "code": "COMPARISON_NOT_FOUND", "message": "비교 결과를 찾을 수 없어요." }
```

---

## 3. 서류 비교 (AI 서버 API)

`POST /ai/comparisons`

> **BE → AI 서버** 호출 API입니다 (X-API-Key). 앱에서는 호출하지 않습니다. 담당: BE, AI
>
> 참고: 서류 비교 요청(BE) 설명에는 "보내는 값은 `userId`, `baseDocumentId`, `targetDocumentId`, `compareType`(문자열 ID)뿐"이라고 되어 있지만, 이 AI 페이지는 `comparisonId`를 포함하고 ID를 숫자(number)로 받습니다. 원본 그대로 옮겼으니 확정이 필요합니다.

### 개요

- 분석이 끝난 서류 두 개를 비교해서 약속과 다른 금액·조건을 찾아줍니다.
- 기준 서류(계약서) vs 비교할 서류(고지서 또는 광고)
  - **고지서와 비교**(BILL): 계약 금액과 실제 청구 금액 비교 → 초과 추정액. 예) 계약서 월 33,000원 vs 11월 고지서 45,000원 → +12,000원
  - **광고와 비교**(AD): 광고에서 약속한 조건과 계약서 실제 조건 비교. 예) 광고 "평생 월 29,900원" vs 계약서 "13개월부터 월 49,900원"
- 비교 결과 화면(C02)에 필요한 데이터를 줍니다. 확인 요청 문자(C03)는 `POST /ai/comparisons/{id}/inquiry-messages`로 만듭니다.

### 기술 설명/요구 사항

- 흐름: APP → BE(`POST /comparisons`) → AI 서버(`POST /ai/comparisons`). BE가 비교 ID를 만들고, AI 응답을 그대로 저장해서 `GET /comparisons/{id}`로 앱에 돌려줍니다.
- BE는 비교 ID와 문서 ID 두 개만 보내고, AI 서버가 BE 서류 상세 조회 `GET /documents/{id}?userId=`를 X-Internal-Key로 두 번 호출해 분석 결과(사용자 수정 반영)를 가져옵니다.
- 두 문서 모두 문서 분석이 끝난 상태여야 합니다. 두 문서의 userId가 요청 userId와 같아야 합니다.
- **처리 순서**
  1. 비교 가능 여부 확인: 같은 서비스·같은 상대방인지(회사명, 상품명, 고객번호), 고지서 청구 기간이 계약 기간 안인지. 다르면 422
  2. 항목 짝짓기(LLM): 계약서 항목과 고지서·광고 항목을 매칭 (예: 계약서 "월 이용료" ↔ 고지서 "기본료 + 부가사용료 - 약정할인")
  3. 금액 계산(코드): 차이·초과 추정액은 LLM이 아니라 코드로 계산합니다. 부가세 포함 여부가 다르면 맞춰서 비교하고, 청구 기간이 한 달이 안 되면 일할 계산합니다.
  4. 이상 징후 문구(LLM): 차이가 생긴 가능한 이유(추가 사용료, 할인 종료, 약정 위반 등)와 확인할 일
- **결과 상태(status)**: MISMATCH(약속과 다름) / MATCH(일치) / NEEDS_CHECK(비교 항목이 불분명)
- **표기**: 금액은 콤마 + 원, 차이는 부호 포함(+12,000원). 원 단위 숫자(amount)도 같이 줍니다.
- 모든 결과는 추정이므로 disclaimer를 넣습니다. AI 서버는 비교 결과를 저장하지 않고, 저장이 필요하면 BE가 합니다.
- 타임아웃 20초.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |
| X-API-Key | O | AI 서버 내부 키 (BE만 보유) |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body**

```json
{
  "comparisonId": 301,
  "userId": 1024,
  "baseDocumentId": 501,
  "targetDocumentId": 511,
  "compareType": "BILL"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| comparisonId | number | O | BE가 만든 비교 ID (응답에 그대로 돌려줌) |
| userId | number | O | 사용자 ID (두 문서 소유자 확인) |
| baseDocumentId | number | O | 기준 서류 ID (계약서) |
| targetDocumentId | number | O | 비교할 서류 ID (고지서 또는 광고) |
| compareType | string | O | BILL(고지서와 비교) / AD(광고와 비교) |

### Response

**200 OK**

```json
{
  "comparisonId": 301,
  "baseDocumentId": 501,
  "targetDocumentId": 511,
  "compareType": "BILL",
  "status": "MISMATCH",
  "headline": "약속과 다른 금액이 있어요",
  "highlight": {
    "label": "이번 달 초과 추정액",
    "amount": 12000,
    "value": "12,000원",
    "formula": "청구액 45,000원 - 약정액 33,000원"
  },
  "comparisons": [
    {
      "title": "통신요금 비교",
      "itemKey": "monthlyFee",
      "base": { "label": "계약서", "value": "월 33,000원", "amount": 33000 },
      "target": { "label": "11월 고지서", "value": "45,000원", "amount": 45000 },
      "difference": { "label": "차이", "value": "+12,000원", "amount": 12000 },
      "evidence": [
        { "documentId": 501, "text": "월 이용료 33,000원(VAT 포함)", "page": 1, "bbox": [0.10, 0.40, 0.55, 0.03] },
        { "documentId": 511, "text": "당월 청구금액 45,000원", "page": 1, "bbox": [0.52, 0.30, 0.40, 0.04] }
      ]
    }
  ],
  "notices": [
    {
      "severity": "WARNING",
      "title": "확인 필요 · 약정 금액보다 높아요.",
      "description": "추가 사용료나 할인 종료 여부를 확인해 주세요.",
      "possibleReasons": ["EXTRA_USAGE", "DISCOUNT_ENDED"]
    }
  ],
  "disclaimer": "추정 결과이며 실제 과오납 여부는 발급처 확인이 필요해요."
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| status | string | MISMATCH / MATCH / NEEDS_CHECK |
| headline | string | 화면 제목 (예: 약속과 다른 금액이 있어요, 약속대로 청구됐어요) |
| highlight | object \| null | 강조 카드 (BILL: 초과 추정액, AD: 가장 큰 조건 차이). 차이가 없으면 null |
| comparisons[] | array | 항목별 비교표. base / target / difference + 양쪽 원본 근거 위치(원본 근거 보기용) |
| comparisons[].difference | object \| null | 금액 차이. AD에서 금액이 아닌 조건 차이면 value만 (예: "평생 → 12개월") |
| notices[] | array | 이상 징후 안내 (severity: WARNING / INFO) |
| notices[].possibleReasons | string[] | EXTRA_USAGE, DISCOUNT_ENDED, PRICE_CHANGED, CONTRACT_VIOLATION, ETC |
| disclaimer | string | APP에 반드시 표시 |

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "같은 문서끼리는 비교할 수 없습니다." }
```

**401 Unauthorized**

```json
{ "code": "INVALID_API_KEY", "message": "API 키가 올바르지 않습니다." }
```

**403 Forbidden**

```json
{ "code": "FORBIDDEN", "message": "본인 문서만 비교할 수 있습니다." }
```

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "BE에서 문서를 찾을 수 없습니다." }
```

**422 Unprocessable Entity**

```json
{ "code": "NOT_COMPARABLE", "message": "같은 서비스와 같은 기간의 서류를 골라 주세요.", "reason": "DIFFERENT_PROVIDER" }
```

**500 Internal Server Error**

```json
{ "code": "AI_COMPARE_FAILED", "message": "서류 비교 중 오류가 발생했습니다." }
```

---

## 4. 확인 요청 문자 생성 (AI 서버 API)

`POST /ai/comparisons/{id}/inquiry-messages`

> **BE → AI 서버** 호출 API입니다 (X-API-Key). 담당: BE
>
> 앱이 부르는 BE API는 `POST /comparisons/{id}/inquiry-messages`입니다 ([화면별 API](03-screen-map.md) C03, 아래 "흐름" 참고). 노션에는 BE 쪽 API의 별도 페이지가 없고, 이 AI 페이지에만 흐름이 적혀 있습니다. BE API의 Request/Response는 이 AI API를 그대로 전달하는 형태로 확정이 필요합니다.

### 개요

- 서류 비교 결과(약정 금액과 청구 금액의 차이 등)를 바탕으로 발급처에 보낼 확인 요청 문자를 만듭니다. (화면 C03)
- 예) "계약서상 월 요금은 33,000원인데, 고지서에는 45,000원이 청구되어 12,000원 차이가 있습니다. 추가 청구 항목과 산정 근거를 알려 주실 수 있을까요?"
- AI는 문구만 만듭니다. 사용자가 복사해서 문자 앱에 붙여 넣어 보냅니다(자동 전송 안 함).

### 기술 설명/요구 사항

- 흐름: APP → BE(`POST /comparisons/{id}/inquiry-messages`) → AI 서버(`POST /ai/comparisons/{id}/inquiry-messages`)
- `{id}`는 BE의 비교 ID입니다. AI 서버는 비교 결과를 저장하지 않으므로 BE의 `GET /comparisons/{id}`를 내부 키(X-Internal-Key)로 호출해 비교 결과와 두 서류 ID를 가져옵니다. 받는 사람(발급처) 이름은 BE 서류 상세 조회(`GET /documents/{id}?userId=`, X-Internal-Key)로 가져온 서류의 parties에서 씁니다.
  - > 참고: 이 설명대로라면 BE의 `GET /comparisons/{id}`도 X-Internal-Key 호출을 받아야 하지만, [2. 비교 결과 조회](#2-비교-결과-조회) 페이지에는 Authorization(Bearer)만 적혀 있습니다.
- 문자에는 비교 결과의 금액만 씁니다(약정 금액, 청구 금액, 차액). 생성 후 금액을 대조하고, 다르면 다시 생성합니다.
- 구성: 인사 → 무엇에 대한 문의인지(예: 11월 통신요금) → 차이 내용 → 요청(청구 항목·산정 근거) 순서
- 어조(tone): POLITE(정중, 기본) / FRIENDLY / FORMAL
- 길이: 90byte 이하면 SMS, 넘으면 LMS(최대 2,000byte)
- 계좌번호, 주민번호 같은 민감정보는 마스킹합니다.
- "다시 만들기"는 같은 요청을 다시 보내면 됩니다(매번 다른 문구). "문구 수정"은 APP에서 직접 고칩니다.
- 비교 결과가 MATCH(차이 없음)이면 문자를 만들지 않고 422를 돌려줍니다. 타임아웃 20초.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |
| X-API-Key | O | AI 서버 내부 키 (BE만 보유) |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 301 |

**Body**

```json
{
  "userId": 1024,
  "tone": "POLITE",
  "extraRequest": "할인 종료 여부도 같이 물어봐 주세요"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| userId | number | O | 사용자 ID (비교 결과 소유자 확인) |
| tone | string | X | POLITE(기본) / FRIENDLY / FORMAL |
| extraRequest | string | X | 추가로 물어볼 내용 (최대 200자) |

### Response

**201 Created**

```json
{
  "comparisonId": 301,
  "message": {
    "text": "안녕하세요.\n11월 통신요금에 대해 확인 부탁드립니다.\n\n계약서상 월 요금은 33,000원인데, 고지서에는 45,000원이 청구되어 12,000원 차이가 있습니다.\n\n추가 청구 항목과 산정 근거를 알려 주실 수 있을까요?",
    "type": "LMS",
    "byteLength": 210
  },
  "tone": "POLITE"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| comparisonId | number | 요청한 비교 ID 그대로 |
| message.text | string | 문자 본문 (줄바꿈 포함) |
| message.type | string | SMS / LMS |
| message.byteLength | number | EUC-KR 기준 바이트 수 |

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "tone 값이 올바르지 않습니다." }
```

**401 Unauthorized**

```json
{ "code": "INVALID_API_KEY", "message": "API 키가 올바르지 않습니다." }
```

**404 Not Found**

```json
{ "code": "COMPARISON_NOT_FOUND", "message": "BE에서 비교 결과를 찾을 수 없습니다." }
```

**422 Unprocessable Entity**

```json
{ "code": "NO_DIFFERENCE", "message": "차이가 없어 확인 요청 문자를 만들 필요가 없습니다." }
```

**500 Internal Server Error**

```json
{ "code": "AI_GENERATION_FAILED", "message": "문자 생성 중 오류가 발생했습니다." }
```
