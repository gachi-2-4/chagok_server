# BOX API 명세

> 원본: 노션 「API명세서」 > DOCUMENT DB 중 박스 관련 API (`/boxes`)
>
> 노션에서는 DOCUMENT DB에 함께 들어 있지만, 서버 패키지 구조(`domain/box`)에 맞춰 문서를 분리했습니다.
> 공통 규칙(인증·형식·에러·페이지네이션)은 [00-common.md](00-common.md), 코드표(박스 color·icon, 기본 박스)는 [02-enums.md](02-enums.md#박스-color--icon)를 참고합니다.

## 목록

| # | 이름 | Method | Path |
| --- | --- | --- | --- |
| 1 | [박스 목록 조회](#1-박스-목록-조회) | GET | `/boxes` |
| 2 | [박스 만들기](#2-박스-만들기) | POST | `/boxes` |
| 3 | [박스 상세 조회](#3-박스-상세-조회) | GET | `/boxes/{id}` |
| 4 | [박스 수정](#4-박스-수정) | PATCH | `/boxes/{id}` |
| 5 | [박스 삭제](#5-박스-삭제) | DELETE | `/boxes/{id}` |

---

## 1. 박스 목록 조회

`GET /boxes`

### 개요

- 내 박스 목록을 조회합니다.
- 화면: 보관함 (박스 목록·박스 생성 칸), 촬영·업로드 때 박스 선택, 서류 박스 옮기기

### 기술 설명/요구 사항

- 박스는 사용자가 만드는 서류 묶음입니다 (이름·색·아이콘).
- 가입하면 기본 박스 5개를 만들어 둡니다: 집 · 임대, 자동차, 세금, 병원 · 보험, 통신 · 구독.
- 기본은 만든 순서입니다. `sort=RECENT_OPENED`면 최근에 연 순서입니다 (열지 않은 박스는 뒤). 메인 화면의 "최근 열어본 박스"는 메인 화면 조회 API가 따로 줍니다.
- `documentCount`는 분석 실패(`FAILED`)를 뺀 서류 수입니다.
- `nearestDeadline`은 박스 안 서류의 완료하지 않은 기한 중 가장 가까운 것입니다 (박스 배지 "D-13"). 없으면 null.
- `lastOpenedAt`은 박스 상세 조회를 마지막으로 부른 일시입니다.
- 박스는 최대 30개라서 페이지 나누기 없이 한 번에 줍니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| sort | string | X | CREATED | `CREATED` / `RECENT_OPENED` |

**Path Variable**: 없음

**Body**: 없음

### Response

**200 OK**

```json
{
  "content": [
    { "boxId": 1, "name": "집 · 임대", "color": "PEACH", "icon": "HOME", "documentCount": 3, "nearestDeadline": { "date": "2026-11-25", "dDay": 13 }, "lastOpenedAt": "2026-11-12T09:10:00+09:00" },
    { "boxId": 2, "name": "자동차", "color": "GREEN", "icon": "CAR", "documentCount": 2, "nearestDeadline": null, "lastOpenedAt": null },
    { "boxId": 3, "name": "세금", "color": "GRAY", "icon": "RECEIPT", "documentCount": 2, "nearestDeadline": { "date": "2026-11-30", "dDay": 18 }, "lastOpenedAt": "2026-11-10T20:00:00+09:00" },
    { "boxId": 4, "name": "병원 · 보험", "color": "GREEN", "icon": "HEART", "documentCount": 2, "nearestDeadline": { "date": "2026-11-15", "dDay": 3 }, "lastOpenedAt": null },
    { "boxId": 5, "name": "통신 · 구독", "color": "PEACH", "icon": "WIFI", "documentCount": 1, "nearestDeadline": null, "lastOpenedAt": null }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].boxId | number | 박스 ID |
| content[].name | string | 박스 이름 |
| content[].color | string | `PEACH` / `GREEN` / `GRAY` |
| content[].icon | string | 아이콘 키 (코드표 참고) |
| content[].documentCount | number | 서류 수 |
| content[].nearestDeadline | object | 가장 가까운 기한 (date, dDay). 없으면 null |
| content[].lastOpenedAt | string | 마지막으로 연 일시 (없으면 null) |

---

## 2. 박스 만들기

`POST /boxes`

### 개요

- 새 박스를 만듭니다.
- 화면: 05 새 박스 만들기 "만들기"

### 기술 설명/요구 사항

- `name`은 앞뒤 공백을 지운 뒤 1–20자입니다. 내 박스 중 같은 이름이 있으면 409.
- `color`는 05의 색 3개 중 하나, `icon`은 05의 아이콘 12개 중 하나입니다 (코드표 참고).
- 박스는 최대 30개까지 만들 수 있습니다.

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
  "name": "반려동물",
  "color": "GREEN",
  "icon": "PET"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| name | string | O | 박스 이름 (1–20자) |
| color | string | O | `PEACH` / `GREEN` / `GRAY` |
| icon | string | O | 아이콘 키 |

### Response

**201 Created**

```json
{
  "boxId": 6,
  "name": "반려동물",
  "color": "GREEN",
  "icon": "PET",
  "documentCount": 0,
  "nearestDeadline": null,
  "createdAt": "2026-11-12T14:00:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| boxId | number | 만든 박스 ID |
| 나머지 | - | 박스 목록 조회의 content[]와 같음 |

**400 Bad Request**

```json
{ "code": "INVALID_BOX_NAME", "message": "박스 이름은 1자에서 20자 사이로 지어 주세요." }
```

**409 Conflict**

```json
{ "code": "BOX_NAME_DUPLICATED", "message": "같은 이름의 박스가 있어요." }
```

```json
{ "code": "BOX_LIMIT_EXCEEDED", "message": "박스는 30개까지 만들 수 있어요." }
```

---

## 3. 박스 상세 조회

`GET /boxes/{id}`

### 개요

- 박스 하나의 정보를 조회하고, 연 기록을 남깁니다.
- 화면: 보관함에서 박스 누르기, 메인 화면 "최근 열어본 박스" 누르기 (06 박스 상세 상단)

### 기술 설명/요구 사항

- 부를 때마다 `lastOpenedAt`을 지금으로 바꿉니다. 메인 화면의 "최근 열어본 박스"가 이 값을 씁니다.
- 박스 안 서류 목록은 서류 조회(`GET /documents?boxId=`)로 받습니다. 박스 안에서 새 파일 추가는 서류 업로드(`POST /documents`, 이 boxId)입니다.
- 박스 삭제 확인 창의 파일 수는 `documentCount`를 씁니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 1 |

**Body**: 없음

### Response

**200 OK**

```json
{
  "boxId": 1,
  "name": "집 · 임대",
  "color": "PEACH",
  "icon": "HOME",
  "documentCount": 3,
  "nearestDeadline": { "date": "2026-11-25", "dDay": 13 },
  "lastOpenedAt": "2026-11-12T09:10:00+09:00",
  "createdAt": "2026-11-01T10:21:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| lastOpenedAt | string | 방금 연 일시 |
| 나머지 | - | 박스 목록 조회의 content[]와 같음 |

**404 Not Found**

```json
{ "code": "BOX_NOT_FOUND", "message": "박스를 찾을 수 없어요." }
```

---

## 4. 박스 수정

`PATCH /boxes/{id}`

### 개요

- 박스 이름·색·아이콘을 바꿉니다.
- 화면: 아직 없음. 박스를 길게 눌러 05 시트를 다시 여는 방식을 제안합니다.

### 기술 설명/요구 사항

- 보낸 필드만 바꿉니다. 값 규칙은 박스 만들기와 같습니다.
- 박스 안 서류는 그대로입니다.

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
| id | number | O | 6 |

**Body**

```json
{
  "name": "우리 강아지",
  "color": "PEACH"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| name | string | X | 박스 이름 (1–20자) |
| color | string | X | `PEACH` / `GREEN` / `GRAY` |
| icon | string | X | 아이콘 키 |

### Response

**200 OK**

박스 만들기 응답과 같은 형식입니다.

```json
{
  "boxId": 6,
  "name": "우리 강아지",
  "color": "PEACH",
  "icon": "PET",
  "documentCount": 0,
  "nearestDeadline": null,
  "createdAt": "2026-11-12T14:00:00+09:00"
}
```

**400 Bad Request**

```json
{ "code": "INVALID_BOX_NAME", "message": "박스 이름은 1자에서 20자 사이로 지어 주세요." }
```

**404 Not Found**

```json
{ "code": "BOX_NOT_FOUND", "message": "박스를 찾을 수 없어요." }
```

**409 Conflict**

```json
{ "code": "BOX_NAME_DUPLICATED", "message": "같은 이름의 박스가 있어요." }
```

---

## 5. 박스 삭제

`DELETE /boxes/{id}`

### 개요

- 박스와 박스 안의 모든 파일을 함께 삭제합니다. 되돌릴 수 없습니다.
- 화면: 보관함 → 박스 메뉴 "박스 삭제" (앱이 "안에 있는 파일 N개도 함께 삭제돼요. 되돌릴 수 없어요." 확인 창을 띄운 뒤 호출)

### 기술 설명/요구 사항

- 박스 안 서류를 모두 서류 삭제와 같은 방식으로 지웁니다.
  - 원본 파일, 페이지 이미지, 분석 결과
  - 기한, 기한 알림과 예약된 푸시
  - 그 서류를 쓴 비교 결과
  - AI 서버 벡터 인덱스 (AI 서버에 삭제 요청)
  - 대화에서는 연결만 끊습니다 (지난 대화 목록에 "삭제된 서류"로 보임). 대화에 박스를 통째로 넣었다면 그 박스 연결도 끊습니다.
- DB에서는 바로 지우고, 스토리지 파일과 AI 인덱스는 비동기로 지웁니다.
- 확인 창에 보여 줄 파일 수는 박스 상세 조회의 `documentCount`를 씁니다.
- 마지막 박스도 지울 수 있습니다. 박스가 하나도 없으면 앱은 업로드 전에 박스 만들기를 먼저 보여 줍니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**: 없음

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 6 |

**Body**: 없음

### Response

**200 OK**

```json
{
  "boxId": 6,
  "deletedDocumentCount": 3
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| boxId | number | 삭제한 박스 ID |
| deletedDocumentCount | number | 함께 삭제한 서류 수 |

**404 Not Found**

```json
{ "code": "BOX_NOT_FOUND", "message": "박스를 찾을 수 없어요." }
```
