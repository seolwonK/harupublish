# Auth First Plan

> Historical planning document. 현재 구현 상태와 API 계약은 README, Swagger, API_PLANNING.md, DATABASE_SCHEMA.md를 기준으로 확인한다.

## 왜 인증 도메인을 먼저 구현해야 하는가

Haru의 주요 기능은 모두 사용자 식별과 역할 판단에 의존한다. 학생은 예약과 결제를 수행하고, 튜터는 프로필과 스케줄을 등록하며, 관리자는 승인과 운영 처리를 수행한다. 같은 계정이 Student와 Tutor 역할을 모두 가질 수 있고, 화면에서는 현재 사용 중인 역할(activeRole)에 따라 메뉴와 권한이 달라진다.

따라서 Auth/User를 먼저 구현하지 않으면 Tutor Profile, Booking, Payment, Admin의 소유자와 권한 기준이 흔들린다. MVP 개발은 인증과 사용자 모델을 P0로 고정한 뒤 나머지 도메인을 연결해야 한다.

## Auth P0 범위

- 이메일 회원가입
- 이메일 로그인
- Access Token 발급
- Refresh Token 발급/저장/재발급
- Logout 시 Refresh Token 폐기
- `/me` API로 현재 사용자, roles, activeRole, 계정 상태 반환
- 사용자 기본 프로필 저장
- 표준 시간대 저장
- Student/Tutor/Admin role 구조
- activeRole 변경

## P0 제외 범위

- 실제 카카오/구글/애플 소셜 로그인 연동
- 이메일 인증
- 비밀번호 찾기/재설정
- 2FA
- 관리자 계정 생성 UI
- 세밀한 권한 그룹 관리

## roles와 activeRole 정책

### roles

`roles`는 사용자가 보유한 권한 집합이다.

- `STUDENT`: 기본 가입 사용자
- `TUTOR`: 튜터 기능 사용 가능 사용자
- `ADMIN`: 관리자 기능 사용 가능 사용자

일반 사용자는 가입 시 `STUDENT`를 가진다. PPT 기준 "전문가도 우선 고객으로 무조건 로그인"하므로, 튜터가 되려는 사용자는 기존 Student 계정에서 Tutor 역할을 추가하거나 튜터 프로필 등록 흐름을 시작한다.

### activeRole

`activeRole`은 현재 사용자가 어떤 모드로 서비스를 사용하는지를 나타낸다.

- Student 모드: 전문가 탐색, 예약, 결제, 내 예약, 완료 수업
- Tutor 모드: 레슨 프로필, 예약 현황, 완료 수업, 수익 관리
- Admin 모드: 운영 관리

`activeRole`은 권한 자체가 아니다. 예를 들어 `roles`에 `TUTOR`가 없으면 `activeRole=TUTOR`로 변경할 수 없다.

## Student/Tutor/Admin 권한 구조

### Student

- 전문가 목록/상세 조회
- 예약 생성
- 결제
- 내 예약 조회
- 수업 입장
- 예약 변경/취소
- 수업 완료 확인
- 리뷰 작성
- 이슈 신고
- 튜터에게 메시지 전송

### Tutor

- 튜터 프로필 등록/수정
- 가능 시간 등록
- 지급 수단 등록
- 내 예약 현황 조회
- 수업 입장
- 예약 변경/취소
- 완료 수업 조회
- 수익/정산 정보 조회
- 학생에게 메시지 전송

튜터 기능 중 공개 노출과 예약 수락 가능 여부는 관리자 승인 상태와 연결한다. `TUTOR` 역할이 있더라도 프로필이 승인되지 않으면 공개 목록 노출이나 예약 가능 상태를 제한할 수 있다.

### Admin

- 회원 검색과 상태 변경
- 전문가 승인/거절
- 전체 예약 조회
- 결제/정산 조회
- 신고/후기 관리
- 수업 녹화본 조회
- 비매너 회원 block

Admin 권한은 일반 가입이나 튜터 전환으로 부여하지 않는다.

## 회원가입 API 설계

### 후보

`POST /api/auth/signup`

### 요청 후보

```json
{
  "email": "user@example.com",
  "password": "password",
  "name": "Keunhee Lee",
  "timeZone": "Asia/Seoul"
}
```

### 처리 기준

- 이메일 중복을 검증한다.
- 비밀번호는 해시로 저장한다.
- 기본 role은 `STUDENT`.
- 기본 activeRole도 `STUDENT`.
- timeZone이 없으면 클라이언트 탐지값 또는 서버 기본값을 사용한다.

## 로그인 API 설계

### 후보

`POST /api/auth/login`

### 요청 후보

```json
{
  "email": "user@example.com",
  "password": "password"
}
```

### 응답 후보

```json
{
  "accessToken": "...",
  "refreshToken": "...",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "name": "Keunhee Lee",
    "roles": ["STUDENT"],
    "activeRole": "STUDENT",
    "timeZone": "Asia/Seoul"
  }
}
```

## Refresh Token API 설계

### 후보

`POST /api/auth/refresh`

### 처리 기준

- Refresh Token이 유효하면 새 Access Token을 발급한다.
- Refresh Token rotation 적용 여부는 구현 단계에서 결정한다.
- 폐기되거나 만료된 Refresh Token은 재사용할 수 없다.

## Logout API 설계

### 후보

`POST /api/auth/logout`

### 처리 기준

- 현재 Refresh Token을 서버 저장소에서 폐기한다.
- 클라이언트는 Access Token과 Refresh Token을 삭제한다.
- 전체 기기 로그아웃은 후순위 확장으로 둔다.

## Me API 설계

### 후보

`GET /api/auth/me`

### 응답 후보

```json
{
  "id": 1,
  "email": "user@example.com",
  "name": "Keunhee Lee",
  "mobileNumber": "+82 10-9186-8147",
  "roles": ["STUDENT", "TUTOR"],
  "activeRole": "TUTOR",
  "accountStatus": "ACTIVE",
  "timeZone": "Asia/Seoul",
  "tutorProfileStatus": "PENDING"
}
```

## activeRole 변경 API 설계

### 후보

`PATCH /api/users/me/active-role`

### 요청 후보

```json
{
  "activeRole": "TUTOR"
}
```

### 처리 기준

- 요청한 activeRole이 사용자의 roles에 포함되어야 한다.
- `TUTOR` 역할이 없으면 튜터 프로필 등록 흐름으로 유도한다.
- `ADMIN`은 관리자 권한이 있는 계정에서만 가능하다.

## 이후 실제 구현 시 참고 기준

- User는 인증의 중심 aggregate로 둔다.
- Tutor Profile은 User와 1:1 관계 후보이지만 승인 상태와 공개 상태를 별도로 관리한다.
- Refresh Token은 로그아웃과 보안 통제를 위해 서버 저장소에 둔다.
- Access Token에는 userId, roles, activeRole을 포함할 수 있으나, 중요한 권한 판단은 서버 최신 상태를 확인하는 방향을 권장한다.
- 정지 또는 탈퇴 상태 계정은 로그인과 토큰 재발급을 차단한다.
- 시간대는 IANA timezone 문자열을 사용한다. 예: `Asia/Seoul`, `America/New_York`.

## 작성 당시 구현하지 않았던 것

이 문서는 인증 도메인의 우선순위와 정책 기준을 먼저 정리하기 위해 작성된 초기 계획 문서다. 현재는 Spring Security 설정, JWT 클래스, User Entity, Auth Controller, Auth Service, Repository, DB 테이블이 구현되어 있으므로 현행 상태는 README와 API_PLANNING.md를 기준으로 확인한다.
