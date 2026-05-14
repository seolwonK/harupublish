# Tutor Role and Profile Flow

이 문서는 현재 구현된 튜터 전환, `activeRole`, `roles`, `tutor_profiles.status`의 의미를 명확히 정리한다.

## 핵심 결론

현재 구조는 후보 C에 가깝다.

- 인증 계정은 `users`가 담당한다.
- 계정이 가질 수 있는 모드/권한은 `user_roles`가 담당한다.
- 현재 사용자가 보고 있는 앱 모드는 `users.active_role`이 담당한다.
- 실제 튜터 판매 가능 여부와 Experts 목록 노출 여부는 `tutor_profiles.status`가 담당한다.

따라서 `activeRole = TUTOR`는 "튜터 모드로 진입했다"는 뜻이지, "승인된 판매 가능 강사"라는 뜻이 아니다.

## 용어 구분

### roles

`user_roles` 테이블에 저장된다.

예시:

```text
STUDENT
TUTOR
ADMIN
```

의미:

- 이 계정이 사용할 수 있는 권한 또는 모드 목록이다.
- 일반 회원가입 직후에는 `STUDENT`만 가진다.
- 튜터 전환을 누르면 `TUTOR`가 추가된다.
- 관리자는 `ADMIN`을 가진다.

### activeRole

`users.active_role` 컬럼에 저장된다.

의미:

- 현재 사용자가 앱에서 선택한 모드다.
- 학생 화면을 보고 있으면 `STUDENT`.
- 튜터 대시보드나 튜터 프로필 작성 화면으로 들어가면 `TUTOR`.
- 관리자 화면이면 `ADMIN`.

주의:

- `activeRole = TUTOR`는 튜터 승인 완료를 의미하지 않는다.
- `activeRole`은 UI 모드, 네비게이션, 대시보드 진입 기준으로 쓰는 값이다.
- 판매 가능 여부, 예약 가능 여부, 고객 메인 Experts 노출 여부는 `activeRole`로 판단하면 안 된다.

### tutorProfile.status

`tutor_profiles.status` 컬럼에 저장된다.

현재 상태 값:

```text
DRAFT
PENDING
APPROVED
REJECTED
```

의미:

- `DRAFT`: 튜터 프로필 작성 중
- `PENDING`: 관리자 승인 대기
- `APPROVED`: 관리자 승인 완료
- `REJECTED`: 관리자 반려

중요 규칙:

- Experts 목록에는 반드시 `APPROVED` 상태만 노출한다.
- `DRAFT`, `PENDING`, `REJECTED`는 튜터 모드 접근은 가능하지만 고객에게 판매 가능한 강사로 노출되지 않는다.

## 현재 구현된 흐름

### 1. 일반 회원가입

API:

```http
POST /api/auth/signup
```

생성 결과:

```text
users.active_role = STUDENT
user_roles = STUDENT
tutor_profiles row 없음
```

이 시점의 사용자는 일반 고객 계정이다.

### 2. 로그인

API:

```http
POST /api/auth/login
```

동작:

- access token과 refresh token을 발급한다.
- `users.last_login_at`을 현재 시각으로 갱신한다.

### 3. 튜터 전환

API:

```http
POST /api/tutors/me/switch
Authorization: Bearer {accessToken}
```

현재 구현 동작:

```text
1. 현재 로그인 사용자를 조회한다.
2. user_roles에 TUTOR를 추가한다.
3. users.active_role을 TUTOR로 변경한다.
4. tutor_profiles가 없으면 DRAFT 상태로 생성한다.
5. 이미 tutor_profiles가 있으면 기존 프로필을 반환한다.
```

결과 예시:

```text
users.active_role = TUTOR
user_roles = STUDENT, TUTOR
tutor_profiles.status = DRAFT
```

이 단계에서 사용자는 튜터 모드에 들어간다. 하지만 아직 고객 메인 Experts 목록에 노출되지는 않는다.

### 4. 내 튜터 프로필 조회

API:

```http
GET /api/tutors/me/profile
Authorization: Bearer {accessToken}
```

동작:

- 로그인 사용자의 `tutor_profiles`를 조회한다.
- 튜터 전환을 아직 하지 않아 프로필이 없으면 `404 NOT_FOUND`가 반환된다.

### 5. 내 튜터 프로필 저장

API:

```http
PUT /api/tutors/me/profile
Authorization: Bearer {accessToken}
```

현재 저장 가능한 필드:

```text
displayName
shortIntroduction
aboutMe
whatIOffer
category
profileImageUrl
introVideoUrl
thumbnailUrl
availableLanguages
lessonPriceAmount
availableTimeNote
paymentMethod
```

중요 규칙:

- 이 API는 프로필을 저장만 한다.
- 저장했다고 `APPROVED`로 바뀌지 않는다.
- `REJECTED` 상태에서 다시 수정하면 현재 구현상 `DRAFT`로 돌아간다.
- `APPROVED` 상태에서 다시 수정하면 현재 구현상 `DRAFT`로 돌아간다.
- 승인된 프로필을 수정하면 재검수가 필요하므로 Experts 목록 노출도 중단된다.

### 6. 승인 요청 제출

API:

```http
POST /api/tutors/me/profile/submit
Authorization: Bearer {accessToken}
```

현재 필수값:

```text
displayName
shortIntroduction
aboutMe
whatIOffer
category
availableLanguages
lessonPriceAmount
availableTimeNote
paymentMethod
```

동작:

- 필수값이 부족하면 `400 INVALID_REQUEST`.
- 필수값이 채워져 있으면 `tutor_profiles.status = PENDING`.
- `submitted_at`을 기록한다.
- `approved_at`, `rejected_at`은 초기화한다.

이 상태에서도 Experts 목록에는 노출되지 않는다.

### 7. 관리자 승인

API:

```http
PATCH /api/admin/tutors/{tutorProfileId}/approve
Authorization: Bearer {adminAccessToken}
```

권한:

- `ROLE_ADMIN` 필요.

동작:

```text
PENDING 상태만 승인 가능
tutor_profiles.status = APPROVED
approved_at = 현재 시각
rejected_at = null
```

이때부터 고객 메인 Experts 목록에 노출될 수 있다.

### 8. 관리자 반려

API:

```http
PATCH /api/admin/tutors/{tutorProfileId}/reject
Authorization: Bearer {adminAccessToken}
```

권한:

- `ROLE_ADMIN` 필요.

동작:

```text
PENDING 상태만 반려 가능
tutor_profiles.status = REJECTED
rejected_at = 현재 시각
approved_at = null
```

반려된 프로필은 Experts 목록에 노출되지 않는다.

### 9. Experts 목록 조회

API:

```http
GET /api/tutors
```

인증:

- 현재 구현상 공개 API다.

동작:

- `tutor_profiles.status = APPROVED`인 프로필만 반환한다.
- `activeRole = TUTOR`만으로는 절대 노출하지 않는다.

## 상태별 판단표

| 상황 | roles | activeRole | tutorProfile.status | 튜터 대시보드 | 프로필 작성 | Experts 노출 |
| --- | --- | --- | --- | --- | --- | --- |
| 일반 회원가입 직후 | STUDENT | STUDENT | 없음 | 불가 | 불가 | 불가 |
| 튜터 전환 직후 | STUDENT, TUTOR | TUTOR | DRAFT | 가능 | 가능 | 불가 |
| 승인 요청 후 | STUDENT, TUTOR | TUTOR | PENDING | 가능 | 가능 | 불가 |
| 관리자 승인 후 | STUDENT, TUTOR | TUTOR | APPROVED | 가능 | 가능 | 가능 |
| 관리자 반려 후 | STUDENT, TUTOR | TUTOR | REJECTED | 가능 | 가능 | 불가 |

## JWT와 activeRole 주의사항

현재 access token에는 발급 시점의 `roles`, `activeRole` 클레임이 들어간다.

따라서 DB에서 `active_role`이 바뀌어도 이미 발급된 access token 안의 클레임은 자동으로 바뀌지 않는다.

다만 현재 `/api/users/me`와 `/api/auth/me`는 DB에서 사용자를 다시 조회해 응답을 만든다. 그래서 서버 응답의 사용자 정보는 DB 기준으로 갱신된 값을 볼 수 있다.

프론트엔드에서 주의할 점:

- 화면 모드는 `/api/users/me` 또는 `/api/auth/me` 응답의 `activeRole`을 기준으로 보는 편이 안전하다.
- 토큰 payload만 디코딩해서 현재 모드를 장기간 판단하면 오래된 값이 보일 수 있다.
- 튜터 승인 여부는 토큰이 아니라 `/api/tutors/me/profile`의 `status` 또는 사용자 응답의 `tutorProfileStatus`를 기준으로 봐야 한다.

## 현재 구현 파일

튜터 도메인:

```text
src/main/java/com/haru/tutor/domain/TutorProfile.java
src/main/java/com/haru/tutor/domain/TutorProfileStatus.java
```

튜터 API:

```text
src/main/java/com/haru/tutor/api/TutorController.java
src/main/java/com/haru/tutor/api/dto/TutorProfileRequest.java
src/main/java/com/haru/tutor/api/dto/TutorProfileResponse.java
src/main/java/com/haru/tutor/api/dto/ExpertListResponse.java
```

튜터 서비스:

```text
src/main/java/com/haru/tutor/application/TutorService.java
```

튜터 저장소:

```text
src/main/java/com/haru/tutor/infra/TutorProfileRepository.java
```

DB 마이그레이션:

```text
src/main/resources/db/migration/V3__create_tutor_profiles.sql
```

테스트:

```text
src/test/java/com/haru/tutor/TutorIntegrationTest.java
```

## 현재 반영된 사용자 응답

현재 `UserMeResponse`의 `tutorProfileStatus` 필드는 실제 `tutor_profiles.status`와 연결되어 있다.

```text
GET /api/users/me
GET /api/auth/me
```

위 API는 로그인 사용자의 튜터 프로필이 있으면 실제 상태를 내려준다.

또한 다음 인증 응답의 `user.tutorProfileStatus`에도 같은 값이 포함된다.

```text
POST /api/auth/signup
POST /api/auth/login
POST /api/auth/refresh
```

튜터 프로필이 없으면 `tutorProfileStatus = null`이다.

응답 예시:

```json
{
  "roles": ["STUDENT", "TUTOR"],
  "activeRole": "TUTOR",
  "tutorProfileStatus": "PENDING"
}
```

프론트엔드는 다음 기준으로 명확히 판단한다.

```text
activeRole == TUTOR
튜터 모드 화면 표시

tutorProfileStatus == APPROVED
Experts 목록 노출 및 예약/판매 가능
```
