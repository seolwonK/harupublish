# 기획안 대비 구현 현황

이 문서는 `C:\Users\A\Downloads\하루기획.pptx`를 기준으로 현재 Haru 백엔드 구현 상태를 정리한다.

## 요약

현재 구현은 MVP 전체 중 **계정/권한, 튜터 전환, 튜터 프로필 등록/승인, 공개 Experts 목록/상세 조회, 튜터 가능 시간 슬롯, Booking v1**까지 완료된 상태다.

다음 큰 흐름인 **결제, Booking 확장, 정산, 채팅, 알림, 리뷰, 운영 관리자 확장 기능**은 아직 구현되지 않았다.

현재 동작 가능한 핵심 흐름:

```text
회원가입
-> 로그인
-> 튜터 전환
-> 튜터 프로필 작성
-> 승인 요청
-> 관리자 승인
-> 공개 Experts 목록/상세 노출
-> 튜터 가능 시간 등록
-> 25분 수업 예약
```

## 구현 완료

| 기획안 항목 | 현재 구현 상태 |
| --- | --- |
| 이메일 회원가입/로그인 | `POST /api/auth/signup`, `POST /api/auth/login` 구현 |
| JWT 인증 | access token, refresh token, logout, refresh token rotation 구현 |
| 고객으로 가입 후 튜터 전환 | `POST /api/tutors/me/switch` 구현 |
| 학생/튜터/Admin 역할 구조 | `user_roles`, `users.active_role` 기반 구현 |
| 사용자 프로필 | 이름, 휴대전화, 시간대 조회/수정 구현 |
| 튜터 레슨 프로필 1개 등록/수정 | `GET/PUT /api/tutors/me/profile` 구현 |
| 튜터 승인 상태 | `DRAFT`, `PENDING`, `APPROVED`, `REJECTED` 구현 |
| 튜터 승인 요청 | `POST /api/tutors/me/profile/submit` 구현 |
| 관리자 승인/반려 | `PATCH /api/admin/tutors/{id}/approve`, `PATCH /api/admin/tutors/{id}/reject`, `GET /api/admin/tutors/pending` 구현 |
| 승인된 튜터만 공개 노출 | `GET /api/tutors`에서 `APPROVED`만 반환 |
| 공개 튜터 상세 조회 | `GET /api/tutors/{tutorProfileId}` 구현 |
| 튜터 가능 언어 | API에서 문자열 배열로 입출력 |
| 튜터 카테고리 | `KOREAN`, `KPOP`, `KBEAUTY`, `OTHER` enum 구현 |
| 25분/50분 가격 | `lessonPrice25Amount`, `lessonPrice50Amount` 구현 |
| YouTube 소개 영상 URL | YouTube URL 검증 구현 |
| 튜터 가능 시간 슬롯 | `PUT/GET /api/tutors/me/schedule`, `GET /api/tutors/{id}/schedule` 구현 |
| Booking v1 | 25분 수업 예약 생성, 내 예약 조회, 취소, join 가능 여부 조회 구현 |
| 로컬 실행 환경 | Docker Compose로 backend + MySQL 실행 가능 |
| API 확인 도구 | Swagger UI, static test UI 제공 |

## 부분 구현

| 기획안 항목 | 현재 상태 | 남은 작업 |
| --- | --- | --- |
| 소개 영상 | `introVideoUrl` 저장 가능 | 직접 녹화, 업로드, 재생 처리 미구현 |
| 썸네일 | `thumbnailUrl` 저장 가능 | 업로드/선택/생성 기능 미구현 |
| 지급 수단 | `paymentMethod` 문자열 저장 | PayPal, Payoneer, 국내계좌 구조화 미구현 |
| 카테고리 | 고정 enum 제공 | 관리자 카테고리 생성/삭제/노출순서 미구현 |
| 관리자 기능 | 튜터 승인 대기 조회, 승인/반려 가능 | 대시보드, 회원관리, 결제/정산, 신고/후기 관리 미구현 |
| 공개 전문가 카드 | 기본 프로필/가격/언어 제공 | 평점, 리뷰 개수, 접속 여부 미구현 |
| 시간대 | 사용자 `timeZone` 저장, 스케줄은 UTC로 저장/반환 | 화면용 시간 변환 미구현 |

## 미구현

| 도메인 | 기획안 요구 |
| --- | --- |
| Booking 확장 | 50분 수업, 예약 변경, 완료 수업 목록, Lessons to Schedule |
| Lesson Join | 실제 Jitsi Meet 링크 생성/저장 |
| Recording | Jitsi 수업 녹화본 저장 및 관리자 조회 |
| Payment | 1회/5회/10회권, 카드/PayPal/간편결제, 5회 5% 할인, 10회 10% 할인 |
| Student Fee | 학생 결제 시 5% 수수료 안내 및 계산 |
| Settlement | 튜터 수익 반영, 15% 플랫폼 수수료, 3.5% 송금 수수료, 월 1회 인출 |
| Chat | 학생-튜터 채팅방, 메시지 조회/전송 |
| Notification | 예약/변경/취소/수업 30분 전 알림, 이메일 알림 |
| Review | 수업 완료 후 학생 리뷰, 튜터 초기 리뷰 3개 정책 |
| Report | 수업 문제 신고, 지급 보류, 관리자 검토 |
| Admin Expansion | 대시보드, 회원관리, 레슨관리, 결제/정산관리, 신고/후기관리, 통계/분석 |
| Social Login | 카카오 등 SNS 로그인 |
| Multilingual UI | ENG/KOR 언어 선택 |

## 현재 API 기준

```text
POST   /api/auth/signup
POST   /api/auth/login
POST   /api/auth/refresh
POST   /api/auth/logout
GET    /api/auth/me

GET    /api/users/me
PATCH  /api/users/me
PATCH  /api/users/me/active-role

POST   /api/tutors/me/switch
GET    /api/tutors/me/profile
PUT    /api/tutors/me/profile
POST   /api/tutors/me/profile/submit

GET    /api/tutors
GET    /api/tutors/{tutorProfileId}

PATCH  /api/admin/tutors/{tutorProfileId}/approve
PATCH  /api/admin/tutors/{tutorProfileId}/reject
GET    /api/admin/tutors/pending

PUT    /api/tutors/me/schedule
GET    /api/tutors/me/schedule
GET    /api/tutors/{tutorProfileId}/schedule

POST   /api/bookings
GET    /api/bookings/me
GET    /api/bookings/{bookingId}
PATCH  /api/bookings/{bookingId}/cancel
GET    /api/bookings/{bookingId}/join
```

## 추천 작업 단위

### 1. 현재 변경분 정리 및 검증

- 현재 튜터 프로필 구조화 변경분을 테스트로 검증한다.
- `./gradlew.bat test`를 실행하고, 실패가 있으면 현재 변경 범위 안에서만 수정한다.
- `tutor_profiles.lesson_price_amount` legacy 컬럼은 V5 마이그레이션에서 삭제한다.

### 2. Payment 상태 모델

- 실제 PG 연동 전에 결제 요청, 결제 상태, 회차권, 할인 정책을 먼저 모델링한다.
- 1회/5회/10회권과 학생 5% 수수료 계산 기준을 고정한다.

### 3. Booking 확장

- 50분 수업 예약을 연속 슬롯 기준으로 지원한다.
- 예약 변경, 완료 처리, 문제 신고를 추가한다.
- Lessons to Schedule 잔여 횟수와 연결한다.

### 4. Settlement 상태 모델

- 수업 완료 후 튜터 수익을 반영한다.
- 플랫폼 수수료 15%, 송금 수수료 3.5%, 인출 요청 상태를 모델링한다.
- 실제 송금 자동화는 MVP 범위에서 제외한다.

## 우선순위 판단

다음 개발은 **Payment -> Booking 확장 -> Settlement** 순서가 가장 안전하다.

이유:

- 예약은 스케줄에 의존한다.
- 결제는 예약 대상과 회차권 정책에 의존한다.
- 정산은 완료된 수업과 결제 금액에 의존한다.
- 채팅/알림/리뷰는 예약 흐름이 생긴 뒤 붙이는 편이 구현 기준이 명확하다.
