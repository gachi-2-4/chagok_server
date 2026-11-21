# NOTIFICATION API 명세

> 원본: 노션 「API명세서」 > NOTIFICATION DB 중 알림(`/notifications`) API
>
> 같은 DB에 있던 기한·기한 알림 API는 [deadline.md](deadline.md), 메인 화면 API는 [home.md](home.md)로 분리했습니다.
> 공통 규칙은 [00-common.md](00-common.md), 코드표(알림 type)는 [02-enums.md](02-enums.md#기타)를 참고합니다.

## 목록

| # | 이름 | Method | Path |
| --- | --- | --- | --- |
| 1 | [알림 목록 조회](#1-알림-목록-조회) | GET | `/notifications` |
| 2 | [알림 상세 조회](#2-알림-상세-조회) | GET | `/notifications/{id}` |
| 3 | [알림 상태 변경](#3-알림-상태-변경) | PATCH | `/notifications/{id}` |
| 4 | [알림 모두 읽음](#4-알림-모두-읽음) | PATCH | `/notifications/read-all` |
| 5 | [알림 설정 조회](#5-알림-설정-조회) | GET | `/notifications/settings` |
| 6 | [알림 설정 변경](#6-알림-설정-변경) | PATCH | `/notifications/settings` |
| 7 | [푸시 토큰 등록](#7-푸시-토큰-등록) | PUT | `/notifications/push-tokens/{deviceId}` |

---

## 1. 알림 목록 조회

`GET /notifications`

### 개요

- 받은 알림 목록과 읽지 않은 알림 수를 조회합니다.
- 화면: N01 알림 (전체/읽지 않음/완료, "새 알림 2개"), 04 내 보관함·M01 상단 "알림" 배지

### 기술 설명/요구 사항

- 알림 종류(`type`)
  - `DEADLINE_DUE`: 기한 알림 (기한 알림 등록 API로 예약한 알림. 납부일, 갱신 통보 마감 등)
  - `ANALYSIS_COMPLETED`: 서류 분석 완료 ("서류 정리가 끝났어요")
  - `ANALYSIS_FAILED`: 서류 분석 실패 (다시 찍기 안내)
  - `COMPARISON_COMPLETED`: 서류 비교 완료 (M00 "자동차 보험 계약서 비교가 완료됐어요"). 비교는 바로 응답하므로 목록에만 남기고 푸시는 보내지 않습니다. `comparison.comparisonId`로 C02 비교 결과로 갑니다. 처음부터 `isRead=true`로 만듭니다 (사용자가 이미 결과를 봄).
- `filter`
  - `ALL`: 전체
  - `UNREAD`: 읽지 않은 알림
  - `COMPLETED`: 완료로 표시한 `DEADLINE_DUE`
- 보낸 시각(`sentAt`) 최신순입니다. 90일이 지난 알림은 지웁니다.
- `unreadCount`는 filter와 관계없이 전체 안 읽은 수입니다.
- `deadline.dDay`는 오늘 기준 남은 일수입니다 (N01 "D-1"). `advanceDay`는 이 알림이 기한 며칠 전 알림이었는지입니다 (보낼 때 기준, 0은 당일). 완료로 표시한 알림은 앱이 `deadline.label` + "완료"(예: 관리비 납부 완료)로 보여 줍니다.
- 알림 설정이 꺼져 있거나 기기 권한이 없어 푸시를 못 받아도 목록에는 쌓입니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| filter | string | X | ALL | `UNREAD` (`ALL` / `UNREAD` / `COMPLETED`) |
| page | number | X | 0 | 0 |
| size | number | X | 20 | 20 |

**Path Variable**: 없음

**Body**: 없음

### Response

**200 OK**

```json
{
  "unreadCount": 2,
  "content": [
    {
      "notificationId": 3001,
      "type": "DEADLINE_DUE",
      "title": "내일은 월세 납부일이에요",
      "message": "11월 25일까지 500,000원을 내면 돼요.",
      "deadline": { "deadlineId": 70, "type": "PAYMENT", "label": "월세 납부", "date": "2026-11-25", "amount": 500000, "dDay": 1 },
      "advanceDay": 1,
      "document": { "documentId": 501, "title": "임대차계약서", "boxName": "집 · 임대" },
      "comparison": null,
      "isRead": false,
      "isCompleted": false,
      "completedAt": null,
      "sentAt": "2026-11-24T09:00:00+09:00"
    },
    {
      "notificationId": 2990,
      "type": "ANALYSIS_COMPLETED",
      "title": "서류 정리가 끝났어요",
      "message": "11월 통신요금 고지서를 확인해 주세요.",
      "deadline": null,
      "document": { "documentId": 533, "title": "11월 통신요금 고지서", "boxName": "통신 · 구독" },
      "isRead": false,
      "isCompleted": false,
      "completedAt": null,
      "sentAt": "2026-11-24T08:50:00+09:00"
    },
    {
      "notificationId": 2950,
      "type": "DEADLINE_DUE",
      "title": "오늘은 관리비 납부일이에요",
      "message": "오늘까지 85,000원을 내면 돼요.",
      "deadline": { "deadlineId": 74, "type": "PAYMENT", "label": "관리비 납부", "date": "2026-11-20", "amount": 85000, "dDay": -4 },
      "document": { "documentId": 530, "title": "10월 관리비 고지서", "boxName": "집 · 임대" },
      "isRead": true,
      "isCompleted": true,
      "completedAt": "2026-11-20T12:10:00+09:00",
      "sentAt": "2026-11-20T09:00:00+09:00"
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
| unreadCount | number | 안 읽은 알림 수 (N01 "새 알림 2개", 배지) |
| content[].type | string | `DEADLINE_DUE` / `ANALYSIS_COMPLETED` / `ANALYSIS_FAILED` / `COMPARISON_COMPLETED` |
| content[].title | string | 알림 제목 (푸시 제목과 같음) |
| content[].message | string | 알림 내용 (푸시 내용과 같음) |
| content[].deadline | object | 기한 (`DEADLINE_DUE`만, 나머지는 null) |
| content[].deadline.type | string | 기한 종류 (`PAYMENT`면 N02 버튼이 "납부 완료로 표시") |
| content[].advanceDay | number | 기한 며칠 전 알림인지 (`DEADLINE_DUE`만, 0은 당일) |
| content[].comparison | object | 비교 결과 (`COMPARISON_COMPLETED`만: comparisonId) |
| content[].document | object | 연결 서류 (삭제됐으면 null) |
| content[].isRead | boolean | 읽음 여부 |
| content[].isCompleted | boolean | 완료 여부 |
| content[].completedAt | string | 완료로 표시한 일시 (N01 "완료 · 11월 20일") |
| content[].sentAt | string | 보낸 일시 |

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "필터 값을 확인해 주세요." }
```

---

## 2. 알림 상세 조회

`GET /notifications/{id}`

### 개요

- 알림 한 건을 조회합니다.
- 화면: N02 월세 납부 알림 (제목·본문, 납부 정보, 연결 서류), 푸시 알림을 눌러 들어올 때

### 기술 설명/요구 사항

- 조회만 하고 읽음 처리는 하지 않습니다. 앱은 상세에 들어오면 알림 상태 변경 API로 `isRead=true`를 보냅니다.
- N02 머리 "2026-11-18 · 오전 9:00 · 집 · 임대"는 `sentAt`·`document.boxName`, 제목 "[납부] 내일은 월세 납부일이에요"는 앱이 `deadline.type`(PAYMENT → [납부])과 `title`을 붙여 그립니다.
- N02 본문은 앱이 세 줄을 붙여 그립니다. 알림에 이름을 저장하지 않기 위해서입니다.
  1. "안녕하세요, {이름}님." ← 내 정보 조회의 `name`
  2. "11월 25일까지 월세 500,000원을 내면 돼요." ← `message` (푸시 본문과 같음)
  3. "납부일을 놓치지 않도록 하루 전에 알려드려요." ← `advanceDay` (0 당일 / 1 하루 전 / 7 일주일 전 / 그 밖 N일 전)
- 납부 정보(금액·납부일)는 `deadline.amount`·`deadline.date`, 연결 서류는 `document`입니다.
- N02 아래 버튼은 `deadline.type`이 `PAYMENT`면 "납부 완료로 표시", 아니면 "완료로 표시"로 보여 줍니다.
- 연결 서류가 삭제됐으면 `document`는 null입니다 (N02 "연결된 서류 보기" 숨김).
- 푸시 데이터(data payload)에는 `notificationId`, `type`, `documentId`가 들어 있어 앱이 바로 이 화면으로 이동할 수 있습니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 3001 |

**Body**: 없음

### Response

**200 OK**

```json
{
  "notificationId": 3001,
  "type": "DEADLINE_DUE",
  "title": "내일은 월세 납부일이에요",
  "message": "11월 25일까지 500,000원을 내면 돼요.",
  "deadline": { "deadlineId": 70, "type": "PAYMENT", "label": "월세 납부", "date": "2026-11-25", "amount": 500000, "dDay": 1 },
  "reminderId": 33,
  "advanceDay": 1,
  "comparison": null,
  "document": { "documentId": 501, "title": "임대차계약서", "boxName": "집 · 임대" },
  "isRead": true,
  "readAt": "2026-11-24T09:12:00+09:00",
  "isCompleted": false,
  "completedAt": null,
  "sentAt": "2026-11-24T09:00:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| reminderId | number | 이 알림을 만든 기한 알림 ID (`DEADLINE_DUE`만) |
| advanceDay | number | 기한 며칠 전 알림인지 (`DEADLINE_DUE`만, 0은 당일). N02 본문 셋째 줄 |
| readAt | string | 읽은 일시 (N02 "읽음 · 오늘 오전 9:12") |
| 나머지 | - | 알림 목록 조회의 content[]와 같음 |

**404 Not Found**

```json
{ "code": "NOTIFICATION_NOT_FOUND", "message": "알림을 찾을 수 없어요." }
```

---

## 3. 알림 상태 변경

`PATCH /notifications/{id}`

### 개요

- 알림을 읽음으로 바꾸거나 완료로 표시합니다.
- 화면: N02 진입(읽음), 푸시 알림 탭(읽음), N02 "납부 완료로 표시"

### 기술 설명/요구 사항

- 보낸 필드만 바꿉니다.
- `isCompleted`는 `DEADLINE_DUE` 알림만 바꿀 수 있습니다. true면 `isRead`도 true가 됩니다. false로 보내면 완료를 취소합니다.
- 완료는 기한의 그 회차(같은 `deadlineId`의 같은 날짜)에 기록합니다.
  - 그 회차에 남은 예약 알림은 보내지 않습니다. 예) 1일 전 알림에서 완료로 표시하면 당일 알림은 가지 않습니다.
  - 매월 반복 기한은 다음 달 회차부터 다시 알립니다.
  - 기한 목록 조회(캘린더)와 서류 상세 `deadlines[].isCompleted`에도 반영됩니다.
- 완료로 표시하면 N01 "완료" 탭에서 다시 볼 수 있습니다 (N02 안내 문구).
- `isRead=false`(안 읽음으로 되돌리기)는 지원하지 않습니다.

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
| id | number | O | 3001 |

**Body**

```json
{
  "isRead": true,
  "isCompleted": true
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| isRead | boolean | X | 읽음 처리 (true만 가능) |
| isCompleted | boolean | X | 완료 여부 (`DEADLINE_DUE`만) |

### Response

**200 OK**

```json
{
  "notificationId": 3001,
  "isRead": true,
  "readAt": "2026-11-24T09:12:00+09:00",
  "isCompleted": true,
  "completedAt": "2026-11-24T09:13:00+09:00",
  "unreadCount": 1
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| isRead / readAt | boolean / string | 읽음 여부·일시 |
| isCompleted / completedAt | boolean / string | 완료 여부·일시 |
| unreadCount | number | 바뀐 뒤 안 읽은 알림 수 (배지 갱신용) |

**400 Bad Request**

```json
{ "code": "NOT_COMPLETABLE", "message": "기한 알림만 완료로 표시할 수 있어요." }
```

**404 Not Found**

```json
{ "code": "NOTIFICATION_NOT_FOUND", "message": "알림을 찾을 수 없어요." }
```

---

## 4. 알림 모두 읽음

`PATCH /notifications/read-all`

### 개요

- 안 읽은 알림을 모두 읽음으로 바꿉니다.
- 화면: N01 "모두 읽음"

### 기술 설명/요구 사항

- 납부 완료 상태는 바꾸지 않습니다.
- 안 읽은 알림이 없어도 200을 줍니다 (`updatedCount=0`).
- 경로가 `/notifications/{id}`와 겹치지 않도록 `read-all`을 고정 경로로 먼저 매핑합니다.

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
  "updatedCount": 2,
  "unreadCount": 0
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| updatedCount | number | 읽음으로 바뀐 알림 수 |
| unreadCount | number | 바뀐 뒤 안 읽은 알림 수 (항상 0) |

---

## 5. 알림 설정 조회

`GET /notifications/settings`

### 개요

- 알림 설정을 조회합니다.
- 화면: S03 놓치지 않도록 알려 드려요 (납부일 알림, 마케팅 수신, 기본 알림 시각)

### 기술 설명/요구 사항

- 가입하면 기한 알림 켜짐, 기본 알림 시각 09:00으로 만듭니다.
- 서류 분석 완료 토글은 S03 디자인에서 빠졌습니다. 분석 완료·실패 알림은 설정 없이 항상 보냅니다.
- S03 "납부일 알림" 토글은 `deadlineAlertEnabled`입니다. 납부일뿐 아니라 갱신 통보 마감 같은 모든 기한 알림을 켜고 끕니다.
- 마케팅 수신은 약관 동의 상태를 그대로 보여 줍니다 (`marketingAgreed`). 바꿀 때는 약관 동의 기록 API(`POST /user/me/consents`)를 씁니다.
- 기기 알림 권한(S03 "기기 알림이 꺼져 있어요")은 서버가 알 수 없으므로 앱이 OS 권한을 직접 확인합니다.

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
  "deadlineAlertEnabled": true,
  "marketingAgreed": false,
  "defaultAlertTime": "09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| deadlineAlertEnabled | boolean | 기한 알림 (S03 "납부일 알림") |
| marketingAgreed | boolean | 마케팅 수신 동의 (읽기 전용) |
| defaultAlertTime | string | 기본 알림 시각 `HH:mm` |

---

## 6. 알림 설정 변경

`PATCH /notifications/settings`

### 개요

- 알림 종류별 켜기/끄기와 기본 알림 시각을 바꿉니다.
- 화면: S03 납부일 알림 토글, "기본 알림 시각"

### 기술 설명/요구 사항

- 보낸 필드만 바꿉니다. 토글을 누를 때마다 바로 호출합니다.
- `deadlineAlertEnabled=false`면 기한 알림 푸시를 보내지 않습니다. 등록한 기한 알림은 지우지 않고, 다시 켜면 다음 알림부터 보냅니다 (S03 "기기 권한을 바꾸어도 등록한 납부 일정은 사라지지 않아요").
- `defaultAlertTime`은 새 기한 알림을 등록할 때 알림 시간의 기본값입니다 (D02 초기값, 12 달력 아이콘으로 바로 등록할 때). 이미 등록한 알림 시각은 바뀌지 않습니다.
- 시각은 `HH:mm`, 10분 단위입니다.
- 마케팅 수신 토글은 이 API가 아니라 약관 동의 기록 API(`POST /user/me/consents`)를 부릅니다 (동의 이력을 남겨야 해서).

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
  "deadlineAlertEnabled": true,
  "defaultAlertTime": "09:00"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| deadlineAlertEnabled | boolean | X | 기한 알림 (S03 "납부일 알림") |
| defaultAlertTime | string | X | 기본 알림 시각 `HH:mm` (10분 단위) |

### Response

**200 OK**

알림 설정 조회와 같은 형식입니다.

```json
{
  "deadlineAlertEnabled": true,
  "marketingAgreed": false,
  "defaultAlertTime": "09:00"
}
```

**400 Bad Request**

```json
{ "code": "INVALID_ALERT_TIME", "message": "알림 시각은 10분 단위로 골라 주세요." }
```

---

## 7. 푸시 토큰 등록

`PUT /notifications/push-tokens/{deviceId}`

### 개요

- 이 기기의 FCM 푸시 토큰을 등록하거나 갱신합니다.
- 화면: 직접 호출하는 화면은 없습니다. 로그인 직후, 앱 시작 시, FCM 토큰이 바뀔 때, 기기 알림 권한이 바뀔 때 호출합니다.

### 기술 설명/요구 사항

- `deviceId`는 앱을 설치할 때 만든 UUID입니다. 같은 `deviceId`로 다시 보내면 덮어씁니다 (여러 번 불러도 같은 결과).
- 한 사용자가 여러 기기를 쓸 수 있고, 알림은 등록된 모든 기기로 보냅니다.
- 같은 FCM 토큰이 다른 사용자에게 등록돼 있으면 그쪽에서 지웁니다 (한 기기에서 계정을 바꿔 로그인한 경우).
- `permissionGranted=false`면 그 기기로는 푸시를 보내지 않습니다 (알림 목록에는 쌓임).
- 로그아웃 API가 이 기기의 토큰을 지웁니다. FCM이 토큰 만료(UNREGISTERED)를 알려 주면 서버가 지웁니다.

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
| deviceId | string | O | 8d1c3e0a-6b2f-4c1e-9a77-1f0c2b3d4e5f |

**Body**

```json
{
  "fcmToken": "fKq3xQ...:APA91bH...",
  "platform": "IOS",
  "permissionGranted": true,
  "appVersion": "1.0.0"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| fcmToken | string | O | FCM 등록 토큰 |
| platform | string | O | `IOS` / `ANDROID` |
| permissionGranted | boolean | O | 기기 알림 권한 허용 여부 |
| appVersion | string | X | 앱 버전 |

### Response

**204 No Content**

응답 본문 없음

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "fcmToken은 필수예요." }
```
