# API Planning

## 목적

이 문서는 Haru 백엔드 API를 현재 구현 상태와 이후 구현 후보로 나누어 정리한다.

문서 기준:

- 현재 구현: Controller, Service, DTO, 테스트 또는 Swagger 설명까지 반영된 API
- 다음 구현 후보: MVP 흐름상 곧 필요하지만 아직 코드가 없는 API
- 보류/외부 연동: PG, Jitsi, 이메일 등 provider 결정 이후 확정할 API

## 공통 API 기준

- 성공 응답은 `ApiResponse` 형식을 사용한다.
- 실패 응답은 `ErrorResponse` 형식을 사용한다.
- 인증 필요 API는 `Authorization: Bearer {accessToken}`을 요구한다.
- refresh token은 request body로 전달하며 서버에는 원문이 아닌 SHA-256 hash를 저장한다.
- 시간 값은 서버 저장 기준 UTC를 우선한다.
- 사용자의 표시 시간대는 `users.time_zone`의 IANA timezone 값을 기준으로 한다.
- Admin API는 `/api/admin/**` 하위에 둔다.
- 공개 API는 명시적으로 `permitAll`에 포함된 API만 허용한다.

## 현재 구현된 API

### Auth

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/auth/signup` | 이메일 기반 회원가입 후 token pair 발급 | No |
| POST | `/api/auth/login` | 이메일 또는 내부 계정 ID 로그인 후 token pair 발급 | No |
| POST | `/api/auth/refresh` | refresh token 회전 및 새 token pair 발급 | No |
| POST | `/api/auth/logout` | 전달된 refresh token 폐기 | Yes |
| GET | `/api/auth/me` | 현재 사용자, roles, activeRole, tutorProfileStatus 조회 | Yes |

구현 메모:

- 회원가입 시 기본 role과 activeRole은 `STUDENT`다.
- 로그인 성공 시 `last_login_at`을 갱신한다.
- refresh token은 회전 방식이다.
- 이미 폐기된 refresh token 재사용은 `REFRESH_TOKEN_REUSED`로 차단한다.
- access token에는 발급 시점의 role/activeRole claim이 들어가지만, 인증 필터는 매 요청마다 DB에서 사용자를 다시 조회한다.

### User

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/users/me` | 내 사용자 정보 조회 | Yes |
| PATCH | `/api/users/me` | 이름, 휴대전화, 시간대 수정 | Yes |
| PATCH | `/api/users/me/active-role` | 현재 앱 모드 변경 | Yes |

구현 메모:

- `activeRole`은 현재 UI 모드다.
- 요청한 activeRole은 사용자의 `user_roles`에 이미 포함되어 있어야 한다.
- `activeRole = TUTOR`는 승인된 판매 가능 튜터라는 뜻이 아니다.
- 튜터 승인 상태는 `tutorProfileStatus`로 판단한다.

### Tutor

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/tutors/me/switch` | TUTOR role 추가, activeRole 변경, DRAFT 프로필 생성 | Yes |
| GET | `/api/tutors/me/profile` | 내 튜터 프로필 조회 | Yes |
| PUT | `/api/tutors/me/profile` | 내 튜터 프로필 임시저장 | Yes |
| POST | `/api/tutors/me/profile/submit` | 필수값 검증 후 승인 요청 | Yes |
| GET | `/api/tutors` | 승인된 Experts 공개 목록 조회 | No |
| GET | `/api/tutors/{tutorProfileId}` | 승인된 Expert 공개 상세 조회 | No |

구현 메모:

- 튜터 전환은 승인 완료가 아니다.
- 프로필 상태는 `DRAFT`, `PENDING`, `APPROVED`, `REJECTED`다.
- `category`는 `KOREAN`, `KPOP`, `KBEAUTY`, `OTHER` 중 하나다.
- `availableLanguages`는 API에서 문자열 배열로 받고 내려준다.
- 승인 요청 필수값은 `displayName`, `shortIntroduction`, `aboutMe`, `whatIOffer`, `category`, `availableLanguages`, `lessonPrice25Amount`, `lessonPrice50Amount`, `availableTimeNote`, `paymentMethod`다.
- `introVideoUrl`은 비어 있거나 YouTube URL이어야 한다.
- `APPROVED` 또는 `REJECTED` 상태에서 프로필을 수정하면 `DRAFT`로 돌아간다.
- `/api/tutors`와 `/api/tutors/{tutorProfileId}`는 `APPROVED` 프로필만 반환한다.

### Admin

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| PATCH | `/api/admin/tutors/{tutorProfileId}/approve` | PENDING 튜터 프로필 승인 | Admin |
| PATCH | `/api/admin/tutors/{tutorProfileId}/reject` | PENDING 튜터 프로필 반려 | Admin |
| GET | `/api/admin/tutors/pending` | PENDING 튜터 프로필 승인 대기 목록 조회 | Admin |

구현 메모:

- `/api/admin/**`는 `ROLE_ADMIN` 권한이 필요하다.
- 현재 Admin API는 TutorController에 함께 구현되어 있다.
- 이후 관리자 API가 늘어나면 별도 AdminController로 분리하는 편이 좋다.

### Schedule

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| PUT | `/api/tutors/me/schedule` | 내 가능 시간 슬롯 전체 교체 | Tutor |
| GET | `/api/tutors/me/schedule` | 내 가능 시간 슬롯 조회 | Tutor |
| GET | `/api/tutors/{tutorProfileId}/schedule` | 승인된 튜터 가능 시간 공개 조회 | No |

구현 메모:

- 저장 기준은 UTC다.
- `from`, `to` query parameter로 UTC 조회 범위를 받는다.
- `PUT`은 기존 슬롯을 요청 목록으로 전체 교체한다.
- `startAt`은 30분 단위여야 하며, `endAt`은 `startAt + 30분`으로 저장한다.
- 공개 조회는 `APPROVED` 튜터 프로필만 허용한다.
- 반복 일정, 예외 일정, 예약 충돌 검사는 아직 구현하지 않는다.

### Booking

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/bookings` | 25분 수업 예약 생성 | Student |
| GET | `/api/bookings/me` | 내 예약 목록 조회 | Yes |
| GET | `/api/bookings/{bookingId}` | 예약 상세 조회 | Yes |
| PATCH | `/api/bookings/{bookingId}/cancel` | 예약 취소 | Yes |
| GET | `/api/bookings/{bookingId}/join` | 수업 입장 정보 조회 | Yes |

구현 메모:

- `APPROVED` 튜터 프로필만 예약 가능하다.
- 수업 시작 10분 전부터 입장을 허용한다.
- 수업 3시간 전까지만 취소를 허용한다.
- 현재는 25분 수업만 예약 가능하다.
- 실제 Jitsi 링크는 아직 생성하지 않고 join placeholder를 반환한다.
- 예약 변경, 완료 처리, 이슈 신고, 50분 수업, 회차권 관리는 아직 구현하지 않는다.

## 이후 구현 후보

### Payment

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/payments/checkout` | 결제 요청 생성 | Student |
| GET | `/api/payments/me` | 내 결제 내역 조회 | Student |
| GET | `/api/payments/{paymentId}` | 결제 상세 조회 | Yes |
| POST | `/api/payments/{paymentId}/refund-request` | 환불 요청 | Student |

권장 기준:

- 실제 PG 연동 전에도 결제 상태 모델을 먼저 만든다.
- 1회/5회/10회권을 지원한다.
- 5회권 5%, 10회권 10% 할인을 계산한다.
- 학생 결제 수수료 5%를 별도 금액으로 표시한다.

### Settlement

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/settlements/me/summary` | 튜터 수익 요약 | Tutor |
| GET | `/api/settlements/me/transactions` | 수입/인출 내역 조회 | Tutor |
| POST | `/api/settlements/me/withdrawals` | 인출 신청 | Tutor |
| GET | `/api/settlements/me/withdrawals` | 인출 신청 내역 | Tutor |

권장 기준:

- 수업 완료 후 튜터 수익에 반영한다.
- 플랫폼 수수료 15%를 차감한다.
- 송금 수수료는 MVP 정책상 3.5% 통일 후보로 둔다.
- 실제 송금 자동화는 구현 대상이 아니다.

### Chat/Notification

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/chats` | 내 채팅방 목록 | Yes |
| GET | `/api/chats/{chatRoomId}/messages` | 메시지 목록 | Yes |
| POST | `/api/chats/{chatRoomId}/messages` | 메시지 전송 | Yes |
| GET | `/api/notifications/me` | 내 알림 목록 | Yes |

권장 기준:

- MVP에서는 이메일 알림을 우선한다.
- 휴대폰 푸시, 접속 상태, Read/Unread는 후순위다.

### Review/Report

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| POST | `/api/bookings/{bookingId}/reviews` | 완료 수업 리뷰 작성 | Student |
| GET | `/api/tutors/{tutorProfileId}/reviews` | 튜터 리뷰 조회 | No |
| POST | `/api/reports` | 신고 생성 | Yes |

권장 기준:

- 완료된 수업당 학생 리뷰 1회를 허용한다.
- 튜터 초기 리뷰 3개 정책은 Tutor Profile 또는 Review 도메인 중 하나로 귀속시켜야 한다.

### Admin Expansion

| Method | Path | 설명 | 인증 |
| --- | --- | --- | --- |
| GET | `/api/admin/dashboard` | 운영 대시보드 요약 | Admin |
| GET | `/api/admin/users` | 회원 검색 | Admin |
| PATCH | `/api/admin/users/{userId}/status` | 회원 상태 변경 | Admin |
| GET | `/api/admin/bookings` | 전체 예약/레슨 조회 | Admin |
| GET | `/api/admin/payments` | 결제 내역 조회 | Admin |
| GET | `/api/admin/settlements` | 정산 내역 조회 | Admin |
| GET | `/api/admin/reports` | 신고 목록 조회 | Admin |
| PATCH | `/api/admin/reviews/{reviewId}/visibility` | 후기 공개/비공개 처리 | Admin |
| GET | `/api/admin/video-recordings` | 녹화본 목록/참조 조회 | Admin |

## 명시적으로 보류한 항목

- 실제 카카오/구글/애플 소셜 로그인 연동
- 이메일 인증, 비밀번호 찾기/재설정, 2FA
- 영상 업로드, 썸네일 생성, 영상 인코딩
- PG callback/webhook
- Jitsi webhook, 녹화본 저장 연동
- 이메일 provider 연동
- 고급 검색, 추천, 통계/분석

## 다음 개발 순서 제안

1. 현재 변경분 테스트 검증 및 문서 동기화
2. Payment 상태 모델
3. 50분 수업 및 회차권 기반 Lessons to Schedule
4. Booking reschedule/complete/issue
5. Settlement 상태 모델
