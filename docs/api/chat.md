# CHAT API 명세

> 원본: 노션 「API명세서」 > CHAT DB
>
> 공통 규칙(인증·형식·에러·페이지네이션)은 [00-common.md](00-common.md), 코드표(질문 칩 category)는 [02-enums.md](02-enums.md), AI 연동 규칙은 [01-ai-integration.md](01-ai-integration.md)를 참고합니다.

## 목록

| # | 이름 | Method | Path | 호출 주체 |
| --- | --- | --- | --- | --- |
| 1 | [대화 시작](#1-대화-시작) | POST | `/chats` | APP |
| 2 | [지난 대화 목록](#2-지난-대화-목록) | GET | `/chats` | APP |
| 3 | [대화 메시지 조회](#3-대화-메시지-조회) | GET | `/chats/{id}/messages` | APP, AI 서버 (X-Internal-Key) |
| 4 | [대화 서류 변경](#4-대화-서류-변경) | PUT | `/chats/{id}/documents` | APP |
| 5 | [질문 보내기](#5-질문-보내기) | POST | `/chats/{id}/messages` | APP |
| 6 | [서류 기반 AI 채팅 (AI 서버 API)](#6-서류-기반-ai-채팅-ai-서버-api) | POST | `/ai/chats/{id}/answer` | BE → AI 서버 (X-API-Key) |
| 7 | [자주 묻는 질문 칩 (AI 서버 API)](#7-자주-묻는-질문-칩-ai-서버-api) | GET | `/ai/chat/suggestions` | BE → AI 서버 (X-API-Key) |

---

## 1. 대화 시작

`POST /chats`

### 개요

- 새 AI 채팅을 만듭니다. 내 보관함의 서류나 박스를 함께 넣을 수 있습니다.
- 화면: AI 챗봇 화면 "새 채팅", 서류 화면에서 "물어보기"(H01)

### 기술 설명/요구 사항

- 서류(`documentIds`)와 박스(`boxIds`)를 넣을 수 있고, 둘 다 비워 두고 만든 뒤 나중에 넣어도 됩니다 (대화 서류 변경 API). 단, 질문하려면 서류가 하나 이상 있어야 합니다.
- 내 보관함에 있는 서류만 넣을 수 있습니다. 외부 파일은 먼저 보관함(박스)에 올린 뒤 넣습니다.
- 박스를 넣으면 그 박스의 서류 전체를 연결합니다. 나중에 그 박스에 추가된 서류도 자동으로 들어가고, 지운 서류는 빠집니다.
- 연결할 수 있는 서류는 박스를 펼친 수를 포함해 최대 20개입니다. 단, AI 채팅은 지금 서류 1–5개만 받으므로 AI 한도가 늘기 전까지는 5개로 막습니다 (AI 명세에 요청 필요).
- 분석 중(`ANALYZING`)인 서류는 `linkStatus=PENDING`으로 연결되고, 분석이 끝나면 자동으로 `LINKED`가 됩니다. 분석에 실패한 서류는 연결할 수 없습니다 (409).
- 질문 칩(`suggestions`)은 채팅창 위에 띄우며, 내 보관함 서류 종류를 기준으로 고릅니다.
  - BE가 내 서류의 `docType`을 많은 순으로 최대 3개 골라 AI 자주 묻는 질문 칩 API를 부릅니다.
  - AI 칩 API는 지금 `documentId` 하나만 받으므로 `docTypes`를 받도록 요청이 필요합니다. 그 전에는 가장 많은 종류의 최근 서류 `documentId`로 부릅니다. 서류가 없으면 `documentId` 없이 불러 공통 질문을 받습니다.
  - AI가 실패하면 빈 배열을 줍니다 (채팅 만들기는 실패시키지 않음).
- 대화 제목(`title`)은 첫 질문으로 자동으로 정해지며, 그 전에는 null입니다.
- `chatId`는 AI 채팅·최근 대화 조회에서 `conversationId`(문자열)로 씁니다.

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
  "documentIds": [501],
  "boxIds": [3]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| documentIds | number[] | X | 넣을 서류 (내 보관함 서류만) |
| boxIds | number[] | X | 통째로 넣을 박스 |

### Response

**201 Created**

```json
{
  "chatId": 12,
  "title": null,
  "boxes": [
    { "boxId": 3, "name": "세금", "documentCount": 2 }
  ],
  "documents": [
    { "documentId": 501, "title": "임대차계약서", "linkStatus": "LINKED", "viaBoxId": null },
    { "documentId": 570, "title": "종합소득세 중간예납 고지서", "linkStatus": "LINKED", "viaBoxId": 3 },
    { "documentId": 571, "title": "자동차세 고지서", "linkStatus": "LINKED", "viaBoxId": 3 }
  ],
  "greeting": "안녕하세요! 넣어 주신 서류에서 궁금한 내용을 쉽게 설명해 드릴게요.",
  "suggestions": [
    { "text": "보증금 언제 돌려받아?", "category": "AMOUNT" },
    { "text": "중간에 나가면 위약금 있어?", "category": "PENALTY" },
    { "text": "세금 언제까지 내야 해?", "category": "DEADLINE" }
  ],
  "createdAt": "2026-11-18T09:40:00+09:00"
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| chatId | number | 대화 ID |
| title | string | 대화 제목 (첫 질문 전에는 null) |
| boxes[] | array | 통째로 넣은 박스 |
| documents[] | array | 연결된 서류 전체 (박스를 펼친 것 포함) |
| documents[].linkStatus | string | `LINKED` / `PENDING`(분석 중) |
| documents[].viaBoxId | number | 박스로 들어온 서류면 그 박스 ID, 직접 넣었으면 null |
| greeting | string | 첫 안내 말풍선 |
| suggestions[] | array | 질문 칩 (AI 자주 묻는 질문 칩 응답: text, category) |

**400 Bad Request**

```json
{ "code": "TOO_MANY_DOCUMENTS", "message": "서류는 5개까지 넣을 수 있어요." }
```

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "보관함에서 서류를 찾을 수 없어요." }
```

```json
{ "code": "BOX_NOT_FOUND", "message": "박스를 찾을 수 없어요." }
```

**409 Conflict**

```json
{ "code": "DOCUMENT_ANALYSIS_FAILED", "message": "분석에 실패한 서류는 넣을 수 없어요." }
```

---

## 2. 지난 대화 목록

`GET /chats`

### 개요

- 지난 AI 대화 목록을 조회합니다.
- 화면: H03 지난 대화 (대화 내용 검색, 오늘/이전 대화)

### 기술 설명/요구 사항

- 마지막 메시지 시각(`lastMessageAt`) 최신순입니다. "오늘"·"이전 대화" 묶음은 앱이 `lastMessageAt`으로 나눕니다.
- `keyword`는 대화 제목과 메시지 내용에서 찾습니다 (H03 "대화 내용 검색").
- 질문을 하나도 하지 않은 대화(`title=null`)는 목록에서 뺍니다.
- 삭제된 서류는 `documents`에 `deleted=true`, `title="삭제된 서류"`로 줍니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | O | Bearer {accessToken} |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| keyword | string | X | - | 월세 |
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
      "chatId": 12,
      "title": "월세는 언제 내야 하나요?",
      "documents": [{ "documentId": 501, "title": "임대차계약서", "deleted": false }],
      "lastMessageAt": "2026-11-18T09:41:00+09:00"
    },
    {
      "chatId": 11,
      "title": "관리비가 왜 달라졌나요?",
      "documents": [{ "documentId": 540, "title": "11월 관리비 고지서", "deleted": false }],
      "lastMessageAt": "2026-11-18T09:20:00+09:00"
    },
    {
      "chatId": 8,
      "title": "약정 기간이 얼마나 남았나요?",
      "documents": [{ "documentId": 488, "title": "통신 서비스 계약서", "deleted": false }],
      "lastMessageAt": "2026-11-14T15:02:00+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 4,
  "hasNext": false
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| content[].chatId | number | 대화 ID |
| content[].title | string | 대화 제목 (첫 질문) |
| content[].documents[] | array | 연결된 서류 (H03 "임대차계약서 · 오전 9:41") |
| content[].documents[].deleted | boolean | 삭제된 서류인지 |
| content[].lastMessageAt | string | 마지막 메시지 일시 |

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "검색 조건을 확인해 주세요." }
```

---

## 3. 대화 메시지 조회

`GET /chats/{id}/messages`

### 개요

- 대화 하나의 메시지와 연결된 서류를 조회합니다.
- 화면: H03에서 지난 대화 열기 → H01
- AI 서버도 서류 기반 AI 채팅에서 이 API로 지난 대화를 가져갑니다 (X-Internal-Key).

### 기술 설명/요구 사항

- 최신 메시지부터 `size`개씩 줍니다. 위로 스크롤하면 받은 메시지 중 가장 오래된 `messageId`를 `beforeMessageId`로 보내 이전 메시지를 더 받습니다.
- `messages`는 오래된 순으로 정렬해 줍니다 (앱이 그대로 그림).
- AI 답변에는 근거(`citations`)가 들어 있습니다. H01 "근거: 계약서 1페이지 · 원본 확인 ›"은 `citations[].label`과 서류 원본 조회 API로 그립니다.
- H01 상단(연결된 서류, 첫 안내, 질문 칩)을 그릴 수 있게 `documents`·`greeting`·`suggestions`도 같이 줍니다. `suggestions`는 내 보관함 서류 종류를 기준으로 받은 값입니다 (대화 시작 참고). 화면을 열 때마다 다시 받습니다 (AI 쪽 캐시).
- `citations`·`answerType`·`disclaimer`·`followUpQuestions`는 AI 채팅 응답 값을 그대로 저장한 것입니다. `documentTitle`·`label`만 BE가 붙입니다.
- 다른 사용자의 대화를 조회하면 존재 여부를 숨기기 위해 404를 줍니다.
- **AI 서버 호출**:
  - 사용자 토큰 대신 `X-Internal-Key` 헤더와 `userId` 쿼리로 호출합니다. BE는 그 사용자의 대화인지 확인합니다.
  - AI는 `size=10`으로 최근 대화만 가져갑니다.
  - BE는 새 질문을 저장하기 **전에** AI 채팅을 호출합니다. 그래야 지금 질문이 지난 대화에 중복으로 들어가지 않습니다. (먼저 저장한다면 AI 호출 때 그 messageId를 `beforeMessageId`로 넘깁니다.)
  - `documents` 중 `linkStatus`가 `LINKED`인 서류만 AI가 서류 상세 조회로 원문을 가져갑니다.

> [BE ↔ AI 서버 연동](01-ai-integration.md)의 내부 API "최근 대화 조회"는 노션에 별도 페이지가 없고, 이 API의 "AI 서버 호출" 설명이 그 역할을 합니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Authorization | △ | Bearer {accessToken} (앱 호출) |
| X-Internal-Key | △ | AI 서버 전용 내부 키 (AI 서버 호출). Authorization과 둘 중 하나 필수 |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| beforeMessageId | number | X | - | 901 |
| size | number | X | 30 | 30 |
| userId | number | AI 호출 때만 O | - | 1024 |

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 12 |

**Body**: 없음

### Response

**200 OK**

```json
{
  "chatId": 12,
  "title": "월세는 언제 내야 하나요?",
  "boxes": [],
  "documents": [{ "documentId": 501, "title": "임대차계약서", "linkStatus": "LINKED", "viaBoxId": null }],
  "greeting": "안녕하세요! 이 서류에서 궁금한 내용을 쉽게 설명해 드릴게요.",
  "suggestions": [
    { "text": "보증금 언제 돌려받아?", "category": "AMOUNT" },
    { "text": "중간에 나가면 위약금 있어?", "category": "PENALTY" }
  ],
  "messages": [
    {
      "messageId": 901,
      "role": "USER",
      "content": "월세는 언제 내야 하나요?",
      "createdAt": "2026-11-18T09:41:00+09:00"
    },
    {
      "messageId": 902,
      "role": "ASSISTANT",
      "content": "매월 25일까지 500,000원을 내면 돼요.",
      "answerType": "ANSWERED",
      "citations": [
        {
          "sourceType": "DOCUMENT",
          "documentId": 501,
          "documentTitle": "임대차계약서",
          "page": 1,
          "bbox": [0.10, 0.48, 0.78, 0.05],
          "snippet": "차임은 매월 25일에 지급하기로 한다.",
          "score": 0.82,
          "label": "계약서 1페이지"
        }
      ],
      "disclaimer": null,
      "followUpQuestions": ["늦게 내면 어떻게 되나요?"],
      "createdAt": "2026-11-18T09:41:03+09:00"
    }
  ],
  "hasMore": false
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| suggestions[] | array | 질문 칩 (text, category: `AMOUNT` / `DEADLINE` / `PENALTY` / `PAYMENT` / `GENERAL`) |
| messages[].role | string | `USER` / `ASSISTANT` |
| messages[].answerType | string | `ANSWERED` / `NOT_IN_DOCUMENT` / `NEEDS_EXPERT` (AI 답변만) |
| messages[].citations[] | array | 근거 (AI 답변만) |
| citations[].sourceType | string | `DOCUMENT`(서류 원문) / `LAW`(법 조문) |
| citations[].documentTitle | string | 서류 이름 (BE가 붙임) |
| citations[].page / bbox | number / number[] | 원본 위치 (DOCUMENT일 때) |
| citations[].lawName / article / url | string | 법 이름·조문·링크 (LAW일 때) |
| citations[].label | string | 화면 표시용 근거 이름 (BE가 붙임) |
| messages[].disclaimer | string | 법 관련 답변일 때 안내 문구 (반드시 표시) |
| messages[].followUpQuestions | string[] | 이어서 물어볼 질문 |
| hasMore | boolean | 더 오래된 메시지가 있는지 |

**401 Unauthorized**

```json
{ "code": "UNAUTHORIZED", "message": "로그인이 필요해요." }
```

**404 Not Found**

없는 대화이거나 다른 사용자의 대화일 때

```json
{ "code": "CHAT_NOT_FOUND", "message": "대화를 찾을 수 없어요." }
```

---

## 4. 대화 서류 변경

`PUT /chats/{id}/documents`

### 개요

- 채팅에 넣은 서류·박스를 바꾸거나 추가합니다.
- 화면: 채팅 화면 "서류 넣기" (내 보관함에서 서류 또는 박스 고르기), H01 "연결된 서류 · 변경 ›"

### 기술 설명/요구 사항

- 넣을 서류·박스 전체 목록을 보냅니다 (보낸 목록으로 교체). 추가할 때는 기존 것과 새 것을 같이 보냅니다.
- 내 보관함의 서류·박스만 넣을 수 있습니다. 외부 파일은 넣을 수 없으며, 먼저 박스에 올린 뒤 넣습니다 (피그마 H02의 "사진 촬영·앨범·PDF 업로드"는 기획 흐름에서 빠짐).
- 박스를 넣으면 그 박스의 서류 전체가 연결되고, 나중에 박스에 추가·삭제된 서류도 따라갑니다.
- 연결 서류는 박스를 펼친 수를 포함해 최대 20개이며, AI 채팅 한도가 늘기 전까지는 5개입니다 (대화 시작 참고).
- 분석 중 서류는 `PENDING`으로 연결되고 분석이 끝나면 `LINKED`가 됩니다.
- 지난 메시지는 그대로 둡니다. 다음 질문부터 바뀐 서류를 AI 채팅의 `documentIds`로 보내며, AI의 최근 대화 조회 응답 `documentIds`도 바뀐 목록을 줍니다.
- 질문 칩은 내 보관함 서류 종류 기준이라 서류를 바꿔도 다시 받지 않습니다.

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
| id | number | O | 12 |

**Body**

```json
{
  "documentIds": [501],
  "boxIds": [3]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| documentIds | number[] | X | 넣을 서류 전체 (내 보관함 서류만) |
| boxIds | number[] | X | 통째로 넣을 박스 전체 |

### Response

**200 OK**

```json
{
  "chatId": 12,
  "boxes": [
    { "boxId": 3, "name": "세금", "documentCount": 2 }
  ],
  "documents": [
    { "documentId": 501, "title": "임대차계약서", "linkStatus": "LINKED", "viaBoxId": null },
    { "documentId": 570, "title": "종합소득세 중간예납 고지서", "linkStatus": "LINKED", "viaBoxId": 3 },
    { "documentId": 571, "title": "자동차세 고지서", "linkStatus": "PENDING", "viaBoxId": 3 }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| boxes[] | array | 통째로 넣은 박스 |
| documents[] | array | 연결된 서류 전체 (박스를 펼친 것 포함) |
| documents[].linkStatus | string | `LINKED` / `PENDING` |
| documents[].viaBoxId | number | 박스로 들어온 서류면 그 박스 ID |

**400 Bad Request**

```json
{ "code": "TOO_MANY_DOCUMENTS", "message": "서류는 5개까지 넣을 수 있어요." }
```

**404 Not Found**

```json
{ "code": "CHAT_NOT_FOUND", "message": "대화를 찾을 수 없어요." }
```

**409 Conflict**

```json
{ "code": "DOCUMENT_ANALYSIS_FAILED", "message": "분석에 실패한 서류는 넣을 수 없어요." }
```

---

## 5. 질문 보내기

`POST /chats/{id}/messages`

> 담당: BE

### 개요

- 대화에 질문을 보내고, 연결된 서류를 근거로 한 AI 답변을 받습니다.
- 화면: H01 입력창 전송, 질문 칩·이어지는 질문 탭

### 기술 설명/요구 사항

- BE 처리 순서
  1. 질문을 검사합니다. 1–500자, 사용자당 분당 10회까지입니다 (AI 서버 제한과 같음).
  2. AI 서류 기반 AI 채팅을 부릅니다. 보내는 값은 `userId`, `conversationId`(=chatId), `documentIds`(`LINKED`만), `question`, `stream`입니다. ID는 문자열로 보냅니다.
  3. AI가 필요한 데이터를 BE 내부 API로 직접 가져갑니다.
     - 최근 대화 → 최근 대화 조회
     - 서류 최신 값·원문(`fullText`)·법적 점검 → 문서 분석 결과 조회
     - 사용자 조건(나이·지역 등) → 사용자 정보 조회
     - 그래서 BE는 AI 채팅 Body의 `history`·`documentContext`·`userProfile`·`legalIssues`를 보내지 않습니다.
  4. 답변을 받으면 질문과 답변을 함께 저장하고 응답합니다. AI 응답 전에는 질문을 저장하지 않으므로 최근 대화 조회에 지금 질문은 들어가지 않습니다 (AI 명세와 같음). 실패하면 아무것도 저장하지 않으므로, 앱이 같은 질문을 다시 보내도 중복되지 않습니다.
- `stream=true`면 AI 서버의 SSE를 그대로 이어서 보냅니다 (`delta` → `citations` → `done`). `done` 이벤트에 BE가 저장한 메시지 ID를 붙입니다. 앱 연결이 끊겨도 BE는 답변을 끝까지 받아 저장합니다 (대화를 다시 열면 보임).
- 답변 종류(`answerType`): `ANSWERED` / `NOT_IN_DOCUMENT`(서류에 없는 내용) / `NEEDS_EXPERT`(전문가 상담 필요). 법 관련 답변에는 `disclaimer`가 붙으며 반드시 표시합니다.
- 근거(`citations`)는 AI 응답 그대로 주되, BE가 서류 이름(`documentTitle`)과 표시 문구(`label`, 예: "계약서 1페이지")를 붙이고 문서 ID를 숫자로 바꿉니다. 법 조문 근거(`sourceType=LAW`)는 lawName·article·snippet·url을 줍니다.
- 박스를 통째로 넣은 대화는 질문할 때마다 그 박스의 현재 서류를 펼쳐 `documentIds`로 보냅니다.
- 서류가 하나도 연결돼 있지 않으면 409 `NO_LINKED_DOCUMENT`입니다 (앱은 "보관함에서 서류를 넣어 주세요" 안내).
- 대화의 첫 질문이면 대화 제목으로 저장합니다 (40자까지 자름).
- 연결된 서류가 모두 `PENDING`이면 409입니다.
- AI 에러 처리 (타임아웃 30초)
  - 404 `INDEX_NOT_FOUND` → BE가 그 서류의 문서 분석을 다시 요청하고(사용자가 고친 값은 유지) 409 `DOCUMENT_NOT_INDEXED`
  - 429 → 429, 500 → 502, 시간 초과 → 504

> 참고: 이 페이지는 AI 채팅에 `conversationId`·`documentIds`(복수, 문자열 ID)·`stream`을 보낸다고 하지만, [6. 서류 기반 AI 채팅](#6-서류-기반-ai-채팅-ai-서버-api) 페이지의 Body는 `userId`·`documentId`(단수, number)·`question`이며 대화방 ID는 path로 받습니다. 원본 그대로 옮겼으니 AI 팀과 확정이 필요합니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| Content-Type | O | application/json |
| Authorization | O | Bearer {accessToken} |
| Accept | X | text/event-stream (stream=true일 때) |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| stream | boolean | X | false | true |

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 12 |

**Body**

```json
{
  "content": "월세는 언제 내야 하나요?"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| content | string | O | 질문 (1–500자) |

### Response

**201 Created**

```json
{
  "question": {
    "messageId": 901,
    "role": "USER",
    "content": "월세는 언제 내야 하나요?",
    "createdAt": "2026-11-18T09:41:00+09:00"
  },
  "answer": {
    "messageId": 902,
    "role": "ASSISTANT",
    "content": "매월 25일까지 500,000원을 내면 돼요.",
    "answerType": "ANSWERED",
    "citations": [
      {
        "sourceType": "DOCUMENT",
        "documentId": 501,
        "documentTitle": "임대차계약서",
        "page": 1,
        "bbox": [0.10, 0.48, 0.78, 0.05],
        "snippet": "차임은 매월 25일에 지급하기로 한다.",
        "score": 0.82,
        "label": "계약서 1페이지"
      }
    ],
    "disclaimer": null,
    "followUpQuestions": ["늦게 내면 어떻게 되나요?", "계약은 언제 끝나요?"],
    "createdAt": "2026-11-18T09:41:03+09:00"
  }
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| question | object | 저장된 질문 메시지 |
| answer | object | AI 답변 메시지 (answer·answerType·citations·disclaimer·followUpQuestions는 AI 채팅 응답 그대로) |

**stream=true일 때 (SSE)**

```javascript
event: delta
data: { "text": "매월 25일까지 " }

event: citations
data: { "citations": [ ... ] }

event: done
data: { "questionMessageId": 901, "answerMessageId": 902, "answerType": "ANSWERED", "followUpQuestions": [ ... ] }
```

**400 Bad Request**

```json
{ "code": "INVALID_QUESTION", "message": "질문은 500자까지 쓸 수 있어요." }
```

**404 Not Found**

```json
{ "code": "CHAT_NOT_FOUND", "message": "대화를 찾을 수 없어요." }
```

**409 Conflict**

```json
{ "code": "NO_LINKED_DOCUMENT", "message": "물어볼 서류가 없어요. 보관함에서 서류를 넣어 주세요." }
```

```json
{ "code": "DOCUMENT_NOT_INDEXED", "message": "서류를 다시 정리하고 있어요. 잠시 후 물어봐 주세요." }
```

**429 Too Many Requests**

```json
{ "code": "RATE_LIMITED", "message": "잠시 후 다시 물어봐 주세요." }
```

**502 Bad Gateway**

```json
{ "code": "AI_ERROR", "message": "답변을 만들지 못했어요. 다시 보내 주세요." }
```

**504 Gateway Timeout**

```json
{ "code": "AI_TIMEOUT", "message": "답변 시간이 오래 걸리고 있어요. 다시 보내 주세요." }
```

---

## 6. 서류 기반 AI 채팅 (AI 서버 API)

`POST /ai/chats/{id}/answer`

> **BE → AI 서버** 호출 API입니다 (X-API-Key). 앱에서는 호출하지 않습니다. 담당: BE, AI
>
> 노션 원본에서 이 경로는 빨간색으로 표시되어 있습니다. 또 본문의 흐름에는 AI 경로가 `POST /ai/chats/{id}/messages`로 적혀 있어 DB 속성의 경로(`/ai/chats/{id}/answer`)와 다릅니다. 확정이 필요합니다.

### 개요

- 채팅방에 연결된 서류 1개에 대해 질문하면 답해 주는 채팅입니다. (화면 H01)
- 예) "월세는 언제 내야 하나요?" → "매월 25일까지 500,000원을 내면 돼요." + 근거: 계약서 1페이지, 납부 조건
- 추천 질문 칩은 자주 묻는 질문 칩 API, 서류 추가(H02)는 문서 분석 API, 지난 대화 목록(H03)은 BE가 처리합니다.

### 기술 설명/요구 사항

- 흐름: APP → BE(`POST /chats/{id}/messages`) → AI 서버(`POST /ai/chats/{id}/messages`). `{id}`는 BE의 대화방 ID입니다(대화 시작 POST /chats로 먼저 생성).
- BE는 ID와 질문만 보내고, AI 서버가 BE 내부 API로 필요한 정보를 조회합니다.
  - 서류 분석 결과 + 원문(fullText): BE 서류 상세 조회 `GET /documents/{id}?userId=` (X-Internal-Key로 호출하면 fullText 포함)
  - 온보딩 프로필: BE 사용자 정보 조회 `GET /user/{userId}`
  - 최근 대화 10개: BE 대화 메시지 조회 `GET /chats/{id}/messages?userId=&size=10` (X-Internal-Key로 호출)
  - BE는 새 질문을 저장하기 전에 이 API를 호출합니다(지난 대화에 지금 질문이 중복되지 않게). 응답의 citations·answerType·disclaimer·followUpQuestions는 BE가 그대로 저장해 대화 메시지 조회로 앱에 줍니다.
- **답변 방법**: 서류 원문(fullText) 전체 + 분석 결과(fields, conditions) + 최근 대화를 외부 LLM API에 넣어 답을 만듭니다. AI 서버는 서류를 저장하지 않습니다.
- 금액·날짜는 사용자가 수정했을 수 있으므로 fields 값(userEdited 반영)을 원문보다 우선합니다.
- **답변 형식**: 2~3문장으로 짧게, 쉬운 말로. 핵심 금액·날짜는 `**굵게**` 표시(화면에서 볼드 처리)
- **근거**: 답에 쓴 서류 위치를 페이지 번호와 짧은 이름(예: 납부 조건)으로 줍니다. APP은 "근거: 계약서 1페이지, 납부 조건 · 원본 확인"으로 표시하고, 원본 확인을 누르면 그 페이지를 엽니다.
- **법 관련 질문**: 법령 DB에서 관련 조문을 검색(RAG)해 답에 반영하고, 근거에 조문을 추가합니다. 이때만 disclaimer를 붙입니다.
- 서류에 없는 내용은 지어내지 않고 answerType을 NOT_IN_DOCUMENT로 줍니다. 개별 법률 판단이 필요한 질문은 NEEDS_EXPERT로 주고 상담처를 안내합니다.
- 이어서 물어볼 질문(followUpQuestions)을 최대 2개, 20자 이하로 줍니다.
- 타임아웃 30초. 사용자당 분당 10회 제한.

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
| id | number | O | 12 |

**Body**

```json
{
  "userId": 1024,
  "documentId": 501,
  "question": "월세는 언제 내야 하나요?"
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| userId | number | O | 사용자 ID (BE 조회 시 소유자 확인·요청 제한용) |
| documentId | number | O | 채팅방에 연결된 서류 ID |
| question | string | O | 사용자 질문 (최대 500자) |

### Response

**200 OK**

```json
{
  "answer": "매월 **25일**까지 **500,000원**을 내면 돼요.",
  "answerType": "ANSWERED",
  "citations": [
    {
      "sourceType": "DOCUMENT",
      "documentId": 501,
      "page": 1,
      "bbox": [0.10, 0.48, 0.78, 0.05],
      "snippet": "차임은 매월 25일에 지급하기로 한다.",
      "score": 0.82
    }
  ],
  "disclaimer": null,
  "followUpQuestions": ["늦게 내면 어떻게 되나요?"]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| answer | string | 답변 (핵심 값은 \*\*굵게\*\*) |
| answerType | string | ANSWERED / NOT_IN_DOCUMENT / NEEDS_EXPERT |
| citations[] | array | 근거. sourceType DOCUMENT: documentId, page, bbox, snippet, score / LAW: lawName, article, url, snippet. documentTitle·label(화면 표시용 이름)은 BE가 붙임 |
| disclaimer | string \| null | 법 관련 답변일 때만 문구, 아니면 null |
| followUpQuestions | string[] | 이어서 물어볼 질문 (최대 2개) |

**400 Bad Request**

```json
{ "code": "INVALID_REQUEST", "message": "question은 500자 이하여야 합니다." }
```

**401 Unauthorized**

```json
{ "code": "INVALID_API_KEY", "message": "API 키가 올바르지 않습니다." }
```

**404 Not Found**

```json
{ "code": "DOCUMENT_NOT_FOUND", "message": "BE에서 서류를 찾을 수 없습니다." }
```

**429 Too Many Requests**

```json
{ "code": "RATE_LIMITED", "message": "잠시 후 다시 시도해 주세요." }
```

**500 Internal Server Error**

```json
{ "code": "AI_GENERATION_FAILED", "message": "답변 생성 중 오류가 발생했습니다." }
```

**502 Bad Gateway**

```json
{ "code": "BE_LOOKUP_FAILED", "message": "서류·대화 정보를 불러오지 못했습니다." }
```

---

## 7. 자주 묻는 질문 칩 (AI 서버 API)

`GET /ai/chat/suggestions`

> **BE → AI 서버** 호출 API입니다 (X-API-Key). 앱에서는 호출하지 않습니다. 담당: BE, AI
>
> 참고: DB 속성의 경로는 `/ai/chat/suggestions`(path 변수 없음)인데, 본문 흐름에는 `GET /ai/chats/{id}/suggested-questions`로 적혀 있고 Path Variable `id`도 있습니다. 또 [1. 대화 시작](#1-대화-시작)에서는 "서류가 없으면 `documentId` 없이 불러 공통 질문을 받는다"고 했지만 이 페이지에서 `documentId`는 필수입니다. 원본 그대로 옮겼으니 확정이 필요합니다.

### 개요

- 채팅 화면(H01)에 띄울 추천 질문 칩을 만들어 줍니다.
- 예) 임대차계약서 → "언제까지 내야 하나요?", "위약금이 있나요?"

### 기술 설명/요구 사항

- 흐름: APP → BE → AI 서버(`GET /ai/chats/{id}/suggested-questions`). `{id}`는 BE의 대화방 ID입니다.
- 대화방에 연결된 서류 ID를 documentId 쿼리로 받고, AI 서버가 BE 서류 상세 조회 `GET /documents/{id}?userId=`(X-Internal-Key)로 문서 종류·항목을 조회합니다.
- 문서 종류별 기본 질문 템플릿 + 문서 내용에 맞춘 질문을 섞어서 줍니다. (예: 위약금 조항이 있으면 위약금 질문 추가)
- 칩에 들어갈 수 있게 질문은 20자 이하로 만듭니다.
- 결과는 documentId 기준으로 캐시합니다(대화방이 달라도 같은 서류면 같은 칩). 대화 서류를 바꾸면(PUT /chats/{id}/documents) BE가 다시 호출합니다. 타임아웃 10초.
  - > 참고: [4. 대화 서류 변경](#4-대화-서류-변경)에는 "질문 칩은 내 보관함 서류 종류 기준이라 서류를 바꿔도 다시 받지 않습니다"라고 되어 있어 서로 다릅니다.

### Request

**Header**

| 이름 | 요구 | 형식 |
| --- | --- | --- |
| X-API-Key | O | AI 서버 내부 키 (BE만 보유) |

**Query Parameter**

| 이름 | 타입 | 필수 | 기본값 | 예시 |
| --- | --- | --- | --- | --- |
| userId | number | O | - | 1024 |
| documentId | number | O | - | 501 |
| count | number | X | 2 | 4 |

**Path Variable**

| 이름 | 타입 | 필수 | 예시 |
| --- | --- | --- | --- |
| id | number | O | 12 |

**Body**: 없음 (원본 예시는 빈 객체 `{ }`)

### Response

**200 OK**

```json
{
  "chatId": 12,
  "documentId": 501,
  "suggestions": [
    { "text": "언제까지 내야 하나요?", "category": "DEADLINE" },
    { "text": "위약금이 있나요?", "category": "PENALTY" }
  ]
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| suggestions[].text | string | 칩에 표시할 질문 (20자 이하). 누르면 이 문장으로 질문 보내기 |
| suggestions[].category | string | AMOUNT / DEADLINE / PENALTY / PAYMENT / GENERAL |

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
{ "code": "DOCUMENT_NOT_FOUND", "message": "BE에서 서류를 찾을 수 없습니다." }
```

**500 Internal Server Error**

```json
{ "code": "AI_GENERATION_FAILED", "message": "추천 질문 생성 중 오류가 발생했습니다." }
```
