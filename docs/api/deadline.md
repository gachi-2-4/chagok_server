# DEADLINE · REMINDER API 명세

> 원본: 노션 「API명세서」 > NOTIFICATION DB 중 기한(`/deadlines`)·기한 알림(`/reminders`) API
>
> 노션에서는 NOTIFICATION DB에 함께 들어 있지만, 캘린더 기한과 기한 알림 예약은 알림 목록·설정과 성격이 달라 문서를 분리했습니다. 알림 목록·설정·푸시 토큰은 [notification.md](notification.md), 메인 화면은 [home.md](home.md)에 있습니다.
> 공통 규칙은 [00-common.md](00-common.md), 코드표(기한 종류 deadline type, 기한 출처 source)는 [02-enums.md](02-enums.md#기한-종류-deadline-type)를 참고합니다.

## 목록

| # | 이름 | Method | Path |
| --- | --- | --- | --- |
| 1 | [기한 목록 조회](#1-기한-목록-조회) | GET | `/deadlines` |
| 2 | [기한 추가](#2-기한-추가) | POST | `/deadlines` |
| 3 | [기한 수정](#3-기한-수정) | PATCH | `/deadlines/{id}` |
| 4 | [기한 삭제](#4-기한-삭제) | DELETE | `/deadlines/{id}` |
| 5 | [기한 알림 등록](#5-기한-알림-등록) | POST | `/reminders` |
| 6 | [기한 알림 수정](#6-기한-알림-수정) | PATCH | `/reminders/{id}` |
| 7 | [기한 알림 삭제](#7-기한-알림-삭제) | DELETE | `/reminders/{id}` |

---

## 1. 기한 목록 조회

`GET /deadlines`

### 개요

- 기간 안의 기한을 날짜순으로 조회합니다. 서류에서 뽑은 납부일·계약 만료·갱신 통보 마감·청구 마감 등이 모두 들어 있습니다.
- 화면: 12 캘린더 (달력 점, 다가오는 기한), 13 캘린더 날짜 선택

### 기술 설명/요구 사항

- `from`·`to`로 기간을 정합니다 (최대 1년).
  - 12 달력 점: 그 달 1일–말일 (`from=2026-11-01&to=2026-11-30`). 앱이 날짜별로 묶어 점을 찍고, 알림이 있는 기한(`reminderId`가 있음)은 "기한 알림 등록됨" 색으로 찍습니다.
  - 12 다가오는 기한: 오늘부터 1년, 완료한 기한 빼고 (`from=오늘&to=1년 뒤&includeCompleted=false`)
  - 13 날짜 선택: 그날 하루 (`from=to=2026-11-25`). "전체 보기"를 누르면 다가오는 기한 조회로 돌아갑니다.
- 매월 반복 기한은 기간 안의 회차마다 한 줄씩 펼쳐서 줍니다. 같은 기한의 회차는 `deadlineId`가 같고 `date`가 다릅니다.
- 오른쪽 달력 아이콘은 `reminderId`가 있으면 채운 아이콘(알림 등록됨), 없으면 빈 아이콘입니다. 누르면 기한 알림 등록(`POST /reminders`, `deadlineId`만 보내서 기본값으로 등록) 또는 기한 알림 삭제(`DELETE /reminders/{id}`)를 부릅니다.
- `isCompleted`는 알림에서 완료로 표시한 회차입니다.
- 분석 중이거나 실패한 서류에는 기한이 없습니다.
- 사용자가 기한을 직접 추가·수정·삭제할 수 있습니다 (기한 추가·수정·삭제 API). 사용자가 추가한 기한은 `source=USER`입니다.
- 기한은 AI 문서 분석의 `calendarEvents`(납부·만료 일정)와 `priority.keyDeadline.actionDate`(그 전에 해야 할 행동 기한, 예: 갱신 통보)로 만듭니다. 기한 종류는 `PAYMENT` / `EXPIRY` / `ACTION` / `ETC`입니다 (코드표 참고).
  - > 참고: [서류 상세 조회](document.md#4-서류-상세-조회)에는 "행동 기한도 `calendarEvents`에 `type: ACTION`으로 들어오므로 `priority.keyDeadline.actionDate`로 따로 만들지 않습니다 (중복 방지)"라고 되어 있습니다. 구현 시에는 서류 상세 조회 쪽 규칙(중복 생성 금지)과 맞춰야 합니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| from | string | X | 오늘 | 2026-11-01 |
| to | string | X | from + 30일 | 2026-11-30 |
| includeCompleted | boolean | X | true | false |
| boxId | number | X | - | 1 |
| documentId | number | X | - | 520 |

**Path Variable**: 없음

**Body**: 없음

### Response

**200 OK**

```json
{
  "from": "2026-11-12",
  "to": "2027-11-12",
  "content": [
    {
      "deadlineId": 80,
      "date": "2026-11-15",
      "dDay": 3,
      "type": "ACTION",
      "label": "보험금 청구 마감",
      "amount": null,
      "document": { "documentId": 560, "title": "실손보험 청구 안내" },
      "box": { "boxId": 4, "name": "병원 · 보험" },
      "source": "AI",
      "isCompleted": false,
      "reminderId": null
    },
    {
      "deadlineId": 75,
      "date": "2026-11-25",
      "dDay": 13,
      "type": "PAYMENT",
      "label": "관리비 납부",
      "amount": 85000,
      "document": { "documentId": 540, "title": "11월 관리비 고지서" },
      "box": { "boxId": 1, "name": "집 · 임대" },
      "source": "AI",
      "isCompleted": false,
      "reminderId": null
    },
    {
      "deadlineId": 71,
      "date": "2026-12-28",
      "dDay": 46,
      "type": "ACTION",
      "label": "갱신 통보 마감",
      "amount": null,
      "document": { "documentId": 520, "title": "전세 임대차 계약서" },
      "box": { "boxId": 1, "name": "집 · 임대" },
      "source": "AI",
      "isCompleted": false,
      "reminderId": 31
    }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].deadlineId | number | 기한 ID (기한 알림 등록에 사용) |
| content[].date | string | 기한 날짜 (반복 기한이면 이 회차의 날짜) |
| content[].dDay | number | 오늘 기준 남은 일수 (12 "D-3") |
| content[].type | string | 기한 종류 (코드표 참고) |
| content[].label | string | 기한 이름 |
| content[].amount | number | 낼 금액 (없으면 null) |
| content[].document | object | 서류 (12 목록 제목) |
| content[].box | object | 박스 (12 목록 "11월 15일 · 병원 · 보험") |
| content[].isCompleted | boolean | 완료로 표시했는지 |
| content[].reminderId | number | 등록된 기한 알림 ID (없으면 null) |
| content[].source | string | `AI`(AI가 뽑음) / `USER`(사용자가 추가) |

**400 Bad Request**

```json
{ "code": "INVALID_DATE_RANGE", "message": "기간은 1년까지 조회할 수 있어요." }
```

---

## 2. 기한 추가

`POST /deadlines`

### 개요

- 서류에 날짜가 없거나 AI가 날짜를 못 뽑았을 때, 사용자가 기한을 직접 추가합니다.
- 화면: 캘린더 "기한 추가" (피그마 12·13에는 아직 버튼 없음)

### 기술 설명/요구 사항

- 기한은 내 서류 하나에 붙습니다 (`documentId` 필수). 분석이 끝난 서류(`REVIEW_REQUIRED` / `CONFIRMED`)만 됩니다.
- 추가한 기한은 `source=USER`입니다. 캘린더·메인 미니 캘린더·서류 상세에 AI 기한과 같이 보입니다.
- 매월 반복(`recurrence=MONTHLY`)이면 `date`를 첫 회차로 보고, 그 날짜의 일자를 매월 반복합니다. `endDate`까지 반복합니다 (없으면 계속).
- 추가하면 기한 알림을 자동으로 예약합니다 (한 달 전·일주일 전·하루 전, 기한 알림 등록 참고).
- 사용자가 추가한 기한은 문서 분석 결과 조회 내부 API의 `calendarEvents`에 `userEdited: true`로 넣어, AI 채팅이 최신 기한을 알게 합니다.

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
  "documentId": 545,
  "type": "EXPIRY",
  "label": "확정일자 재확인",
  "date": "2027-02-01",
  "recurrence": null,
  "endDate": null,
  "amount": null
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| documentId | number | O | 기한을 붙일 서류 |
| type | string | O | `PAYMENT` / `EXPIRY` / `ACTION` / `ETC` |
| label | string | O | 기한 이름 (1–30자) |
| date | string(date) | O | 기한 날짜 (매월 반복이면 첫 회차) |
| recurrence | string | X | `MONTHLY` 또는 null |
| endDate | string(date) | X | 반복 끝나는 날 |
| amount | number | X | 낼 금액 (원) |

### Response

**201 Created**

```json
{
  "deadlineId": 90,
  "documentId": 545,
  "documentTitle": "전입신고 확인서",
  "type": "EXPIRY",
  "label": "확정일자 재확인",
  "date": "2027-02-01",
  "recurrence": null,
  "endDate": null,
  "amount": null,
  "dDay": 81,
  "source": "USER",
  "userEdited": false,
  "isCompleted": false,
  "reminder": { "reminderId": 40, "alertTime": "09:00", "advanceDays": [30, 7, 1] },
  "createdAt": "2026-11-12T15:00:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| deadlineId | number | 기한 ID |
| source | string | `USER` (사용자가 추가) |
| reminder | object | 자동으로 예약한 기한 알림 |
| 나머지 | - | 서류 상세 `deadlines[]`와 같은 형식 |

**400 Bad Request**

```json
{ "code": "INVALID_DEADLINE", "message": "기한 이름과 날짜를 확인해 주세요." }
```

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "서류를 찾을 수 없어요." }
```

**409 Conflict**

```json
{ "code": "DOCUMENT_NOT_READY", "message": "분석이 끝난 서류에만 기한을 추가할 수 있어요." }
```

---

## 3. 기한 수정

`PATCH /deadlines/{id}`

> 담당: BE

### 개요

- AI가 뽑은 날짜가 틀렸거나 사용자가 추가한 기한을 고칩니다.
- 화면: 캘린더 기한 목록에서 기한 수정

### 기술 설명/요구 사항

- 보낸 필드만 바꿉니다. AI가 뽑은 기한을 고치면 `source`는 `AI` 그대로이고 `userEdited=true`가 됩니다.
- AI 기한이 서류 항목에서 나온 경우 그 항목도 같이 고칩니다. 서류 상세·AI 정리본·AI 채팅이 같은 값을 쓰게 하려는 것입니다.
  - 예) "계약 만료" 날짜 → `contractEndDate` 항목
  - 예) "월세 납부" 날짜 → `paymentSchedules`의 `dayOfMonth`
  - 항목을 고친 뒤의 처리는 서류 내용 수정과 같습니다 (요약 카드 다시 요청, 비교 결과 `outdated`, 문서 분석 결과 조회에 `userEdited` 반영).
- 매월 반복 기한의 `date`를 바꾸면 앞으로의 회차 날짜(매월 n일)가 바뀝니다. 지난 회차는 바뀌지 않습니다.
- 기한 알림의 예약 시각을 다시 계산합니다.

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
| id | number | O | 72 |

**Body**

```json
{
  "date": "2027-03-01"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| label | string | X | 기한 이름 (1–30자) |
| date | string(date) | X | 기한 날짜 |
| type | string | X | `PAYMENT` / `EXPIRY` / `ACTION` / `ETC` |
| recurrence | string | X | `MONTHLY` 또는 null |
| endDate | string(date) | X | 반복 끝나는 날 |
| amount | number | X | 낼 금액 (원) |

### Response

**200 OK**

```json
{
  "deadlineId": 72,
  "documentId": 520,
  "documentTitle": "전세 임대차 계약서",
  "type": "EXPIRY",
  "label": "계약 만료",
  "date": "2027-03-01",
  "recurrence": null,
  "endDate": null,
  "amount": null,
  "dDay": 109,
  "source": "AI",
  "userEdited": true,
  "isCompleted": false,
  "reminder": { "reminderId": 32, "alertTime": "09:00", "advanceDays": [30, 7, 1] },
  "createdAt": "2026-11-10T13:05:24+09:00"
}
```

기한 추가 응답과 같은 형식입니다.

**400 Bad Request**

```json
{ "code": "INVALID_DEADLINE", "message": "기한 이름과 날짜를 확인해 주세요." }
```

**404 Not Found**

```json
{ "code": "DEADLINE_NOT_FOUND", "message": "기한을 찾을 수 없어요." }
```

---

## 4. 기한 삭제

`DELETE /deadlines/{id}`

### 개요

- 잘못 뽑힌 기한이나 필요 없는 기한을 지웁니다.
- 화면: 캘린더 기한 목록에서 기한 삭제

### 기술 설명/요구 사항

- 기한 알림과 예약된 푸시도 같이 지웁니다. 이미 보낸 알림(알림 목록)은 남깁니다.
- 매월 반복 기한을 지우면 앞으로의 회차가 모두 사라집니다.
- AI가 뽑은 기한은 지운 기록을 남겨, 서류를 다시 분석해도(서류 종류 변경 등) 되살리지 않습니다.
- 기한이 나온 서류 항목(예: `contractEndDate`)은 지우지 않습니다. 항목 값을 고치려면 서류 내용 수정을 씁니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 72 |

**Body**: 없음

### Response

**204 No Content**

응답 본문 없음

**404 Not Found**

```json
{ "code": "DEADLINE_NOT_FOUND", "message": "기한을 찾을 수 없어요." }
```

---

## 5. 기한 알림 등록

`POST /reminders`

### 개요

- 기한 알림을 다시 켭니다. 기한 알림은 기한이 생길 때 자동으로 예약되므로, 이 API는 껐던 알림을 다시 켤 때 씁니다.
- 화면: 12 캘린더 빈 달력 아이콘 누르기, 07·08 "기한 알림 등록", D02 "알림 등록하기" (알림이 없을 때만)
- D02 흐름: 알림은 대부분 자동으로 만들어져 있으므로, 앱은 서류 상세의 `deadlines[].reminder`가 있으면 기한 알림 수정(`PATCH /reminders/{id}`), 없으면 이 API를 부릅니다. 버튼 문구는 디자인대로 "알림 등록하기"를 써도 됩니다.

### 기술 설명/요구 사항

- 자동 예약 (기획 흐름 기준)
  - 기한이 생기면(AI 분석 또는 기한 추가) BE가 기한 알림을 자동으로 만듭니다.
  - 기본값은 기한 종류에 따라 다릅니다. 알림 시각은 알림 설정의 `defaultAlertTime`(기본 09:00)입니다.
    - 매월 반복 납부(`PAYMENT`, D02): 하루 전·당일 `[1, 0]` (D02 디자인 기본 선택)
    - 한 번뿐인 납부(`PAYMENT`): 일주일 전·하루 전·당일 `[7, 1, 0]`
    - 만료·행동 기한(`EXPIRY`, `ACTION`, `ETC`): 한 달 전·일주일 전·하루 전 `[30, 7, 1]` (M00 "임대차계약서 만기가 30일 남았어요")
  - D02 미리 알림 칩은 당일(0)·1일 전·3일 전·7일 전입니다. D02는 납부 기한 화면이라 30일 전 칩이 없습니다. 만료·행동 기한의 알림을 바꾸는 화면은 디자인에 없으며, 생기면 30일 전 칩을 더합니다.
  - AI가 준 추천값(`suggestedAdvanceDays`)은 쓰지 않습니다. 기획 흐름의 간격을 기준으로 합니다.
- 기한 하나에 알림 하나입니다. 이미 있으면 409이며, 바꿀 때는 기한 알림 수정 API를 씁니다.
- `deadlineId`만 보내면 위 기본값으로 다시 켭니다. 값을 보내면 그 값으로 켭니다.
- `advanceDays`는 0–90 사이 정수 1–5개입니다.
- `repeatMonthly`는 매월 반복 기한에만 씁니다. false면 가장 가까운 회차 한 번만 보냅니다.
- 그 달에 납부일이 없으면(예: 31일) 그 달 말일을 기한으로 봅니다. 반복 기한은 `endDate` 이후로는 반복하지 않습니다.
- 발송: 스케줄러가 10분마다 보낼 알림을 찾아 `DEADLINE_DUE` 알림을 만들고 푸시를 보냅니다. 알림 설정의 `deadlineAlertEnabled`가 꺼져 있으면 알림 목록에만 쌓고 푸시는 보내지 않습니다.
- D02 "다음 알림: 11월 24일 오전 9:00"은 저장하기 전에도 보여야 하므로 앱이 납부일·알림 시간·미리 알림으로 계산해 보여 줍니다. 저장 뒤에는 응답의 `nextAlertAt`으로 맞춥니다.

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
  "deadlineId": 72
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| deadlineId | number | O | 알림을 켤 기한 |
| alertTime | string | X | 알림 시각 `HH:mm` (10분 단위, 기본값 defaultAlertTime) |
| advanceDays | number[] | X | 며칠 전에 알릴지 (0–90, 1–5개, 0은 당일. 기본값은 위 기한 종류별 기본값) |
| repeatMonthly | boolean | X | 매월 반복 (반복 기한만, 기본값 true) |

### Response

**201 Created**

```json
{
  "reminderId": 32,
  "deadline": {
    "deadlineId": 72,
    "type": "EXPIRY",
    "label": "계약 만료",
    "date": "2027-02-28",
    "recurrence": null,
    "amount": null,
    "document": { "documentId": 520, "title": "전세 임대차 계약서" }
  },
  "alertTime": "09:00",
  "advanceDays": [30, 7, 1],
  "repeatMonthly": false,
  "endDate": null,
  "nextAlertAt": "2027-01-29T09:00:00+09:00",
  "createdAt": "2026-11-18T10:40:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| reminderId | number | 기한 알림 ID |
| deadline | object | 알림을 건 기한 |
| endDate | string | 반복이 끝나는 날 (없으면 null) |
| nextAlertAt | string | 다음 알림 일시 |
| 나머지 | - | 저장된 설정값 |

**400 Bad Request**

```json
{ "code": "INVALID_ADVANCE_DAYS", "message": "미리 알림은 0일에서 90일 사이로 1개에서 5개까지 골라 주세요." }
```

```json
{ "code": "INVALID_ALERT_TIME", "message": "알림 시각은 10분 단위로 골라 주세요." }
```

**404 Not Found**

```json
{ "code": "DEADLINE_NOT_FOUND", "message": "기한을 찾을 수 없어요." }
```

**409 Conflict**

```json
{ "code": "REMINDER_ALREADY_EXISTS", "message": "이 기한에는 이미 알림이 있어요." }
```

```json
{ "code": "DEADLINE_PASSED", "message": "이미 지난 기한이에요." }
```

---

## 6. 기한 알림 수정

`PATCH /reminders/{id}`

### 개요

- 등록한 기한 알림의 알림 시간·미리 알림·반복을 바꿉니다.
- 화면: D02 "알림 등록하기" (알림이 이미 있을 때. 자동으로 만든 알림이 있으므로 D02는 대부분 이 API를 부름)

### 기술 설명/요구 사항

- 보낸 필드만 바꿉니다. 값 규칙은 기한 알림 등록과 같습니다.
- 자동으로 만든 알림(기한 종류별 기본값, 기한 알림 등록 참고)도 이 API로 바꿉니다.
- D02 칩: 당일 `0` / 1일 전 `1` / 3일 전 `3` / 7일 전 `7`. 칩에 없는 값(예: 30)이 들어 있는 알림은 앱이 해당 칩 없이 보여 주고, 사용자가 칩을 바꾸면 보낸 값으로 통째로 바뀝니다.
- 바꾸면 아직 보내지 않은 예약 알림을 다시 계산합니다. 이미 보낸 알림은 그대로 둡니다.
- 금액·납부일 같은 기한 값은 여기서 바꾸지 않습니다. D01 서류 내용 수정으로 고치면 기한과 알림이 같이 바뀝니다.

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
| id | number | O | 33 |

**Body**

```json
{
  "alertTime": "08:00",
  "advanceDays": [1, 3]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| alertTime | string | X | 알림 시각 `HH:mm` (10분 단위) |
| advanceDays | number[] | X | 며칠 전에 알릴지 (0–90, 1–5개) |
| repeatMonthly | boolean | X | 매월 반복 (반복 기한만) |

### Response

**200 OK**

기한 알림 등록의 201 응답과 같은 형식입니다.

```json
{
  "reminderId": 33,
  "deadline": {
    "deadlineId": 70,
    "type": "PAYMENT",
    "label": "월세 납부",
    "date": "2026-11-25",
    "recurrence": "MONTHLY",
    "amount": 500000,
    "document": { "documentId": 501, "title": "임대차계약서" }
  },
  "alertTime": "08:00",
  "advanceDays": [1, 3],
  "repeatMonthly": true,
  "endDate": "2027-11-24",
  "nextAlertAt": "2026-11-22T08:00:00+09:00",
  "createdAt": "2026-11-18T10:40:00+09:00"
}
```

**400 Bad Request**

```json
{ "code": "INVALID_ADVANCE_DAYS", "message": "미리 알림을 하나 이상 골라 주세요." }
```

**404 Not Found**

```json
{ "code": "REMINDER_NOT_FOUND", "message": "기한 알림을 찾을 수 없어요." }
```

---

## 7. 기한 알림 삭제

`DELETE /reminders/{id}`

### 개요

- 기한 알림을 끕니다.
- 화면: 12 캘린더 채운 달력 아이콘 누르기, D02 알림 끄기

### 기술 설명/요구 사항

- 예약된 알림을 모두 취소합니다. 이미 보낸 알림(N01 목록)은 남깁니다.
- 기한과 서류는 지우지 않습니다. 다시 켜려면 기한 알림 등록 API를 부릅니다.
- 기한이 생길 때 자동으로 만든 알림(한 달 전·일주일 전·하루 전)도 이 API로 끕니다. 끈 기한은 서류를 다시 분석해도 알림을 자동으로 다시 만들지 않습니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 33 |

**Body**: 없음

### Response

**204 No Content**

응답 본문 없음

**404 Not Found**

```json
{ "code": "REMINDER_NOT_FOUND", "message": "기한 알림을 찾을 수 없어요." }
```
