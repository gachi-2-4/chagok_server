# HOME API 명세

> 원본: 노션 「API명세서」 > NOTIFICATION DB 중 메인 화면 API (`/home`)
>
> 노션에서는 NOTIFICATION DB에 들어 있지만, 기한·박스·알림을 모아 주는 화면 전용 API라서 문서를 분리했습니다.
> 공통 규칙은 [00-common.md](00-common.md), 관련 API는 [deadline.md](deadline.md)(기한 목록 조회), [box.md](box.md)(박스 상세 조회), [notification.md](notification.md)(알림 목록 조회)를 참고합니다.

## 목록

| # | 이름 | Method | Path |
| --- | --- | --- | --- |
| 1 | [메인 화면 조회](#1-메인-화면-조회) | GET | `/home` |

---

## 1. 메인 화면 조회

`GET /home`

### 개요

- 메인 화면에 필요한 값을 한 번에 줍니다: 이번 주 미니 캘린더와 가장 가까운 기한, 최근 연 보관함, 최근 알림.
- 화면: M00 홈 (내비게이션 바 가운데)

### 기술 설명/요구 사항

- `weekCalendar`: 오늘이 들어 있는 주의 일요일–토요일 7일입니다 (M00 "15–21", 오늘 18일 강조). 날짜별 기한이며, 기한이 없는 날도 빈 배열로 줍니다. 값은 기한 목록 조회와 같은 기준입니다 (완료한 기한 포함, `isCompleted`로 표시). 기한이 있는 날은 앱이 점을 찍습니다.
- 캘린더 머리의 "2026년 11월 ›"은 `today`로 그리고, 누르면 캘린더(기한 목록 조회)로 갑니다.
- `nextDeadline`: 오늘 이후(오늘 포함) 완료하지 않은 기한 중 가장 가까운 것 하나입니다 (M00 "D-2 통신 서비스 요금 납부일 · 11.20"). 이번 주 밖이어도 줍니다. 없으면 null이고 앱은 그 줄을 숨깁니다.
- `recentBoxes`: 최근에 연 박스 최대 4개 (박스 상세 조회의 `lastOpenedAt` 순). 연 박스가 없으면 빈 배열입니다.
  - "서류 3건 · 오늘 열람"은 `documentCount`와 `lastOpenedAt`(앱이 오늘/어제/날짜로 표시)으로 그립니다.
  - 박스 그림의 "D-13" 배지는 `nearestDeadline`입니다. 박스 안 서류의 완료하지 않은 기한 중 가장 가까운 것이며, 없으면 null이고 배지를 숨깁니다.
- `recentNotifications`: 최근 알림 최대 3개입니다. 알림 목록 조회의 content[]에서 `notificationId`·`type`·`title`·`document`·`isRead`·`sentAt`만 줍니다. 두 번째 줄 "집 · 임대 · 방금"은 `document.boxName`과 `sentAt`(앱이 방금/N시간 전/어제로 표시)입니다. 안 읽은 알림은 주황 점, 읽은 알림은 회색 점입니다.
- 누르면 이동: 미니 캘린더 → 캘린더, 박스 → 박스 상세, 알림 → N02 알림 상세(비교 완료 알림은 C02 비교 결과), "전체 보기" → 보관함 / N01 알림.
- 오른쪽 위 알림 버튼 배지는 `unreadCount`를 씁니다.

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
  "today": "2026-11-18",
  "weekCalendar": [
    { "date": "2026-11-15", "deadlines": [] },
    { "date": "2026-11-16", "deadlines": [] },
    {
      "date": "2026-11-17",
      "deadlines": [
        { "deadlineId": 80, "type": "ACTION", "label": "보험금 청구 마감", "documentId": 560, "documentTitle": "실손보험 청구 안내", "dDay": -1, "isCompleted": true }
      ]
    },
    { "date": "2026-11-18", "deadlines": [] },
    { "date": "2026-11-19", "deadlines": [] },
    {
      "date": "2026-11-20",
      "deadlines": [
        { "deadlineId": 75, "type": "PAYMENT", "label": "통신 서비스 요금 납부일", "documentId": 533, "documentTitle": "11월 통신요금 고지서", "dDay": 2, "isCompleted": false }
      ]
    },
    { "date": "2026-11-21", "deadlines": [] }
  ],
  "nextDeadline": {
    "deadlineId": 75,
    "type": "PAYMENT",
    "label": "통신 서비스 요금 납부일",
    "date": "2026-11-20",
    "dDay": 2,
    "documentId": 533,
    "documentTitle": "11월 통신요금 고지서"
  },
  "recentBoxes": [
    {
      "boxId": 1, "name": "집 · 임대", "color": "PEACH", "icon": "HOME", "documentCount": 3,
      "lastOpenedAt": "2026-11-18T09:10:00+09:00",
      "nearestDeadline": { "deadlineId": 70, "label": "월세 납부", "date": "2026-12-01", "dDay": 13 }
    },
    {
      "boxId": 5, "name": "통신 · 구독", "color": "PEACH", "icon": "WIFI", "documentCount": 1,
      "lastOpenedAt": "2026-11-17T20:00:00+09:00",
      "nearestDeadline": null
    }
  ],
  "recentNotifications": [
    {
      "notificationId": 3010, "type": "DEADLINE_DUE", "title": "임대차계약서 만기가 30일 남았어요",
      "document": { "documentId": 501, "title": "임대차계약서", "boxName": "집 · 임대" },
      "isRead": false, "sentAt": "2026-11-18T09:00:00+09:00"
    },
    {
      "notificationId": 3008, "type": "DEADLINE_DUE", "title": "통신 서비스 요금이 이번 주에 납부돼요",
      "document": { "documentId": 533, "title": "11월 통신요금 고지서", "boxName": "통신 · 구독" },
      "isRead": false, "sentAt": "2026-11-18T07:00:00+09:00"
    },
    {
      "notificationId": 2995, "type": "COMPARISON_COMPLETED", "title": "자동차 보험 계약서 비교가 완료됐어요",
      "document": { "documentId": 610, "title": "자동차 보험 계약서", "boxName": "자동차" },
      "isRead": true, "sentAt": "2026-11-17T15:20:00+09:00"
    }
  ],
  "unreadCount": 2
}
```

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| today | string | 기준 날짜 (KST) |
| weekCalendar[] | array | 오늘이 든 주 일–토 7일 (date, deadlines[]) |
| weekCalendar[].deadlines[] | array | 그날 기한 (deadlineId, type, label, documentId, documentTitle, dDay, isCompleted) |
| nextDeadline | object | 가장 가까운 미완료 기한 (deadlineId, type, label, date, dDay, documentId, documentTitle). 없으면 null |
| recentBoxes[] | array | 최근 연 박스 (최대 4개) |
| recentBoxes[].nearestDeadline | object | 박스 안 가장 가까운 미완료 기한 (deadlineId, label, date, dDay). 없으면 null |
| recentNotifications[] | array | 최근 알림 (최대 3개: notificationId, type, title, document, isRead, sentAt) |
| unreadCount | number | 안 읽은 알림 수 (오른쪽 위 알림 버튼 배지) |

> 노션 원본에는 이 API의 에러 응답이 적혀 있지 않습니다 (공통 에러 응답 401 등은 [00-common.md](00-common.md#에러-응답) 참고).
