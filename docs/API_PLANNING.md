# API Planning

## 목적

이 문서는 MVP에서 필요할 주요 API 후보를 설계 수준으로 정리한다. 실제 Controller, DTO, Service 구현은 현재 단계에서 하지 않는다.

## 공통 API 기준

- 인증 필요 API는 Access Token을 요구한다.
- 시간 값은 서버 저장/요청 기준 UTC를 우선 사용하고, 응답에는 사용자 표시 시간대를 포함하는 방향을 권장한다.
- Admin API는 `/api/admin` 하위에 둔다.
- 실제 URL은 구현 단계에서 REST 규칙과 프론트 요구사항에 맞춰 조정 가능하다.

## Auth P0 API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/auth/signup` | 이메일 기반 회원가입 | No |
| POST | `/api/auth/login` | 이메일 로그인 | No |
| POST | `/api/auth/social-login` | 소셜 로그인 진입점 후보 | No |
| POST | `/api/auth/refresh` | Access Token 재발급 | Refresh |
| POST | `/api/auth/logout` | Refresh Token 폐기 | Yes |
| GET | `/api/auth/me` | 현재 사용자와 activeRole 조회 | Yes |

### 주요 설계 포인트

- 회원가입 시 기본 역할은 Student.
- Tutor 전환은 별도 역할 활성화 또는 튜터 프로필 생성 단계에서 처리한다.
- Admin은 일반 회원가입으로 부여하지 않는다.

## User API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/users/me` | 내 프로필 조회 | Yes |
| PATCH | `/api/users/me` | 이름, 휴대전화, 시간대 수정 | Yes |
| PATCH | `/api/users/me/password` | 비밀번호 변경 후보 | Yes |
| PATCH | `/api/users/me/active-role` | Student/Tutor 모드 전환 | Yes |

### 주요 설계 포인트

- PPT 기준 튜터도 처음에는 고객으로 로그인한다.
- `activeRole`은 화면 모드 전환에 사용한다.
- 사용자의 표준 시간대는 자동 설정하되 사용자가 변경 가능해야 한다.

## Tutor Profile API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/tutors` | 전문가 목록 조회 | Optional |
| GET | `/api/tutors/{tutorId}` | 전문가 상세 조회 | Optional |
| POST | `/api/tutors/me/profile` | 내 튜터 프로필 등록 | Yes |
| GET | `/api/tutors/me/profile` | 내 튜터 프로필 조회 | Yes |
| PATCH | `/api/tutors/me/profile` | 내 튜터 프로필 수정 | Yes |
| POST | `/api/tutors/me/profile/reviews` | 초기 리뷰 직접 등록 후보 | Yes |
| POST | `/api/tutors/me/payout-methods` | 지급 수단 등록 | Yes |
| GET | `/api/tutors/me/payout-methods` | 지급 수단 목록 조회 | Yes |

### 주요 설계 포인트

- 튜터 프로필은 1인 1개로 제한한다.
- 등록 완료 후 관리자 승인 대기 상태가 된다.
- 레슨 등록 페이지는 영어 입력 기준이며 한글 입력 제한은 구현 단계에서 validation 정책으로 다룬다.

## Schedule API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/tutors/{tutorId}/schedule` | 학생용 튜터 가능 시간 조회 | Optional |
| PUT | `/api/tutors/me/schedule` | 튜터 가능 시간 저장 | Yes |
| GET | `/api/tutors/me/schedule` | 내 가능 시간 조회 | Yes |

### 주요 설계 포인트

- 저장은 UTC, 표시는 사용자 시간대 기준.
- 30분 단위 슬롯을 기본 단위로 한다.
- 예약된 시간은 예약 가능 목록에서 제외한다.

## Booking API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/bookings` | 예약 생성 | Yes |
| GET | `/api/bookings/me` | 내 예약 목록 조회 | Yes |
| GET | `/api/bookings/{bookingId}` | 예약 상세 조회 | Yes |
| PATCH | `/api/bookings/{bookingId}/reschedule` | 예약 시간 변경 | Yes |
| PATCH | `/api/bookings/{bookingId}/cancel` | 예약 취소 | Yes |
| GET | `/api/bookings/{bookingId}/join` | 수업 입장 정보 조회 | Yes |
| PATCH | `/api/bookings/{bookingId}/complete` | 학생 수업 완료 확인 | Yes |
| POST | `/api/bookings/{bookingId}/issue` | 수업 문제 신고 | Yes |

### 주요 설계 포인트

- 수업 시작 10분 전부터 입장 가능.
- 수업 3시간 전까지만 변경/취소 가능.
- 변경/취소 시 사유를 저장한다.
- 학생과 튜터 모두 예약 목록을 보지만 표시 대상만 다르다.

## Payment API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/payments/checkout` | 결제 요청 생성 | Yes |
| GET | `/api/payments/me` | 내 결제 내역 조회 | Yes |
| GET | `/api/payments/{paymentId}` | 결제 상세 조회 | Yes |
| POST | `/api/payments/{paymentId}/refund-request` | 환불 요청 후보 | Yes |

### 주요 설계 포인트

- 1회/5회/10회권을 지원한다.
- 5회권 5%, 10회권 10% 할인을 계산한다.
- 학생 결제 수수료 5%를 별도 금액으로 표시한다.
- 실제 PG callback/webhook API는 PG 선정 후 별도 설계한다.

## Settlement API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/settlements/me/summary` | 튜터 수익 요약 | Tutor |
| GET | `/api/settlements/me/transactions` | 수입/인출 내역 조회 | Tutor |
| POST | `/api/settlements/me/withdrawals` | 인출 신청 | Tutor |
| GET | `/api/settlements/me/withdrawals` | 인출 신청 내역 | Tutor |

### 주요 설계 포인트

- 수업 완료 후 튜터 수익에 반영한다.
- 플랫폼 수수료 15%를 차감한다.
- 송금 수수료는 MVP 정책상 3.5% 통일 후보.
- 실제 송금은 현재 구현 대상이 아니다.

## Chat/Notification API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/chats` | 내 채팅방 목록 | Yes |
| GET | `/api/chats/{chatRoomId}/messages` | 메시지 목록 | Yes |
| POST | `/api/chats/{chatRoomId}/messages` | 메시지 전송 | Yes |
| GET | `/api/notifications/me` | 내 알림 목록 | Yes |

### 주요 설계 포인트

- MVP에서는 이메일 알림을 우선한다.
- 휴대폰 푸시, 접속 상태, Read/Unread는 제외한다.

## Review/Report API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/bookings/{bookingId}/reviews` | 완료 수업 리뷰 작성 | Student |
| GET | `/api/tutors/{tutorId}/reviews` | 튜터 리뷰 조회 | Optional |
| POST | `/api/reports` | 신고 생성 | Yes |

### 주요 설계 포인트

- 완료된 수업당 학생 리뷰 1회.
- 튜터 초기 리뷰 3개 정책은 Tutor Profile API 또는 Review 도메인에서 구현 단계에 결정한다.

## Admin API

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/admin/dashboard` | 운영 대시보드 요약 | Admin |
| GET | `/api/admin/users` | 회원 검색 | Admin |
| PATCH | `/api/admin/users/{userId}/status` | 회원 상태 변경 | Admin |
| GET | `/api/admin/tutor-profiles/pending` | 전문가 승인 대기 목록 | Admin |
| PATCH | `/api/admin/tutor-profiles/{profileId}/approve` | 전문가 승인 | Admin |
| PATCH | `/api/admin/tutor-profiles/{profileId}/reject` | 전문가 거절 | Admin |
| GET | `/api/admin/bookings` | 전체 예약/레슨 조회 | Admin |
| GET | `/api/admin/payments` | 결제 내역 조회 | Admin |
| GET | `/api/admin/settlements` | 정산 내역 조회 | Admin |
| GET | `/api/admin/reports` | 신고 목록 조회 | Admin |
| PATCH | `/api/admin/reviews/{reviewId}/visibility` | 후기 공개/비공개 처리 | Admin |
| GET | `/api/admin/video-recordings` | 녹화본 목록/참조 조회 | Admin |

## 현재 단계에서 구현하지 않는 것

- Controller/Service/DTO 작성
- 실제 API path 확정
- OpenAPI/Swagger 명세 생성
- PG webhook, Jitsi webhook, 이메일 provider 연동
