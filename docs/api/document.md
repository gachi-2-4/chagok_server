# DOCUMENT API 명세

> 원본: 노션 「API명세서」 > DOCUMENT DB 중 서류 관련 API (`/documents`, `/ai/documents`)
>
> 박스 API(`/boxes`)는 노션에서는 같은 DOCUMENT DB에 있지만 [box.md](box.md)로 분리했습니다.
> 공통 규칙(인증·형식·에러·페이지네이션)은 [00-common.md](00-common.md), 코드표(docType·docCategory·status·failReason·analysisStep·priority·기한 종류 등)는 [02-enums.md](02-enums.md), AI 연동 규칙은 [01-ai-integration.md](01-ai-integration.md)를 참고합니다.

## 목록

| # | 이름 | Method | Path | 호출 주체 |
| --- | --- | --- | --- | --- |
| 1 | [서류 업로드 및 저장](#1-서류-업로드-및-저장) | POST | `/documents` | APP |
| 2 | [추출 결과 확인](#2-추출-결과-확인) | POST | `/documents/{id}/confirm` | APP |
| 3 | [서류 조회](#3-서류-조회) | GET | `/documents` | APP |
| 4 | [서류 상세 조회](#4-서류-상세-조회) | GET | `/documents/{id}` | APP, AI 서버 (X-Internal-Key) |
| 5 | [서류 원본 조회](#5-서류-원본-조회) | GET | `/documents/{id}/original` | APP |
| 6 | [서류 PDF 다운로드](#6-서류-pdf-다운로드) | GET | `/documents/{id}/download` | APP |
| 7 | [서류 이름·박스 변경](#7-서류-이름박스-변경) | PATCH | `/documents/{id}` | APP |
| 8 | [서류 내용 수정](#8-서류-내용-수정) | PATCH | `/documents/{id}/fields` | APP |
| 9 | [체크 항목 변경](#9-체크-항목-변경) | PATCH | `/documents/{id}/checklist/{checkKey}` | APP |
| 10 | [서류 삭제](#10-서류-삭제) | DELETE | `/documents/{id}` | APP |
| 11 | [문서 분석 (AI 서버 API)](#11-문서-분석-ai-서버-api) | POST | `/ai/documents/{id}/analysis` | BE → AI 서버 (X-API-Key) |
| 12 | [쉬운말 요약 카드 (AI 서버 API)](#12-쉬운말-요약-카드-ai-서버-api) | GET | `/ai/documents/summary-card` | BE → AI 서버 (X-API-Key) |

> [BE ↔ AI 서버 연동](01-ai-integration.md)에 나오는 내부 API "문서 분석 결과 조회"는 노션에 별도 페이지가 없고, [4. 서류 상세 조회](#4-서류-상세-조회)의 "AI 서버 호출"(X-Internal-Key + `userId` 쿼리, `fullText` 포함) 설명이 그 역할을 합니다.

---

## 1. 서류 업로드 및 저장

`POST /documents`

> 담당: BE

### 개요

- 사진이나 PDF를 올려 서류를 만들고 AI 문서 분석을 시작합니다.
- 화면: 14 촬영 "완료", 15 앨범에서 선택 "N장 추가하고 정리", U01 사진 촬영·앨범·PDF 선택, U02 "분석 시작", U03 "다시 촬영하기"·"다른 이미지 선택" (H02 대화에 서류 추가는 보관함 서류만 고르므로 이 API를 부르지 않음)

### 기술 설명/요구 사항

- `multipart/form-data`로 보냅니다. 사진은 1–10장, PDF는 1개입니다. 사진과 PDF를 섞을 수 없습니다.
- 사진은 찍은 순서·고른 순서대로 페이지가 됩니다 (15 앨범 선택의 번호 순서). `files`를 그 순서대로 보냅니다.
- 형식과 크기는 AI 문서 분석의 입력 제한에 맞춥니다: JPG, PNG, HEIC, PDF / PDF는 20MB·20페이지까지 / 사진은 장당 20MB까지.
- `boxId`(보관할 박스)는 필수입니다. 14 상단 박스 선택이나 U02 "보관할 곳"에서 고른 박스이며, 앱은 마지막으로 쓴 박스를 기본으로 골라 둡니다.
- `ocrResult`(선택): 앱이 기기 안에서 OCR(ML Kit / iOS Vision)을 했으면 그 결과를 보냅니다. BE는 AI에 그대로 넘기고, AI는 서버 OCR을 건너뛰어 분석이 빨라집니다. 형식은 AI 문서 분석의 `ocrResult`와 같고, 페이지 번호는 `files` 순서입니다.
- 암호가 걸린 PDF와 열 수 없는 파일은 업로드 단계에서 바로 400을 줍니다 (U02 "암호가 걸린 PDF는 암호를 해제한 후 다시 올려 주세요").
- 분석 흐름 (비동기)
  1. BE가 원본을 스토리지(S3)에 저장하고, 페이지 이미지·썸네일을 만들고, 서류를 `ANALYZING`으로 만든 뒤 202를 줍니다.
  2. 사진이면 BE가 긴 변 2,000px로 줄여 PDF 한 개로 합칩니다. AI 문서 분석은 요청당 파일 1개, 20MB까지만 받습니다. (`analysisStep=ENHANCING`)
  3. BE가 AI 문서 분석(`POST /api/ai/documents/analyze`)을 부릅니다. `documentId`·`userId`는 문자열로, `baseDate`는 오늘입니다. AI 응답은 최대 90초 걸립니다. (`analysisStep=READING_TEXT`)
  4. 성공하면 AI 응답 전체(`fullText` 포함)를 저장합니다. AI는 나중에 문서 분석 결과 조회 내부 API로 이 값을 다시 가져갑니다. 응답으로 서류 이름(`title`)·종류·기한·중요도를 채우고, 상태를 `REVIEW_REQUIRED`·`analysisStep=DONE`으로 바꾸고, 분석 완료 알림을 보냅니다. 이어서 AI 쉬운말 요약 카드를 요청해 저장합니다 (비동기).
  5. 실패하면 이렇게 처리합니다.
     - AI 422 `TEXT_NOT_RECOGNIZED` → `FAILED` + `failReason=TEXT_NOT_RECOGNIZED` (U03)
     - AI 500이나 시간 초과 → 한 번 더 시도하고, 그래도 실패하면 `FAILED` + `failReason=ANALYSIS_ERROR`
     - 두 경우 모두 분석 실패 알림을 보냅니다.
- 16 AI 처리 중 체크리스트: AI 문서 분석은 한 번에 응답하므로 BE가 알 수 있는 단계는 `ENHANCING` → `READING_TEXT` → `DONE`입니다. `READING_TEXT` 동안 앱이 "어떤 서류인지 파악"·"비용·기한·중요도 추출"을 시간에 맞춰 차례로 체크합니다. AI가 단계별 진행을 알려 주게 되면 BE가 `CLASSIFYING`·`EXTRACTING`도 채웁니다.
- 앱(16)은 서류 상세 조회(`GET /documents/{id}`)를 2초 간격으로 최대 120초 조회하거나, 분석 완료 푸시를 받고 다음 화면으로 갑니다.
  - `REVIEW_REQUIRED` → U04 읽어 낸 내용을 확인해요
  - `FAILED` + `failReason=TEXT_NOT_RECOGNIZED` → U03 조금 더 선명하게 찍어 주세요
- 다시 찍으면 새로 업로드합니다. `FAILED` 서류는 목록에 보이지 않고 24시간 뒤 자동 삭제합니다.
- `title`을 보내지 않으면 분석 전에는 파일 이름(확장자 제외) 또는 "새 서류"를 쓰고, 분석이 끝나면 AI가 준 `title`(예: 전세 임대차 계약서)로 바꿉니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | multipart/form-data |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**: 없음

**Body (multipart/form-data)**

```javascript
files: page1.jpg, page2.jpg, page3.jpg
boxId: 1
source: CAMERA
ocrResult: {"pages":[{"page":1,"text":"...","blocks":[...]}]}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| files | file[] | O | 사진 1–10장(페이지 순서대로) 또는 PDF 1개 |
| boxId | number | O | 보관할 박스 |
| title | string | X | 서류 이름 (1–50자) |
| source | string | X | `CAMERA` / `ALBUM` / `PDF` (통계용) |
| ocrResult | string(JSON) | X | 기기 안 OCR 결과 (AI 문서 분석 ocrResult 형식) |

### Response

**202 Accepted**

```json
{
  "documentId": 520,
  "title": "새 서류",
  "originalFileName": null,
  "box": { "boxId": 1, "name": "집 · 임대" },
  "status": "ANALYZING",
  "analysisStep": "ENHANCING",
  "pageCount": 3,
  "createdAt": "2026-11-12T13:05:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| documentId | number | 서류 ID (진행 상황 조회에 사용) |
| title | string | 서류 이름 (분석 전에는 임시 이름) |
| originalFileName | string | 올린 PDF 파일 이름 (사진이면 null) |
| box | object | 보관한 박스 (16 "3장 · 집 · 임대에 저장") |
| status | string | 항상 `ANALYZING` |
| analysisStep | string | 분석 단계 (코드표 참고) |
| pageCount | number | 페이지 수 |
| createdAt | string | 업로드 일시 |

**400 Bad Request**

```json
{ "code": "UNSUPPORTED_FILE_TYPE", "message": "JPG, PNG, HEIC, PDF 파일만 올릴 수 있어요." }
```

```json
{ "code": "ENCRYPTED_PDF", "message": "암호가 걸린 PDF는 암호를 해제한 후 다시 올려 주세요." }
```

```json
{ "code": "CORRUPTED_FILE", "message": "파일을 열 수 없어요. 다른 파일을 골라 주세요." }
```

```json
{ "code": "TOO_MANY_PAGES", "message": "PDF는 20페이지까지 올릴 수 있어요." }
```

```json
{ "code": "INVALID_FILE_COMBINATION", "message": "사진과 PDF는 함께 올릴 수 없어요." }
```

**404 Not Found**

```json
{ "code": "BOX_NOT_FOUND", "message": "박스를 찾을 수 없어요." }
```

**413 Payload Too Large**

```json
{ "code": "FILE_TOO_LARGE", "message": "파일은 20MB까지 올릴 수 있어요." }
```

---

## 2. 추출 결과 확인

`POST /documents/{id}/confirm`

> 담당: BE

### 개요

- 읽어 낸 내용을 사용자가 확인하고 보관을 마칩니다.
- 화면: U04 읽어 낸 내용을 확인해요 "확인하고 보관"

### 기술 설명/요구 사항

- 상태를 `REVIEW_REQUIRED` → `CONFIRMED`로 바꿉니다.
- 확인이 필요한 항목(`needsReview=true`)이 남아 있어도 보관할 수 있습니다. 그 항목은 상세 화면에서 계속 "확인 필요"로 보입니다.
- 이미 `CONFIRMED`면 그대로 200을 줍니다 (여러 번 눌러도 같은 결과).
- 서류 비교는 `CONFIRMED` 서류끼리만 할 수 있습니다.
- 기한이 있는 서류면 `primaryDeadline`을 줍니다. 앱은 알림이 없을 때(`hasReminder=false`) "기한을 알려 드릴까요?"로 D02 기한 알림 등록을 권할 수 있습니다. 기한이 없으면 null.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 501 |

**Body**: 없음

### Response

**200 OK**

```json
{
  "documentId": 501,
  "status": "CONFIRMED",
  "primaryDeadline": {
    "deadlineId": 70,
    "type": "PAYMENT",
    "label": "월세 납부",
    "date": "2026-11-25",
    "dDay": 7,
    "hasReminder": false
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| documentId | number | 서류 ID |
| status | string | `CONFIRMED` |
| primaryDeadline | object | 가장 가까운 기한 (없으면 null) |
| primaryDeadline.deadlineId | number | 기한 알림 등록에 쓸 기한 ID |
| primaryDeadline.hasReminder | boolean | 이미 알림이 있는지 |

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "서류를 찾을 수 없어요." }
```

**409 Conflict**

```json
{ "code": "DOCUMENT_NOT_READY", "message": "분석이 끝난 뒤에 보관할 수 있어요." }
```

---

## 3. 서류 조회

`GET /documents`

### 개요

- 내 서류 목록을 박스·검색어·필터로 조회합니다.
- 화면: 06 박스 상세, 10 검색 (전체 서류·필터 칩), 11 검색 결과, M01 내 서류, H02 "내 보관함에서 선택", C01 서류 "변경"

### 기술 설명/요구 사항

- `FAILED` 서류는 빼고, `ANALYZING`·`REVIEW_REQUIRED` 서류는 `status`와 함께 줍니다 (앱에서 "분석 중"·"확인 필요" 표시).
- 06 박스 상세는 `boxId`로 조회합니다. 박스 이름·아이콘·서류 수는 박스 목록 조회 값을 씁니다.
- `keyword`는 10 "서류 이름, 내용, 금액으로 찾기"입니다.
  - 서류 이름, 요약, 읽어 낸 항목 값, AI 문서 분석의 원문(`fullText`)에서 찾습니다.
  - 숫자로 바꿀 수 있는 검색어(`50만`, `500,000`, `500000`)는 금액(`AMOUNT`) 항목과도 맞춰 봅니다.
- 10 필터 칩
  - 전체 → 필터 없음
  - 중요 높음 → `priority=HIGH`
  - 기한 임박 → `dueWithinDays=30`
  - 네 번째 칩(비용 관련) → `hasCost=true` (AI `priority.keyCost`가 있는 서류). 화면 글자가 작아 칩 이름을 확인하지 못했으니 기획과 맞춰 주세요.
- 서류 분류는 AI 문서 분석의 `docCategory`(`CONTRACT` / `BILL` / `AD` / `INFO`)를 씁니다.
  - M01 필터: 계약서 `docCategory=CONTRACT`, 고지서 `docCategory=BILL`, 기타 `docCategory=AD,INFO`
  - C01 비교 서류 후보: 고지서와 비교 `docCategory=BILL`, 광고와 비교 `docCategory=AD`
- 10 "최근 검색"은 기기에 저장합니다 (최대 10개, 서버 API 없음).
- "전체 서류 10개", "계약서 3개" 같은 개수는 `totalElements`를 씁니다.
- 06 카드의 칩은 `priority.level`(중요 높음·보통·낮음)과 `nextDeadline.dDay`로 그립니다. 10·11 목록의 두 번째 줄은 `box.name` + `oneLineSummary`입니다.
- `sort=PRIORITY`는 중요도 등급 → AI `priority.score` 순입니다.
- `thumbnailUrl`은 첫 페이지 썸네일 Presigned URL(10분)입니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| boxId | number | X | - | 1 |
| keyword | string | X | - | 보험 |
| priority | string | X | - | `HIGH` (`HIGH` / `MEDIUM` / `LOW`) |
| dueWithinDays | number | X | - | 30 (오늘부터 n일 안에 완료하지 않은 기한이 있는 서류) |
| hasCost | boolean | X | - | true (대표 비용이 있는 서류) |
| docCategory | string | X | 전체 | `BILL`, 여러 개는 쉼표 `AD,INFO` |
| docType | string | X | - | `LEASE_CONTRACT` |
| sort | string | X | RECENT | `RECENT` / `OLDEST` / `NAME` / `DEADLINE`(기한 가까운 순) / `PRIORITY` |
| page | number | X | 0 | 0 |
| size | number | X | 20 | 20 |

**Path Variable**: 없음

**Body**: 없음

### Response

**200 OK**

```json
{
  "content": [
    {
      "documentId": 520,
      "title": "전세 임대차 계약서",
      "docType": "LEASE_CONTRACT",
      "docCategory": "CONTRACT",
      "box": { "boxId": 1, "name": "집 · 임대" },
      "status": "CONFIRMED",
      "priority": { "level": "HIGH", "keyCost": { "label": "보증금", "amount": 200000000 } },
      "nextDeadline": { "deadlineId": 71, "type": "ACTION", "label": "갱신 통보 마감", "date": "2026-12-28", "dDay": 46 },
      "oneLineSummary": "보증금 2억 원, 2025.03.01부터 2년 계약. 만기 2개월 전까지 갱신 여부를 집주인에게 알려야 해요.",
      "pageCount": 3,
      "thumbnailUrl": "https://storage.example.com/thumb/520.jpg?X-Amz-Signature=...",
      "createdAt": "2026-11-10T13:05:00+09:00"
    },
    {
      "documentId": 545,
      "title": "전입신고 확인서",
      "docType": "NOTICE",
      "docCategory": "INFO",
      "box": { "boxId": 1, "name": "집 · 임대" },
      "status": "CONFIRMED",
      "priority": { "level": "LOW", "keyCost": null },
      "nextDeadline": null,
      "oneLineSummary": "2025.03.02에 마포구로 전입신고를 마쳤다는 확인서예요.",
      "pageCount": 1,
      "thumbnailUrl": "https://storage.example.com/thumb/545.jpg?X-Amz-Signature=...",
      "createdAt": "2026-11-08T10:20:00+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "hasNext": false
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].documentId | number | 서류 ID |
| content[].title | string | 서류 이름 |
| content[].docType / docCategory | string | AI 문서 분석의 서류 종류·분류 (분석 중이면 null) |
| content[].box | object | 박스 (boxId, name) |
| content[].status | string | `ANALYZING` / `REVIEW_REQUIRED` / `CONFIRMED` |
| content[].priority.level | string | 중요도 `HIGH` / `MEDIUM` / `LOW` (분석 중이면 priority가 null) |
| content[].priority.keyCost | object | 대표 비용 (label, amount). 없으면 null |
| content[].nextDeadline | object | 완료하지 않은 가장 가까운 기한. 없으면 null |
| content[].oneLineSummary | string | 한 줄 요약 (쉬운말 요약 카드, 아직 없으면 AI 문서 분석 summary) |
| content[].pageCount | number | 페이지 수 |
| content[].thumbnailUrl | string | 썸네일 URL (10분 유효) |
| content[].createdAt | string | 업로드 일시 |
| totalElements | number | 조건에 맞는 전체 개수 |

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "검색 조건을 확인해 주세요." }
```

---

## 4. 서류 상세 조회

`GET /documents/{id}`

### 개요

- 서류 한 건의 분석 상태와 분석 결과(종류, 당사자, 항목, 중요도, 기한, AI 정리본, 법적 점검)를 조회합니다.
- 화면: 07 서류 원본 탭 하단 카드, 08 AI 정리본, 09 기한이 없는 서류, 16 AI 처리 중(진행 상황 조회), U04 읽어 낸 내용을 확인해요, D01·D02 초기값

### 기술 설명/요구 사항

- `status`가 `ANALYZING`이면 `analysisStep`만 의미가 있고, 분석 결과 필드는 null입니다.
- 분석 결과는 AI 문서 분석 응답을 저장해 둔 값에 사용자가 고친 값을 덮어쓴 것입니다. 이름은 AI 응답과 같게 씁니다 (`docType`, `docCategory`, `classificationReasons`, `summary`, `parties`, `fields`, `priority`, `legalCheck`). AI 응답의 `conditions`·`calendarEvents`는 BE가 `paymentSchedules`·`deadlines`로 바꿔서 줍니다. `fullText`는 앱에 주지 않습니다.
- 07·08 하단 카드: 중요도는 `priority.level`, 금액은 `priority.keyCost`, D-day는 `primaryDeadline`입니다 (예: "D-46 · ~2026.12.28 갱신 통보 마감까지"). 09처럼 기한이 없으면 `primaryDeadline`이 null이고 D-day 줄과 "기한 알림 등록" 버튼을 숨깁니다.
- 08 AI 정리본은 `summaryCard`(AI 쉬운말 요약 카드)로 그립니다.
  - 한 줄 요약 → `summaryCard.oneLineSummary`
  - 계약 정보·주요 특약·체크 → `summaryCard.sections` (`KEY_VALUE` / `BULLET` / `CHECKLIST`). 섹션 제목은 AI가 서류 종류별로 정합니다.
  - 기한 → `deadlines`
  - 체크 항목을 누르면 체크 항목 변경 API를 부릅니다.
  - 내 상황과 관련 있는지 → `userRelevance` (기획: AI 정리본은 서류 요약과 함께 온보딩 설문 값과 비교한 관련 여부를 보여 줌). AI 문서 분석 응답의 `userRelevance`를 그대로 저장해서 줍니다. 온보딩 전이면 null입니다.
  - 카드 아래 정보(서류 종류·중요도·비용·기한) → `docType`, `priority.level`, `priority.keyCost`, `primaryDeadline`
  - 요약 카드는 분석이 끝난 뒤 따로 만들기 때문에 잠깐 null일 수 있습니다. 그동안은 `summary`를 보여 줍니다.
  - 요약 카드를 다시 만들어도 같은 `checkKey`의 `done`은 BE에 저장된 값(사용자가 체크한 값)을 유지합니다. AI가 주는 `done`은 처음 만들 때만 씁니다.
  - `sections[].items[].fieldKey`는 `fields`에 있는 key만 씁니다. 여러 항목을 합친 값(계약기간 등)이나 당사자처럼 fields에 없는 값은 null입니다.
- `legalCheck`가 있으면 08에 법적 확인 사항(`issues`)을 보여 주고, `legalCheck.disclaimer`는 반드시 함께 표시합니다. 프로필이 없거나 법적 점검을 건너뛰었으면 null입니다.
- `fields[].needsReview=true`면 U04 경고 카드("위약금을 확실히 읽지 못했어요")와 "확인 필요"를 표시합니다. AI 기준대로 신뢰도(`confidence`)가 0.7 미만이거나 연도를 추정한(`inferred`) 항목입니다. 사용자가 값을 고치면(`userEdited=true`) 그 항목의 `needsReview`는 false로 바꿉니다. 문서 전체 `needsReview`는 저장된 AI 값이 아니라 `fields` 중 하나라도 `needsReview`면 true로 계산해서 줍니다.
- `originalLabel`이 `label`과 다르면 U04 하단 안내("원본의 '월세'를 '월 임대료'로 정리했어요")를 띄웁니다.
- `evidenceLocation`(page, bbox)으로 원본 위치를 표시합니다. bbox는 페이지 크기 대비 비율 `[x, y, w, h]`입니다.
- `deadlines`는 AI 응답의 `calendarEvents`를 **하나씩 그대로** 옮겨 만듭니다. 같은 날짜끼리 짝짓지 않습니다 (같은 날 기한이 두 개면 섞이기 때문).
  - `type` ← `calendarEvents[].type` (`PAYMENT` / `EXPIRY` / `ACTION`)
  - `label`·`date` ← `title`·`date`, `recurrence`·`endDate` ← `rrule`
  - `amount` ← `fieldKey`로 `fields`에서 찾은 값 (금액 항목이 아니면 null)
  - `suggestedAdvanceDays` ← `remindBeforeDays`
  - 행동 기한(갱신 통보 등)도 `calendarEvents`에 `type: ACTION`으로 들어오므로 `priority.keyDeadline.actionDate`로 따로 만들지 않습니다 (중복 방지).
- `paymentSchedules`는 AI 응답의 `conditions.paymentSchedules`입니다. `amount`는 `fieldKey`로 `fields`에서 찾아 채웁니다 (사용자가 금액을 고치면 같이 바뀜).
- `primaryDeadline`은 `priority.keyDeadline`에 해당하는 기한입니다. 행동 기한(`actionDate`)이 있고 지나지 않았으면 그 기한, 아니면 대표 기한입니다.
- 다른 사용자의 서류를 조회하면 존재 여부를 숨기기 위해 404를 줍니다.
- `analysisStep` 값: `UPLOADED` → `READING_TEXT`(글자 읽기) → `CLASSIFYING`(종류 판별) → `EXTRACTING`(항목 정리) → `LEGAL_CHECK`(법적 확인) → `DONE`. AI 문서 분석은 한 번의 호출이라 중간 단계는 BE가 경과 시간으로 보여 줍니다(진행 알림 방식은 협의 중).
  - > 참고: 이 값 목록(`UPLOADED`, `LEGAL_CHECK` 포함)은 [코드표 > 분석 단계 analysisStep](02-enums.md#분석-단계-analysisstep)(`ENHANCING` / `READING_TEXT` / `CLASSIFYING` / `EXTRACTING` / `DONE`) 및 서류 업로드 API 설명과 다릅니다. 원본 그대로 옮겼으니 구현 전에 확정이 필요합니다.
- `failReason`: AI 422 → `TEXT_NOT_RECOGNIZED`, AI 500·타임아웃(90초) → `ANALYSIS_ERROR`
- **AI 서버 호출**: AI 서버도 이 API로 서류를 가져갑니다 (서류 기반 AI 채팅, 쉬운말 요약 카드, 서류 비교, 확인 요청 문자).
  - 사용자 토큰 대신 `X-Internal-Key` 헤더와 `userId` 쿼리로 호출합니다. BE는 그 사용자의 서류인지 확인합니다.
  - 이때만 응답에 `fullText`(OCR 원문 전체, [N페이지] 표시)를 추가합니다. 앱(Bearer) 호출에는 절대 주지 않습니다.
  - 사용자가 고친 값(`userEdited`)이 반영된 `fields`를 그대로 줍니다. AI는 이 값을 원문보다 우선합니다.
  - 분석 중(`ANALYZING`)이거나 실패(`FAILED`)한 서류는 409를 줍니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | △ | Bearer {accessToken} (앱 호출) |
| X-Internal-Key | △ | AI 서버 전용 내부 키 (AI 서버 호출). Authorization과 둘 중 하나 필수 |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| userId | number | AI 호출 때만 O | - | 1024 |

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 520 |

**Body**: 없음

### Response

**200 OK**

```json
{
  "documentId": 520,
  "title": "전세 임대차 계약서",
  "originalFileName": null,
  "box": { "boxId": 1, "name": "집 · 임대" },
  "status": "CONFIRMED",
  "analysisStep": "DONE",
  "failReason": null,
  "pageCount": 3,
  "docType": "LEASE_CONTRACT",
  "docCategory": "CONTRACT",
  "classificationReasons": ["양 당사자 서명란 존재", "계약기간·보증금 조항 존재"],
  "summary": "마포구 ○○빌라 302호 전세 계약. 보증금 2억 원, 계약기간 2025-03-01 ~ 2027-02-28.",
  "needsReview": true,
  "parties": [
    { "role": "LESSOR", "name": "홍○○" },
    { "role": "LESSEE", "name": "이차곡" }
  ],
  "fields": [
    {
      "key": "deposit",
      "label": "보증금",
      "originalLabel": "임대보증금",
      "value": 200000000,
      "valueType": "AMOUNT",
      "evidence": "임대보증금 금 이억원정(₩200,000,000)",
      "evidenceLocation": { "page": 1, "bbox": [0.12, 0.31, 0.60, 0.04] },
      "confidence": 0.98,
      "needsReview": false,
      "userEdited": false
    },
    {
      "key": "contractEndDate",
      "label": "계약 만료일",
      "originalLabel": "임대차 종료일",
      "value": "2027-02-28",
      "valueType": "DATE",
      "evidence": "2027년 2월 28일까지",
      "evidenceLocation": { "page": 1, "bbox": [0.12, 0.36, 0.70, 0.04] },
      "confidence": 0.95,
      "needsReview": false,
      "userEdited": false
    },
    {
      "key": "earlyTerminationPenalty",
      "label": "중도 해지 위약금",
      "originalLabel": "위약금",
      "value": null,
      "valueType": "PENALTY",
      "evidence": null,
      "evidenceLocation": { "page": 2, "bbox": [0.10, 0.62, 0.80, 0.05] },
      "confidence": 0.41,
      "needsReview": true,
      "userEdited": false
    }
  ],
  "paymentSchedules": [],
  "priority": {
    "level": "HIGH",
    "score": 86,
    "reasons": ["보증금 2억 원이 걸린 계약이에요.", "만기 2개월 전까지 갱신 여부를 알려야 해요."],
    "keyCost": { "fieldKey": "deposit", "label": "보증금", "amount": 200000000 },
    "keyDeadline": { "fieldKey": "contractEndDate", "label": "계약 만료", "date": "2027-02-28", "actionLabel": "갱신 통보", "actionDate": "2026-12-28" }
  },
  "primaryDeadline": { "deadlineId": 71, "type": "ACTION", "label": "갱신 통보 마감", "date": "2026-12-28", "dDay": 46, "hasReminder": true },
  "deadlines": [
    {
      "deadlineId": 71,
      "type": "ACTION",
      "fieldKey": "contractEndDate",
      "label": "갱신 통보 마감",
      "date": "2026-12-28",
      "recurrence": null,
      "endDate": null,
      "amount": null,
      "dDay": 46,
      "suggestedAdvanceDays": [30, 7, 1],
      "source": "AI",
      "userEdited": false,
      "isCompleted": false,
      "reminder": { "reminderId": 31, "alertTime": "09:00", "advanceDays": [30, 7, 1] }
    },
    {
      "deadlineId": 72,
      "type": "EXPIRY",
      "fieldKey": "contractEndDate",
      "label": "계약 만료",
      "date": "2027-02-28",
      "recurrence": null,
      "endDate": null,
      "amount": null,
      "dDay": 108,
      "suggestedAdvanceDays": [60, 30, 7],
      "source": "AI",
      "userEdited": false,
      "isCompleted": false,
      "reminder": null
    }
  ],
  "summaryCard": {
    "oneLineSummary": "보증금 2억 원, 2025.03.01부터 2년 계약. 만기 2개월 전까지 갱신 여부를 집주인에게 알려야 해요.",
    "sections": [
      {
        "type": "KEY_VALUE",
        "title": "계약 개요",
        "items": [
          { "label": "임대인", "value": "홍○○", "fieldKey": null },
          { "label": "보증금", "value": "200,000,000원", "fieldKey": "deposit" },
          { "label": "계약기간", "value": "2025.03.01 – 2027.02.28", "fieldKey": null }
        ]
      },
      {
        "type": "BULLET",
        "title": "주요 특약",
        "items": [{ "text": "만기 6개월~2개월 전 갱신 여부 통지" }]
      },
      {
        "type": "CHECKLIST",
        "title": "체크",
        "items": [
          { "checkKey": "fixedDate", "text": "확정일자 받음 (2025.02.20)", "done": true },
          { "checkKey": "moveInReport", "text": "전입신고 완료", "done": false }
        ]
      }
    ]
  },
  "userRelevance": {
    "level": "RELEVANT",
    "reasons": ["임차인으로 계약한 서류예요.", "서울 마포구 기준 최우선변제 대상 보증금을 넘어요."]
  },
  "legalCheck": {
    "riskLevel": "LOW",
    "issues": [],
    "skippedRules": [],
    "helpContacts": [{ "name": "대한법률구조공단", "phone": "132" }],
    "disclaimer": "이 내용은 법률 자문이 아닌 참고 정보예요. 정확한 판단은 전문가와 상담하세요."
  },
  "createdAt": "2026-11-10T13:05:00+09:00",
  "updatedAt": "2026-11-10T13:05:24+09:00"
}
```

(fields·sections는 일부만 적었습니다.)

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| status | string | `ANALYZING` / `REVIEW_REQUIRED` / `CONFIRMED` / `FAILED` |
| analysisStep | string | 분석 단계 (16 체크리스트, 끝나면 `DONE`) |
| failReason | string | `FAILED`일 때 이유 (`TEXT_NOT_RECOGNIZED` / `ANALYSIS_ERROR`) |
| docType / docCategory | string | AI 문서 분석의 서류 종류·분류 |
| classificationReasons | string[] | 종류 판별 근거 (계약서 vs 고지서·광고 차이 표시용) |
| summary | string | AI 문서 분석 요약 (3줄 이내) |
| needsReview | boolean | fields 중 하나라도 needsReview면 true (BE가 계산) |
| parties[] | array | 당사자 (role, name). 08 임대인·임차인 |
| fields[] | array | 항목 (key, label, originalLabel, value, valueType, evidence, evidenceLocation, confidence, needsReview, userEdited) |
| paymentSchedules[] | array | 매월 납부 일정 (fieldKey, label, amount, recurrence, dayOfMonth, startDate, endDate). amount는 fieldKey로 fields에서 채움. D01 "납부일" |
| priority | object | AI 중요도 (level, score, reasons, keyCost, keyDeadline) |
| primaryDeadline | object | 하단 카드 D-day (없으면 null) |
| deadlines[] | array | 기한 (deadlineId, type, fieldKey, label, date, recurrence, endDate, amount, dDay, suggestedAdvanceDays, source, userEdited, isCompleted, reminder). 기한 알림은 자동으로 한 달 전·일주일 전·하루 전 |
| summaryCard | object | AI 쉬운말 요약 카드 (oneLineSummary, sections). 만들기 전이면 null |
| legalCheck | object | AI 법적 위험 점검 (riskLevel, issues, helpContacts, disclaimer). 건너뛰었으면 null |
| userRelevance | object | 내 상황과 관련 있는지 (level `RELEVANT` / `PARTIAL` / `NOT_RELEVANT`, reasons). AI 문서 분석 값, 온보딩 전이면 null |
| fullText | string | OCR 원문 전체. **AI 서버(X-Internal-Key) 호출 때만** 포함 |

**401 Unauthorized**

```json
{ "code": "UNAUTHORIZED", "message": "로그인이 필요해요." }
```

**404 Not Found**

없는 서류이거나 다른 사용자의 서류일 때

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "서류를 찾을 수 없어요." }
```

**409 Conflict**

AI 서버 호출에서 분석 중이거나 실패한 서류일 때

```json
{ "code": "DOCUMENT_NOT_READY", "message": "서류 분석이 끝나지 않았어요." }
```

---

## 5. 서류 원본 조회

`GET /documents/{id}/original`

### 개요

- 서류 원본을 페이지별 이미지로 조회합니다.
- 화면: 07·09 원본 탭 (페이지 "1/3"), D01 "원본 확인하기", H01 "원본 확인 ›", C02 "원본 근거 보기"

### 기술 설명/요구 사항

- PDF도 업로드할 때 페이지별 이미지로 바꿔 두고, 이미지 URL로 줍니다 (앱은 이미지 뷰어 하나로 처리).
- URL은 Presigned URL이며 10분 동안 유효합니다. 만료되면 다시 조회합니다.
- 근거 위치 강조는 앱이 합니다. 다른 API에서 받은 `page`·`bbox`를 이미지 위에 그립니다.
- `page`를 보내면 그 페이지만 줍니다.
- 분석 중(`ANALYZING`)에도 조회할 수 있습니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| page | number | X | 전체 | 1 |

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 501 |

**Body**: 없음

### Response

**200 OK**

```json
{
  "documentId": 501,
  "pageCount": 3,
  "pages": [
    { "page": 1, "imageUrl": "https://storage.example.com/pages/501-1.jpg?X-Amz-Signature=...", "width": 1240, "height": 1754 },
    { "page": 2, "imageUrl": "https://storage.example.com/pages/501-2.jpg?X-Amz-Signature=...", "width": 1240, "height": 1754 },
    { "page": 3, "imageUrl": "https://storage.example.com/pages/501-3.jpg?X-Amz-Signature=...", "width": 1240, "height": 1754 }
  ],
  "expiresAt": "2026-11-18T10:20:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| pageCount | number | 전체 페이지 수 |
| pages[].page | number | 페이지 번호 (1부터) |
| pages[].imageUrl | string | 페이지 이미지 URL |
| pages[].width | number | 이미지 너비(px) — bbox 계산용 |
| pages[].height | number | 이미지 높이(px) — bbox 계산용 |
| expiresAt | string | URL 만료 일시 |

**400 Bad Request**

```json
{ "code": "INVALID_PAGE", "message": "없는 페이지예요." }
```

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "서류를 찾을 수 없어요." }
```

---

## 6. 서류 PDF 다운로드

`GET /documents/{id}/download`

### 개요

- 서류를 PDF 파일로 내려받을 수 있는 주소를 줍니다.
- 화면: M04 PDF로 내려받기 "PDF 다운로드" (원본 PDF)

### 기술 설명/요구 사항

- 파일을 직접 응답하지 않고 Presigned URL(10분)을 줍니다. 앱은 URL로 받은 뒤 기기의 파일 저장 화면을 띄웁니다 (M04 "저장 위치는 기기의 파일 저장 화면에서 선택해 주세요").
- PDF로 올린 서류는 원본 PDF를, 사진으로 올린 서류는 사진들을 페이지 순서대로 합친 PDF를 줍니다. 합친 PDF는 처음 요청할 때 만들어 저장해 둡니다.
- `type`은 지금 `ORIGINAL`(원본 PDF)만 지원합니다. M04 선택지가 늘어나면(예: 요약 포함 PDF) 값을 추가합니다.
- 파일 이름은 서류 이름(`title`) 기준이며, 파일 이름에 쓸 수 없는 문자는 `_`로 바꿉니다.
- 서류 삭제(M03)·회원 탈퇴(S05) 전에 원본을 받아 두라는 안내에서도 이 API를 씁니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| type | string | X | ORIGINAL | `ORIGINAL` |

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 501 |

**Body**: 없음

### Response

**200 OK**

```json
{
  "fileName": "임대차계약서.pdf",
  "downloadUrl": "https://storage.example.com/docs/501.pdf?X-Amz-Signature=...",
  "fileSize": 1048576,
  "pageCount": 3,
  "expiresAt": "2026-11-18T10:20:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| fileName | string | 저장할 파일 이름 |
| downloadUrl | string | 다운로드 URL (10분 유효) |
| fileSize | number | 파일 크기(byte) |
| pageCount | number | 페이지 수 |
| expiresAt | string | URL 만료 일시 |

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "지원하지 않는 형식이에요." }
```

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "서류를 찾을 수 없어요." }
```

---

## 7. 서류 이름·박스 변경

`PATCH /documents/{id}`

### 개요

- 서류 이름을 바꾸거나 다른 박스로 옮깁니다.
- 화면: M02 서류 이름 변경 "이름 저장", 서류를 다른 박스로 옮기기

### 기술 설명/요구 사항

- 서류 이름·박스만 바꾸고 원본 파일과 분석 결과는 그대로 둡니다 (M02 "서류 이름만 바뀌고 원본 파일 내용은 유지돼요").
- 보낸 필드만 바꿉니다.
- `title`은 앞뒤 공백을 지운 뒤 1–50자여야 합니다. 같은 이름이 있어도 됩니다.
- `boxId`를 보내면 그 박스로 옮깁니다. 내 박스가 아니면 404.
- 항목 값(금액·날짜)을 고치는 것은 서류 내용 수정 API(`PATCH /documents/{id}/fields`)입니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 501 |

**Body**

```json
{
  "title": "우리 집 임대차계약서",
  "boxId": 1
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| title | string | X | 새 서류 이름 (1–50자) |
| boxId | number | X | 옮길 박스 |

### Response

**200 OK**

```json
{
  "documentId": 501,
  "title": "우리 집 임대차계약서",
  "box": { "boxId": 1, "name": "집 · 임대" },
  "updatedAt": "2026-11-18T10:12:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| documentId | number | 서류 ID |
| title | string | 바뀐 서류 이름 |
| box | object | 서류가 든 박스 |
| updatedAt | string | 수정 일시 |

**400 Bad Request**

```json
{ "code": "INVALID_TITLE", "message": "서류 이름은 1자에서 50자 사이로 지어 주세요." }
```

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "서류를 찾을 수 없어요." }
```

```json
{ "code": "BOX_NOT_FOUND", "message": "박스를 찾을 수 없어요." }
```

---

## 8. 서류 내용 수정

`PATCH /documents/{id}/fields`

### 개요

- AI가 잘못 읽은 항목 값과 매월 납부 일정을 사용자가 고칩니다.
- 화면: D01 서류 내용 수정 "수정 내용 저장", U04 "내용 수정하기"

### 기술 설명/요구 사항

- `fields`는 보낸 `key`만 바꿉니다. `value`를 null로 보내면 값을 비웁니다. AI 문서 분석의 `fields`에 있는 key만 고칠 수 있습니다.
- `paymentSchedules`는 AI 문서 분석의 `conditions.paymentSchedules`를 고칩니다 (D01 "납부일 매월 25일"). 보낸 key의 `dayOfMonth`만 바꿉니다.
- D01 "월 임대료" 같은 매월 금액은 `fields`(예: `monthlyRent`)로 고칩니다. 그 항목을 가리키는 `paymentSchedules[].amount`와 기한 금액은 BE가 같이 바꿉니다 (서류 상세 조회의 `fieldKey` 참고).
- 고친 항목은 `userEdited=true`, `needsReview=false`가 됩니다. AI가 읽은 원래 값은 따로 보관합니다.
- 값 검사 (`valueType` 기준)
  - `AMOUNT`: 0 이상 정수 (원)
  - `DATE`: `YYYY-MM-DD`
  - `TEXT`: 200자 이하
  - `dayOfMonth`: 1–31 (그 달에 없는 날이면 말일로 처리)
- 서류 종류를 바꾸면(`docType`) 항목 구성이 달라지므로, BE가 `docTypeHint`를 넣어 AI 문서 분석을 다시 요청합니다. 상태가 `ANALYZING`으로 돌아가고, 같은 key로 고쳤던 값은 다시 덮어씁니다. 이때는 `fields`·`paymentSchedules`를 같이 보낼 수 없습니다.
- 수정 후 반영 (D01 "수정한 값은 요약과 비교 결과에 반영돼요")
  - 기한(`deadlines`)을 다시 계산합니다. 예) 납부일·계약 만료일을 바꾸면 기한 날짜가 바뀌고, 그 기한의 알림도 다음 알림 시각을 다시 계산합니다. 값을 비워서 사라진 기한은 알림도 같이 지웁니다.
  - AI 쉬운말 요약 카드를 다시 요청합니다 (AI 캐시 갱신). 새 카드가 올 때까지는 이전 카드를 보여 줍니다.
  - 이 서류를 쓴 비교 결과는 `outdated=true`가 됩니다 (다시 비교 안내).
  - AI는 채팅·비교·문자 생성 때 문서 분석 결과 조회 내부 API로 고친 값(`userEdited=true`)을 가져가고, 원문보다 고친 값을 먼저 씁니다.
- 분석 중(`ANALYZING`)이거나 실패(`FAILED`)한 서류는 고칠 수 없습니다 (409).
- 서류 이름·박스는 서류 이름·박스 변경 API(`PATCH /documents/{id}`)로 바꿉니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 501 |

**Body**

```json
{
  "fields": [
    { "key": "monthlyRent", "value": 500000 },
    { "key": "contractEndDate", "value": "2027-11-24" },
    { "key": "earlyTerminationFee", "value": "잔여 기간 월세의 10%" }
  ],
  "paymentSchedules": [
    { "key": "monthlyRent", "dayOfMonth": 25 }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| docType | string | X | 서류 종류를 바꿀 때만 (다시 분석, 다른 필드와 함께 못 보냄) |
| fields | array | X | 고칠 항목 목록 |
| fields[].key | string | O | 항목 key |
| fields[].value | any | O | 새 값 (valueType에 맞게, 비우려면 null) |
| paymentSchedules | array | X | 고칠 매월 납부 일정 |
| paymentSchedules[].key | string | O | 납부 일정 key (예: `monthlyRent`) |
| paymentSchedules[].dayOfMonth | number | X | 매월 납부일 (1–31) |

### Response

**200 OK**

서류 상세 조회(`GET /documents/{id}`)와 같은 형식으로, 고친 값이 반영된 전체 서류를 줍니다. `docType`을 바꿨으면 `status=ANALYZING`인 상태를 줍니다.

```json
{
  "documentId": 501,
  "docType": "LEASE_CONTRACT",
  "status": "REVIEW_REQUIRED",
  "fields": [
    {
      "key": "earlyTerminationFee",
      "label": "중도 해지 위약금",
      "originalLabel": "위약금",
      "value": "잔여 기간 월세의 10%",
      "valueType": "TEXT",
      "evidence": "중도 해지 시 잔여 기간 차임의 10%를 위약금으로 한다.",
      "evidenceLocation": { "page": 2, "bbox": [0.10, 0.62, 0.80, 0.05] },
      "confidence": 1,
      "needsReview": false,
      "userEdited": true
    }
  ],
  "paymentSchedules": [
    { "key": "monthlyRent", "label": "월세 납부", "fieldKey": "monthlyRent", "amount": 500000, "dayOfMonth": 25, "startDate": "2025-12-25", "endDate": "2027-11-25", "userEdited": true }
  ],
  "updatedAt": "2026-11-18T10:15:00+09:00"
}
```

(예시는 일부 필드만 적었습니다.)

**400 Bad Request**

```json
{ "code": "INVALID_FIELD_VALUE", "message": "납부일은 1일에서 31일 사이로 입력해 주세요." }
```

```json
{ "code": "UNKNOWN_FIELD_KEY", "message": "이 서류에 없는 항목이에요." }
```

```json
{ "code": "INVALID_REQUEST", "message": "서류 종류는 다른 항목과 따로 바꿔 주세요." }
```

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "서류를 찾을 수 없어요." }
```

**409 Conflict**

```json
{ "code": "DOCUMENT_NOT_READY", "message": "분석이 끝난 뒤에 고칠 수 있어요." }
```

---

## 9. 체크 항목 변경

`PATCH /documents/{id}/checklist/{checkKey}`

### 개요

- AI 정리본의 '체크' 항목을 했음/안 했음으로 바꿉니다.
- 화면: 08 AI 정리본 체크 섹션 (예: 확정일자 받음, 전입신고 완료)

### 기술 설명/요구 사항

- 체크 항목은 AI 쉬운말 요약 카드의 `CHECKLIST` 섹션에서 옵니다. AI는 문서에서 이미 한 것으로 확인되면 `done=true`로 줍니다.
- 그 뒤의 체크 상태는 BE가 저장하고 관리합니다 (AI 쉬운말 요약 카드 명세).
- 바꾼 상태는 서류 상세의 `summaryCard`와, AI가 부르는 문서 분석 결과 조회 내부 API의 `checklist`에 반영됩니다.
- 요약 카드를 다시 만들어도 사용자가 바꾼 체크 상태는 유지합니다 (`checkKey` 기준).

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 520 |
| checkKey | string | O | moveInReport |

**Body**

```json
{
  "done": true
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| done | boolean | O | 했으면 true |

### Response

**200 OK**

```json
{
  "checkKey": "moveInReport",
  "text": "전입신고 완료",
  "done": true,
  "updatedAt": "2026-11-18T10:20:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| checkKey | string | 체크 항목 key |
| text | string | 체크 항목 문구 |
| done | boolean | 바뀐 상태 |
| updatedAt | string | 바꾼 일시 |

**404 Not Found**

```json
{ "code": "CHECK_ITEM_NOT_FOUND", "message": "체크 항목을 찾을 수 없어요." }
```

**409 Conflict**

```json
{ "code": "SUMMARY_CARD_NOT_READY", "message": "AI 정리본을 만드는 중이에요. 잠시 후 다시 시도해 주세요." }
```

---

## 10. 서류 삭제

`DELETE /documents/{id}`

### 개요

- 서류와 관련 데이터를 삭제합니다.
- 화면: M03 이 서류를 삭제할까요? "서류 삭제" ("삭제할 서류를 확인했어요" 체크는 앱에서 검사)

### 기술 설명/요구 사항

- 같이 삭제하는 데이터
  - 원본 파일, 페이지 이미지, 분석 결과
  - 기한, 기한 알림과 예약된 푸시
  - 이 서류를 쓴 비교 결과
  - AI 서버의 벡터 인덱스 (AI 서버에 삭제 요청)
- 대화는 지우지 않고 연결만 끊습니다. 지난 대화 목록에서는 "삭제된 서류"로 보입니다.
- 이미 보낸 알림(N01)은 남기고 연결 서류만 null로 바꿉니다.
- 복구할 수 없습니다 (M03 "삭제하면 이 서류와 분석 결과를 다시 볼 수 없어요"). DB에서는 바로 지우고, 스토리지 파일과 AI 인덱스는 비동기로 지웁니다.
- 원본이 필요하면 삭제 전에 서류 PDF 다운로드 API로 받습니다 (M03 "필요한 원본은 먼저 다운로드해 주세요").

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 501 |

**Body**: 없음

### Response

**204 No Content**

응답 본문 없음

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "서류를 찾을 수 없어요." }
```

---

## 11. 문서 분석 (AI 서버 API)

`POST /ai/documents/{id}/analysis`

> **BE → AI 서버** 호출 API입니다 (X-API-Key). 앱에서는 호출하지 않습니다. 담당: BE, AI
>
> 참고: 서류 업로드 API 설명에는 이 호출 경로가 `POST /api/ai/documents/analyze`로 적혀 있어 이 페이지의 경로와 다릅니다. 원본 그대로 옮겼으니 AI 팀과 경로를 확정해야 합니다.

### 개요

- 공지서·계약서·고지서를 PDF나 사진으로 올리면 분석해서 구조화된 문서 데이터로 만들어 줍니다.
- 문서 종류 분석 → 항목명 정규화 → 조건 추출(금액·기한·위약금·납부일) → 요약과 캘린더 일정 후보 생성 → 사용자 조건에 맞춘 법적 위험 점검 → 사용자 관련도·서류 중요도 판단까지 한 번에 처리합니다.
- 요청당 문서 1건(파일 1개)만 받습니다.
- BE 서류 상세 조회(`GET /documents/{id}`) 수정안과 필드를 맞췄습니다: 모든 추출값은 fields에, 캘린더 일정은 type·fieldKey로 하나씩, userRelevance·fields[].needsReview 포함.

### 기술 설명/요구 사항

- 흐름: APP → BE → AI 서버. AI 서버는 BE DB에 직접 접근하지 않고, 필요한 정보는 **BE 내부 API를 호출해서 조회**합니다. documentId는 BE가 만들어 path로 보내고, 분석 결과 저장도 BE가 합니다.
- ID는 BE와 같이 **숫자(number)**를 씁니다. (documentId 520, userId 1024)
  - > 참고: [BE ↔ AI 서버 연동 > 규칙](01-ai-integration.md#규칙)에는 "BE의 숫자 ID를 문자열로 바꿔 보냅니다", 서류 업로드 API에는 "`documentId`·`userId`는 문자열로"라고 되어 있어 이 페이지와 다릅니다. 원본 그대로 옮겼으니 확정이 필요합니다.
- **OCR 원문 전달**: AI 서버는 서류 내용을 저장하지 않습니다. 대신 응답의 fullText(OCR 원문 전체, 페이지 구분 표시 포함)를 BE가 분석 결과와 함께 저장하고, 채팅 때 AI가 BE 서류 상세 조회(`GET /documents/{id}?userId=`, X-Internal-Key)로 다시 받아 씁니다.
- 지원 형식: pdf, jpg, jpeg, png, heic / 최대 20MB / PDF는 최대 20페이지
- **OCR 속도 대책** (우선순위 순):
  1. 텍스트가 들어있는 PDF는 OCR 없이 텍스트 레이어를 바로 읽습니다(수십 ms).
  2. APP이 기기 안에서 OCR(ML Kit / iOS Vision)을 하고 결과를 ocrResult로 보내면 서버 OCR을 건너뜁니다. 신뢰도가 낮은 페이지만 서버에서 다시 읽습니다.
  3. 서버 OCR은 경량 모델(PaddleOCR mobile, ONNX Runtime) + 긴 변 2,000px로 축소 + 페이지 병렬 처리로 돌립니다.
- **문서 종류 분류**: 이미지 모델(CNN) 없이 OCR 텍스트 기반으로 합니다. 키워드 규칙(예: "임대인·임차인", "납부기한·고지서") → 애매하면 LLM 분류
- 처리 순서: 파일 검증 → OCR(텍스트 PDF는 텍스트를 바로 추출) → 문서 종류 분류 → 항목명 정규화 → 조건 추출 → 법적 위험 점검 → 사용자 관련도·중요도 판단 → 결과 JSON 스키마 검증 → 응답
- **법적 위험 점검**: AI 서버가 userId로 BE 사용자 정보 조회 API(`GET /user/{userId}`)를 호출해서 온보딩 설문 정보를 가져옵니다.
  1. 규칙 엔진: 나이·금액·시간처럼 기준이 숫자인 것은 코드로 판정 (미성년자 계약, 연소근로자 근로시간, 연도별 최저임금, 이자 상한, 청약철회 기간, 지역별 최우선변제 기준 등). 기준값은 시행일이 있는 테이블로 관리
  2. 법령 RAG: AI 전용 **법령 DB**(PostgreSQL + pgvector, BE DB와 별개)에서 검색. 국가법령정보센터 Open API 원문을 조·항 단위로 저장(법령명·조문·시행일·URL·분야 태그·임베딩), 주 1회 개정 동기화. 문서 종류에 맞는 분야 태그로 걸러서 벡터 + 키워드 혼합 검색
     - 규칙 엔진 기준값(최저임금, 최우선변제 금액 등)도 같은 법령 DB의 기준값 테이블에서 읽습니다.
  3. LLM 판단: 조항 + 사용자 조건 + 규칙 결과 + 검색 조문으로 이슈 생성. 조문 인용 없는 이슈는 버림
     - 외부 LLM API에는 생년월일 대신 계산된 나이만 보냅니다(최소 전송). consents.overseasTransfer가 false면 프로필은 외부 LLM에 보내지 않고 규칙 엔진 점검만 합니다.
     - birthDate가 null이면 나이 기준 규칙은 skippedRules로 알립니다.
     - 온보딩 전(profile = null)이거나 legalCheck=false면 법적 점검은 건너뛰고 legalCheck: null을 줍니다. 법적 점검이 실패해도 분석 결과는 정상 응답하고 warnings에 알립니다.
- **문서 종류(docType)**: LEASE_CONTRACT(임대차계약서), EMPLOYMENT_CONTRACT(근로계약서), SERVICE_CONTRACT(용역·서비스 계약), BILL(고지서·청구서), NOTICE(공지·안내문), ADVERTISEMENT(광고·홍보물), ETC
- **계약서 vs 고지서·광고 구분**: 고지서처럼 꾸민 광고(예: "납부 안내" 형식의 홍보 우편)를 구분할 수 있도록 `docCategory`(CONTRACT / BILL / AD / INFO)와 판별 근거(`classificationReasons`)를 줍니다. 차이 표시 화면은 BE·APP이 이 값으로 그립니다.
- **근거 위치**: 서류 뒤집기(원본·근거 확인)용으로 evidence마다 `page`(1부터)와 `bbox`([x, y, w, h], 페이지 기준 0~1 비율)를 줍니다.
- **항목명 정규화**: 문서마다 다른 표현을 표준 키로 맞춥니다. 예) "보증금", "임대보증금" → `deposit` / "월세", "차임" → `monthlyRent` / "납기일", "납부기한" → `paymentDueDate`
- **모든 추출값은 fields에**: 금액(KRW 정수), 날짜(YYYY-MM-DD), 위약금(PENALTY: 문장 + 금액 또는 비율), 텍스트 값을 전부 fields에 한 줄씩 넣습니다. conditions에는 fields에 없는 정보(반복 납부 규칙)만 두고, 각 항목은 `fieldKey`로 fields를 가리킵니다. BE는 금액이 필요하면 fieldKey로 fields에서 찾습니다.
- **needsReview**: 값마다 원문 근거(evidence)와 신뢰도(confidence 0~1)를 주고, 신뢰도가 0.7 미만이거나 연도를 추정한 날짜(`inferred: true`)면 `fields[].needsReview: true`입니다. 최상위 needsReview는 fields 중 하나라도 true면 true입니다.
- **캘린더 일정(calendarEvents)**: BE가 날짜로 짝짓지 않고 하나씩 그대로 deadlines에 옮길 수 있게 줍니다.
  - type: PAYMENT(납부, 반복 가능) / EXPIRY(만료·종료) / ACTION(그 전에 해야 할 행동, 예: 갱신 통보·해지 통보·이의신청)
  - fieldKey: 근거가 된 fields의 key (행동 기한처럼 원문 값이 없으면 기준이 된 날짜의 key, 예: contractEndDate)
  - 행동 기한도 calendarEvents에 들어가므로 BE가 priority.keyDeadline.actionDate로 따로 만들 필요가 없습니다.
- **사용자 관련도(userRelevance)**: 사용자 프로필과 서류 내용(수신인·지역·대상 조건)을 비교해서 RELEVANT(나에게 해당) / PARTIAL(일부만 해당·확인 필요) / NOT_RELEVANT(해당 없음, 예: 다른 지역 대상 안내문)를 줍니다. 프로필이 없으면 null입니다.
- **서류 중요도·D-day**: 분석 결과와 법적 점검 결과로 중요도를 같이 만듭니다.
  - level: HIGH(돈이 나가거나 기한을 놓치면 불이익) / MEDIUM / LOW(참고·광고). ADVERTISEMENT와 userRelevance NOT_RELEVANT는 항상 LOW, legalCheck.riskLevel이 HIGH면 최소 HIGH
  - keyCost: 카드에 보여줄 대표 비용 1개 (fieldKey 포함)
  - keyDeadline: 대표 기한과 행동 기한. D-day 숫자는 BE·APP이 오늘 날짜로 계산
- 날짜에 연도가 없으면 baseDate를 기준으로 추정하고 `inferred: true`로 표시합니다. 타임아웃은 90초입니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | multipart/form-data |
| X-API-Key | O | AI 서버 내부 키 (BE만 보유) |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| baseDate | string(date) | X | 오늘 | 2026-11-18 |
| lang | string | X | ko | ko |
| legalCheck | boolean | X | true | false |

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 520 (BE가 만든 documentId) |

**Body**

```json
{
  "file": "(binary) contract.pdf",
  "userId": 1024,
  "docTypeHint": "LEASE_CONTRACT",
  "ocrResult": "(선택, JSON 문자열) { pages: [ { page: 1, text: '...', blocks: [ { text: '임대보증금 ...', bbox: [0.12, 0.34, 0.52, 0.03], confidence: 0.93 } ] } ] }"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| file | file | O | PDF 또는 이미지 파일 1개 |
| userId | number | O | 사용자 ID (프로필 조회용) |
| ocrResult | string(JSON) | X | APP 기기 내 OCR 결과 (페이지별 text, blocks[].bbox·confidence). 있으면 서버 OCR 생략 |
| docTypeHint | string | X | 사용자가 미리 고른 문서 종류(분류 보조용) |

### Response

**200 OK**

```json
{
  "documentId": 520,
  "docType": "LEASE_CONTRACT",
  "docTypeConfidence": 0.96,
  "docCategory": "CONTRACT",
  "classificationReasons": ["양 당사자 서명란 존재", "계약기간·보증금 조항 존재"],
  "title": "주택 임대차 계약서",
  "summary": "마포구 OO빌라 302호 월세 계약. 보증금 1,000만원, 월세 55만원, 매월 25일 납부, 계약기간 2026-12-01 ~ 2028-11-30.",
  "parties": [
    { "role": "LESSOR", "name": "홍길동" },
    { "role": "LESSEE", "name": "김OO" }
  ],
  "fields": [
    {
      "key": "deposit",
      "label": "보증금",
      "originalLabel": "임대보증금",
      "value": 10000000,
      "valueType": "AMOUNT",
      "evidence": "임대보증금 금 일천만원정(₩10,000,000)",
      "evidenceLocation": { "page": 1, "bbox": [0.12, 0.34, 0.52, 0.03] },
      "confidence": 0.98,
      "inferred": false,
      "needsReview": false
    },
    {
      "key": "monthlyRent",
      "label": "월세",
      "originalLabel": "차임",
      "value": 550000,
      "valueType": "AMOUNT",
      "evidence": "차임은 월 금 오십오만원정으로 하고 매월 25일에 지불한다",
      "evidenceLocation": { "page": 1, "bbox": [0.12, 0.40, 0.60, 0.03] },
      "confidence": 0.95,
      "inferred": false,
      "needsReview": false
    },
    {
      "key": "contractStartDate",
      "label": "계약 시작일",
      "originalLabel": "임대차 기간(시작)",
      "value": "2026-12-01",
      "valueType": "DATE",
      "evidence": "2026년 12월 1일부터",
      "evidenceLocation": { "page": 1, "bbox": [0.12, 0.46, 0.40, 0.03] },
      "confidence": 0.97,
      "inferred": false,
      "needsReview": false
    },
    {
      "key": "contractEndDate",
      "label": "계약 만료일",
      "originalLabel": "임대차 기간(종료)",
      "value": "2028-11-30",
      "valueType": "DATE",
      "evidence": "2028년 11월 30일까지",
      "evidenceLocation": { "page": 1, "bbox": [0.52, 0.46, 0.40, 0.03] },
      "confidence": 0.97,
      "inferred": false,
      "needsReview": false
    },
    {
      "key": "earlyTerminationPenalty",
      "label": "중도 해지 위약금",
      "originalLabel": "제5조 중도 해지",
      "value": { "description": "중도 해지 시 잔여 기간 월세의 10% 위약금", "amount": null, "rate": 0.1 },
      "valueType": "PENALTY",
      "evidence": "중도 해지 시 ... 10%를 위약금으로 한다",
      "evidenceLocation": { "page": 2, "bbox": [0.10, 0.62, 0.80, 0.05] },
      "confidence": 0.62,
      "inferred": false,
      "needsReview": true
    }
  ],
  "conditions": {
    "paymentSchedules": [
      { "fieldKey": "monthlyRent", "label": "월세 납부", "recurrence": "MONTHLY", "dayOfMonth": 25, "startDate": "2026-12-25", "endDate": "2028-11-25" }
    ]
  },
  "calendarEvents": [
    { "type": "PAYMENT", "fieldKey": "monthlyRent", "title": "월세 55만원 납부", "date": "2026-12-25", "rrule": "FREQ=MONTHLY;BYMONTHDAY=25;UNTIL=20281125", "remindBeforeDays": [3, 1] },
    { "type": "ACTION", "fieldKey": "contractEndDate", "title": "계약 갱신 여부 통보", "date": "2028-09-30", "rrule": null, "remindBeforeDays": [14, 7, 1] },
    { "type": "EXPIRY", "fieldKey": "contractEndDate", "title": "임대차 계약 만료", "date": "2028-11-30", "rrule": null, "remindBeforeDays": [60, 30, 7] }
  ],
  "needsReview": true,
  "userRelevance": {
    "level": "RELEVANT",
    "reasons": ["임차인으로 계약한 서류예요.", "사는 지역(서울 마포구)의 주택 계약이에요."]
  },
  "fullText": "[1페이지]\n주택 임대차 계약서\n임대보증금 금 일천만원정(₩10,000,000) ...\n[2페이지]\n제5조 중도 해지 시 ...",
  "pageCount": 2,
  "legalCheck": {
    "riskLevel": "MEDIUM",
    "issues": [
      {
        "severity": "MEDIUM",
        "title": "중도 해지 위약금 조항 확인 필요",
        "explanation": "위약금이 지나치게 크면 법원이 줄일 수 있어요. 해지 전에 집주인과 금액을 확인해 두세요.",
        "source": "LLM",
        "fieldKey": "earlyTerminationPenalty",
        "matchedCondition": { "penaltyRate": 0.1 },
        "clauseEvidence": { "text": "중도 해지 시 ... 10%를 위약금으로 한다", "page": 2, "bbox": [0.10, 0.62, 0.80, 0.05] },
        "laws": [ { "lawName": "민법", "article": "제398조", "excerpt": "손해배상의 예정액이 부당히 과다한 경우에는 ...", "url": "https://www.law.go.kr/..." } ],
        "recommendedAction": "해지 전 위약금 금액을 서면으로 확인하세요.",
        "needsExpert": false
      }
    ],
    "skippedRules": [],
    "helpContacts": [ { "name": "대한법률구조공단", "phone": "132" } ],
    "lawVersionDate": "2026-11-16",
    "disclaimer": "이 내용은 법률 자문이 아닌 참고 정보예요. 정확한 판단은 전문가와 상담하세요."
  },
  "priority": {
    "level": "HIGH",
    "score": 86,
    "reasons": ["매월 55만원이 나가는 납부 일정이 있어요.", "중도 해지 위약금 조항이 있어요."],
    "keyCost": { "fieldKey": "deposit", "label": "보증금", "amount": 10000000 },
    "keyDeadline": { "fieldKey": "contractEndDate", "label": "계약 만료", "date": "2028-11-30", "actionLabel": "갱신 통보", "actionDate": "2028-09-30" }
  },
  "warnings": []
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| documentId | number | path의 id 그대로 |
| fullText | string | OCR 원문 전체. 페이지마다 [N페이지] 표시. **BE가 저장해야 채팅에서 쓸 수 있음** |
| pageCount | number | 페이지 수 |
| docType | string | 문서 종류 |
| docCategory | string | 대분류 CONTRACT / BILL / AD / INFO (계약서 vs 고지서·광고 차이 표시용) |
| classificationReasons[] | array | 종류 판별 근거 문장 |
| summary | string | 3줄 이내 요약 |
| fields[] | array | **모든 추출값**. key(표준 키), label, originalLabel(원문 항목명), value, valueType(AMOUNT / DATE / PENALTY / TEXT / NUMBER), evidence, evidenceLocation(page, bbox), confidence, inferred, needsReview |
| fields[].needsReview | boolean | confidence 0.7 미만 또는 inferred면 true. APP이 이 항목만 확인 요청 |
| conditions.paymentSchedules[] | array | 반복 납부 규칙. fieldKey로 금액을 fields에서 찾음 |
| calendarEvents[] | array | 캘린더 일정. type(PAYMENT / EXPIRY / ACTION), fieldKey, title, date, rrule, remindBeforeDays. BE가 하나씩 deadlines로 저장 |
| needsReview | boolean | fields 중 하나라도 needsReview면 true |
| userRelevance | object \| null | level(RELEVANT / PARTIAL / NOT_RELEVANT), reasons[](최대 3개). 프로필이 없으면 null |
| legalCheck | object \| null | 법적 위험 점검 결과 (건너뛰면 null) |
| legalCheck.riskLevel | string | HIGH / MEDIUM / LOW / NONE |
| legalCheck.issues[] | array | severity, title, explanation, source(RULE/LLM), fieldKey(관련 항목, 없으면 null), matchedCondition, clauseEvidence, laws[](필수), recommendedAction, needsExpert |
| legalCheck.skippedRules[] | array | 정보가 없어 못 한 점검 |
| legalCheck.disclaimer | string | APP에 반드시 표시 |
| priority.level | string | 중요도 HIGH / MEDIUM / LOW |
| priority.score | number | 0~100 (같은 등급 안 정렬용) |
| priority.reasons[] | string[] | 판단 이유 (최대 3개) |
| priority.keyCost | object \| null | 대표 비용 fieldKey, label, amount |
| priority.keyDeadline | object \| null | 대표 기한 fieldKey, label, date + 행동 기한 actionLabel, actionDate (같은 일정이 calendarEvents에도 있음) |

> 참고: AI 응답의 valueType에는 `PENALTY`, `NUMBER`가 있지만 [코드표 > 항목 값 형식 valueType](02-enums.md#항목-값-형식-valuetype)에는 `AMOUNT` / `DATE` / `TEXT`만 있습니다 (코드표: "AI가 다른 valueType을 쓰게 되면 여기에 더합니다").

**400 Bad Request**

```json
{ "code": "INVALID_FILE", "message": "지원하지 않는 파일 형식입니다. (pdf, jpg, png, heic)" }
```

**401 Unauthorized**

```json
{ "code": "INVALID_API_KEY", "message": "API 키가 올바르지 않습니다." }
```

**404 Not Found**

```json
{ "code": "USER_NOT_FOUND", "message": "사용자를 찾을 수 없습니다." }
```

**413 Payload Too Large**

```json
{ "code": "FILE_TOO_LARGE", "message": "파일은 20MB 이하만 업로드할 수 있습니다." }
```

**422 Unprocessable Entity**

BE는 failReason을 TEXT_NOT_RECOGNIZED로 저장합니다.

```json
{ "code": "TEXT_NOT_RECOGNIZED", "message": "문서에서 글자를 인식하지 못했습니다. 밝은 곳에서 다시 촬영해 주세요." }
```

**500 Internal Server Error**

BE는 500과 타임아웃(90초)을 failReason ANALYSIS_ERROR로 저장합니다.

```json
{ "code": "AI_ANALYSIS_FAILED", "message": "문서 분석 중 오류가 발생했습니다." }
```

---

## 12. 쉬운말 요약 카드 (AI 서버 API)

`GET /ai/documents/summary-card`

> **BE → AI 서버** 호출 API입니다 (X-API-Key). 앱에서는 호출하지 않습니다. 담당: BE, AI
>
> 참고: 노션 원본의 Method는 `GET`인데 JSON Body(`documentId`, `userId`)를 보내도록 되어 있습니다. GET 요청 Body는 서버·클라이언트에 따라 무시될 수 있어 `POST`로 바꾸거나 쿼리 파라미터로 옮길지 AI 팀과 확정이 필요합니다.

### 개요

- 분석된 문서를 화면의 'AI 정리본' 카드로 보여줄 데이터를 만듭니다. 말을 풀어 쓰는 게 아니라 **중요 쟁점만 "항목: 값" 형태로 짧게** 정리합니다.
- 카드 구성: 한 줄 요약 → 문서 제목 → 개요(항목: 값) → 주요 특약(짧은 목록) → 체크(확인할 일 목록)
- 예) 계약 개요 → 임대인: 김○○ / 보증금: 230,000,000원 / 계약기간: 2025.03.01 – 2027.02.28
- 카드 뒷면(중요도·비용·D-day)은 문서 분석 응답의 priority를 씁니다.

### 기술 설명/요구 사항

- 흐름: APP → BE → AI 서버. BE는 documentId만 보내고, AI 서버가 BE 서류 상세 조회 `GET /documents/{id}?userId=`(X-Internal-Key)로 최신 분석 결과(사용자 수정 반영)를 조회합니다.
- KEY_VALUE의 fieldKey는 fields에 있는 key만 씁니다. 당사자(parties)나 여러 항목을 합친 값(계약기간)은 null입니다.
- 값은 새로 만들지 않고 분석 결과(fields, conditions)에서만 가져옵니다. 없는 항목은 카드에서 뺍니다.
- **표기 규칙**
  - 날짜: YYYY.MM.DD, 기간은 "시작 – 끝" (예: 2025.03.01 – 2027.02.28)
  - 금액: 천 단위 콤마 + 원 (예: 230,000,000원). 한 줄 요약에서만 "2억 3천만 원"처럼 읽기 쉬운 단위 사용
  - 이름·주소의 일부는 마스킹 (예: 김○○, ○○빌라)
  - label 6자 이하, 특약·체크 항목은 한 줄 25자 이하
- **한 줄 요약**: 핵심 금액 + 기간 + 꼭 해야 할 행동 1개, 2문장 이하
- **문서 종류별 개요 항목**

| docType | 섹션 제목 | 항목 |
| --- | --- | --- |
| LEASE_CONTRACT | 계약 개요 | 임대인, 임차인, 소재지, 보증금, 월세, 계약기간 |
| EMPLOYMENT_CONTRACT | 근로 개요 | 근무처, 근무기간, 근무시간, 시급·월급, 급여일 |
| SERVICE_CONTRACT | 계약 개요 | 계약 상대, 서비스, 금액, 계약기간, 해지 조건 |
| BILL | 청구 개요 | 청구처, 청구금액, 납부기한, 납부방법 |
| NOTICE | 안내 개요 | 대상, 일시, 장소, 준비물 |

- **주요 특약**: 일반 조항이 아닌 이 문서만의 특별 조건, 최대 5개
- **체크**: 사용자가 확인하거나 해야 할 일 (예: 확정일자, 전입신고). 문서에서 완료가 확인되면 done: true(예: 확정일자 도장), 아니면 false. 이후 체크 상태 변경은 BE·APP이 관리
- 결과는 documentId 기준으로 캐시하고, 분석 결과가 수정되면 BE가 다시 호출합니다. 타임아웃 15초.

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
  "documentId": 520,
  "userId": 1024
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| documentId | number | O | 문서 ID (AI 서버가 BE에서 분석 결과 조회) |
| userId | number | O | 사용자 ID (BE 서류 조회 때 소유자 확인) |

### Response

**200 OK**

```json
{
  "documentId": 520,
  "category": "집 · 임대",
  "title": "전세 임대차 계약서",
  "oneLineSummary": "보증금 2억 3천만 원, 2025.03.01부터 2년 계약. 만기 2개월 전까지 갱신 여부를 집주인에게 알려야 해요.",
  "sections": [
    {
      "type": "KEY_VALUE",
      "title": "계약 개요",
      "items": [
        { "label": "임대인", "value": "김○○", "fieldKey": null },
        { "label": "임차인", "value": "이지수", "fieldKey": null },
        { "label": "소재지", "value": "마포구 성산동 ○○빌라 302호", "fieldKey": "propertyAddress" },
        { "label": "보증금", "value": "230,000,000원", "fieldKey": "deposit" },
        { "label": "계약기간", "value": "2025.03.01 – 2027.02.28", "fieldKey": null }
      ]
    },
    {
      "type": "BULLET",
      "title": "주요 특약",
      "items": [
        { "text": "만기 6개월~2개월 전 갱신 여부 통지" },
        { "text": "반려동물은 사전 동의 후 가능" },
        { "text": "원상복구는 임차인 과실 손상에 한함" }
      ]
    },
    {
      "type": "CHECKLIST",
      "title": "체크",
      "items": [
        { "checkKey": "fixedDate", "text": "확정일자 받음 (2025.02.20)", "done": true },
        { "checkKey": "moveInReport", "text": "전입신고 완료", "done": true }
      ]
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| category | string | 카드 상단 분류 (예: 집 · 임대, 일 · 근로, 생활 · 고지서) |
| title | string | 문서 제목 |
| oneLineSummary | string | 한 줄 요약 (2문장 이하) |
| sections[].type | string | KEY_VALUE(항목: 값) / BULLET(짧은 목록) / CHECKLIST(체크 목록) |
| sections[].title | string | 섹션 제목 |
| sections[].items[] | array | KEY_VALUE: label, value, fieldKey / BULLET: text / CHECKLIST: checkKey, text, done |
| fieldKey | string \| null | 원본 항목 키 (탭하면 서류 뒤집기에서 근거 위치 표시). fields에 없으면 null |

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "documentId가 필요합니다." }
```

**401 Unauthorized**

```json
{ "code": "INVALID_API_KEY", "message": "API 키가 올바르지 않습니다." }
```

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "BE에서 문서를 찾을 수 없습니다." }
```

**500 Internal Server Error**

```json
{ "code": "AI_GENERATION_FAILED", "message": "요약 카드 생성 중 오류가 발생했습니다." }
```
