# 코드표 (Enum)

> 원본: 노션 「API명세서」 > 코드표 (Enum)

## 서류 종류 docType

AI 문서 분석이 주는 값입니다.

| 값 | 이름 |
| --- | --- |
| `LEASE_CONTRACT` | 임대차계약서 |
| `EMPLOYMENT_CONTRACT` | 근로계약서 |
| `SERVICE_CONTRACT` | 용역·서비스 계약 (통신, 보험 등) |
| `BILL` | 고지서·청구서 |
| `NOTICE` | 공지·안내문 |
| `ADVERTISEMENT` | 광고·홍보물 |
| `ETC` | 기타 |

## 서류 분류 docCategory

AI 문서 분석이 주는 대분류입니다. 계약서와 고지서처럼 꾸민 광고를 구분합니다.

| 값 | 이름 | 쓰는 곳 |
| --- | --- | --- |
| `CONTRACT` | 계약서 | M01 "계약서", 비교 기준 서류 |
| `BILL` | 고지서 | M01 "고지서", 고지서와 비교 |
| `AD` | 광고 | M01 "기타", 광고와 비교 |
| `INFO` | 안내 | M01 "기타" |

## 항목 key

AI 문서 분석이 정한 표준 key를 그대로 씁니다. 예) 보증금·임대보증금 → `deposit`, 월세·차임 → `monthlyRent`, 납기일·납부기한 → `paymentDueDate`. 전체 목록은 AI 명세에서 관리하므로 앱은 key 대신 `label`을 표시합니다.

서류 종류별 주요 항목 (AI 쉬운말 요약 카드 기준)

| docType | 주요 항목 |
| --- | --- |
| `LEASE_CONTRACT` | 임대인, 임차인, 소재지, 보증금, 월세, 계약기간 |
| `EMPLOYMENT_CONTRACT` | 근무처, 근무기간, 근무시간, 시급·월급, 급여일 |
| `SERVICE_CONTRACT` | 계약 상대, 서비스, 금액, 계약기간, 해지 조건 |
| `BILL` | 청구처, 청구금액, 납부기한, 납부방법 |
| `NOTICE` | 대상, 일시, 장소, 준비물 |

## 항목 값 형식 valueType

| 값 | 형식 | 예시 |
| --- | --- | --- |
| `AMOUNT` | 원 단위 정수 | `500000` |
| `DATE` | `YYYY-MM-DD` | `"2027-11-24"` |
| `TEXT` | 문자열 (200자 이하) | `"잔여 기간 월세의 10%"` |

매월 납부일은 항목이 아니라 `paymentSchedules[].dayOfMonth`(1–31)입니다. AI가 다른 valueType을 쓰게 되면 여기에 더합니다.

## 박스 color · icon

박스는 사용자가 만듭니다 (05 새 박스 만들기). 가입하면 기본 박스 5개를 만들어 둡니다.

| 이름 | 값 |
| --- | --- |
| color | `PEACH` 주황 / `GREEN` 초록 / `GRAY` 회색 |
| icon | `HOME` 집, `CAR` 자동차, `RECEIPT` 세금·영수증, `HEART` 병원·건강, `WIFI` 통신, `BRIEFCASE` 일·회사, `PET` 반려동물, `BOOK` 학교·공부, `CART` 쇼핑, `CARD` 금융, `GIFT` 경조사, `ETC` 기타 (05의 아이콘 12개. 키 이름은 디자인 에셋에 맞춰 확정) |

| 기본 박스 | color | icon |
| --- | --- | --- |
| 집 · 임대 | `PEACH` | `HOME` |
| 자동차 | `GREEN` | `CAR` |
| 세금 | `GRAY` | `RECEIPT` |
| 병원 · 보험 | `GREEN` | `HEART` |
| 통신 · 구독 | `PEACH` | `WIFI` |

## 서류 상태 status

| 값 | 설명 |
| --- | --- |
| `ANALYZING` | 분석 중 |
| `REVIEW_REQUIRED` | 분석 끝, 사용자 확인 전 (U04) |
| `CONFIRMED` | 확인하고 보관함 |
| `FAILED` | 분석 실패 (`failReason` 참고). 목록에 안 보이고 24시간 뒤 삭제 |

## 분석 실패 이유 failReason

| 값 | 설명 |
| --- | --- |
| `TEXT_NOT_RECOGNIZED` | AI가 글자를 인식하지 못함 (AI 422) → U03 다시 찍기 |
| `ANALYSIS_ERROR` | AI 서버 오류·시간 초과 (한 번 더 시도한 뒤) |

## 분석 단계 analysisStep

16 AI 처리 중 화면의 체크리스트입니다.

| 값 | 화면 문구 | 누가 정하나 |
| --- | --- | --- |
| `ENHANCING` | 이미지 선명하게 보정 | BE (이미지 줄이기·PDF 합치기) |
| `READING_TEXT` | 글자 인식 | BE (AI 문서 분석 호출 중) |
| `CLASSIFYING` | 어떤 서류인지 파악 | AI가 진행 상황을 알려 줄 때만. 그 전에는 앱이 시간에 맞춰 체크 |
| `EXTRACTING` | 비용·기한·중요도 추출 | 위와 같음 |
| `DONE` | 끝 (성공·실패 모두) | BE |

## 중요도 priority.level

AI 문서 분석이 주는 값입니다.

| 값 | 표시 | 기준 (AI) |
| --- | --- | --- |
| `HIGH` | 높음 | 돈이 나가거나 기한을 놓치면 불이익 |
| `MEDIUM` | 보통 | - |
| `LOW` | 낮음 | 참고·광고 (광고는 항상 LOW) |

## 기한 종류 deadline type

BE가 AI 문서 분석의 `calendarEvents`와 `priority.keyDeadline`으로 만듭니다. 캘린더·기한 알림·D-day가 모두 이 기한을 씁니다. 사용자가 캘린더에서 직접 추가·수정·삭제할 수 있고, 기한이 생기면 알림을 자동으로 예약합니다. 기본 간격: 매월 납부 하루 전·당일, 한 번뿐인 납부 일주일 전·하루 전·당일, 만료·행동 한 달 전·일주일 전·하루 전 (D02 칩: 당일·1일·3일·7일 전).

| 값 | 설명 | 예시 |
| --- | --- | --- |
| `PAYMENT` | 납부 (`paymentSchedules`, `paymentDueDate`). 매월 반복 가능 | 월세 납부, 관리비 납부 |
| `EXPIRY` | 만료 (`keyDeadline.date`, `contractEndDate` 등) | 계약 만료, 약정 만료 |
| `ACTION` | 그 전에 해야 할 행동 (`keyDeadline.actionDate`) | 갱신 통보 마감, 보험금 청구 마감 |
| `ETC` | 그 밖의 `calendarEvents` | - |

## 비교 결과 status

AI 서류 비교가 주는 값입니다: `MISMATCH` 약속과 다름 / `MATCH` 일치 / `NEEDS_CHECK` 비교 항목이 불분명

## 기한 출처 source

| 값 | 설명 |
| --- | --- |
| `AI` | AI 문서 분석에서 뽑은 기한 (사용자가 고치면 `userEdited=true`) |
| `USER` | 사용자가 캘린더에서 추가한 기한 |

## 사용자 관련 여부 userRelevance.level

AI 정리본의 "내 상황과 관련 있는지"입니다 (AI 명세에 추가 요청 필요): `RELEVANT` 관련 있음 / `PARTIAL` 일부 관련 / `NOT_RELEVANT` 관련 없음

## 프로필

AI 사용자 정보 조회와 같은 값입니다.

| 이름 | 값 |
| --- | --- |
| nationality | `KR` / `FOREIGN` |
| housingType | `MONTHLY_RENT` / `JEONSE` / `OWNED` / `DORMITORY` / `WITH_PARENTS` |
| housingType 화면 | 월세 / 전세 / 자가 / 기숙사·고시원 / 부모님 집 (O03) |
| welfareType | `BASIC_LIVELIHOOD` 기초생활수급 / `NEAR_POVERTY` 차상위계층 / `OTHER` 기타 / `NONE` 해당 없음 |

## 기타

| 이름 | 값 |
| --- | --- |
| fontSize | `NORMAL` 보통(기본) / `LARGE` 크게 / `EXTRA_LARGE` 아주 크게 (S02 글자 크기) |
| loginType | `PHONE` / `KAKAO` / `GOOGLE` |
| 인증 purpose | `SIGNUP` / `PASSWORD_RESET` |
| 알림 type | `DEADLINE_DUE` 기한 / `ANALYSIS_COMPLETED` 분석 완료 / `ANALYSIS_FAILED` 분석 실패 / `COMPARISON_COMPLETED` 비교 완료 (목록에만, 푸시 없음) |
| 질문 칩 category | `AMOUNT` / `DEADLINE` / `PENALTY` / `PAYMENT` / `GENERAL` (AI) |
| 문자 tone | `POLITE` 정중 / `FRIENDLY` 친근 / `FORMAL` 공문체 (AI) |
