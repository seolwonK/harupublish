# Database Schema

이 문서는 현재 Haru 백엔드의 DB 테이블 구조와 관계를 정리한 산출물이다.

기준:

- DB: MySQL 8.x
- 마이그레이션: Flyway
- 애플리케이션 설정: `spring.jpa.hibernate.ddl-auto=validate`
- 마이그레이션 위치: `src/main/resources/db/migration`

## 전체 테이블

현재 애플리케이션에서 사용하는 주요 테이블은 다음과 같다.

| 테이블 | 설명 |
| --- | --- |
| `users` | 인증 계정과 사용자 기본 정보 |
| `user_roles` | 사용자에게 부여된 역할 목록 |
| `refresh_tokens` | refresh token 저장 및 회전 이력 |
| `tutor_profiles` | 튜터 활동 정보와 관리자 승인 상태 |
| `flyway_schema_history` | Flyway 마이그레이션 이력 |

## ERD 개요

```text
users 1 ── N user_roles
users 1 ── N refresh_tokens
users 1 ── 0..1 tutor_profiles
refresh_tokens 0..1 ── 0..1 refresh_tokens
```

관계 요약:

- 한 사용자는 여러 role을 가질 수 있다.
- 한 사용자는 여러 refresh token 이력을 가질 수 있다.
- 한 사용자는 최대 하나의 tutor profile만 가질 수 있다.
- refresh token은 회전 시 다음 token id를 `replaced_by_token_id`로 참조할 수 있다.

## users

사용자 인증 계정과 기본 프로필 정보를 저장한다.

| 컬럼 | 타입 | NULL | 키 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | NO | PK | 사용자 ID, auto increment |
| `email` | `VARCHAR(255)` | NO | UNIQUE | 로그인 식별자. 일반 사용자는 이메일, 로컬 관리자 계정은 내부 ID 형식도 허용 |
| `password_hash` | `VARCHAR(100)` | NO |  | BCrypt 비밀번호 해시 |
| `name` | `VARCHAR(100)` | NO |  | 사용자 이름 |
| `mobile_number` | `VARCHAR(30)` | YES |  | 휴대폰 번호 |
| `time_zone` | `VARCHAR(64)` | NO |  | IANA timezone |
| `active_role` | `VARCHAR(20)` | NO |  | 현재 선택한 앱 모드 |
| `account_status` | `VARCHAR(20)` | NO |  | 계정 상태 |
| `created_at` | `TIMESTAMP(6)` | NO |  | 생성 시각 |
| `updated_at` | `TIMESTAMP(6)` | NO |  | 수정 시각 |
| `last_login_at` | `TIMESTAMP(6)` | YES |  | 마지막 로그인 시각 |

제약조건:

| 이름 | 유형 | 컬럼 |
| --- | --- | --- |
| `PRIMARY` | Primary Key | `id` |
| `uk_users_email` | Unique | `email` |

값 규칙:

`active_role`:

```text
STUDENT
TUTOR
ADMIN
```

`account_status`:

```text
ACTIVE
SUSPENDED
DELETED
```

현재 구현 규칙:

- 일반 회원가입 시 `active_role = STUDENT`.
- 일반 회원가입 시 `user_roles`에는 `STUDENT`가 생성된다.
- 로그인 성공 시 `last_login_at`이 갱신된다.
- `active_role`은 사용자가 현재 보고 있는 앱 모드다.
- `active_role = TUTOR`는 승인된 강사라는 의미가 아니다.

## user_roles

사용자가 보유한 role 목록을 저장한다.

| 컬럼 | 타입 | NULL | 키 | 설명 |
| --- | --- | --- | --- | --- |
| `user_id` | `BIGINT` | NO | PK, FK | 사용자 ID |
| `role` | `VARCHAR(20)` | NO | PK | 사용자 role |

제약조건:

| 이름 | 유형 | 컬럼 |
| --- | --- | --- |
| `PRIMARY` | Primary Key | `user_id`, `role` |
| `fk_user_roles_user` | Foreign Key | `user_id -> users.id` |

role 값:

```text
STUDENT
TUTOR
ADMIN
```

현재 구현 규칙:

- 사용자는 여러 role을 동시에 가질 수 있다.
- 튜터 전환 시 `TUTOR` role이 추가된다.
- `PATCH /api/users/me/active-role`은 `user_roles`에 이미 있는 role만 선택할 수 있다.
- `/api/admin/**` API는 `ADMIN` role이 필요하다.

## refresh_tokens

Refresh token 원문은 저장하지 않고 SHA-256 hash만 저장한다.

| 컬럼 | 타입 | NULL | 키 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | NO | PK | refresh token ID, auto increment |
| `user_id` | `BIGINT` | NO | FK, INDEX | 사용자 ID |
| `token_hash` | `VARCHAR(64)` | NO | UNIQUE | refresh token SHA-256 hash |
| `expires_at` | `TIMESTAMP(6)` | NO | INDEX | 만료 시각 |
| `revoked_at` | `TIMESTAMP(6)` | YES |  | 폐기 시각 |
| `replaced_by_token_id` | `BIGINT` | YES | FK | 회전 후 새 refresh token ID |
| `created_at` | `TIMESTAMP(6)` | NO |  | 생성 시각 |

제약조건:

| 이름 | 유형 | 컬럼 |
| --- | --- | --- |
| `PRIMARY` | Primary Key | `id` |
| `uk_refresh_tokens_hash` | Unique | `token_hash` |
| `fk_refresh_tokens_user` | Foreign Key | `user_id -> users.id` |
| `fk_refresh_tokens_replacement` | Foreign Key | `replaced_by_token_id -> refresh_tokens.id` |

인덱스:

| 이름 | 컬럼 | 목적 |
| --- | --- | --- |
| `idx_refresh_tokens_user_id` | `user_id` | 사용자별 token 조회 |
| `idx_refresh_tokens_expires_at` | `expires_at` | 만료 token 조회 |

현재 구현 규칙:

- 로그인, 회원가입, refresh 성공 시 새 refresh token이 발급된다.
- refresh token은 회전 방식이다.
- refresh 성공 시 기존 token은 `revoked_at`이 기록되고 `replaced_by_token_id`가 새 token id를 가리킨다.
- 이미 폐기된 refresh token을 다시 사용하면 `REFRESH_TOKEN_REUSED` 오류가 발생한다.
- 로그아웃 시 전달된 refresh token은 폐기된다.

## tutor_profiles

튜터 활동 정보와 관리자 승인 상태를 저장한다.

| 컬럼 | 타입 | NULL | 키 | 설명 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | NO | PK | 튜터 프로필 ID, auto increment |
| `user_id` | `BIGINT` | NO | UNIQUE, FK | 사용자 ID. 사용자당 하나의 튜터 프로필만 허용 |
| `display_name` | `VARCHAR(100)` | YES |  | 튜터 표시 이름 |
| `short_introduction` | `VARCHAR(255)` | YES |  | 짧은 소개 |
| `about_me` | `TEXT` | YES |  | 자기소개 |
| `what_i_offer` | `TEXT` | YES |  | 제공 수업 설명 |
| `category` | `VARCHAR(50)` | YES |  | 수업 카테고리 |
| `profile_image_url` | `VARCHAR(500)` | YES |  | 프로필 이미지 URL |
| `intro_video_url` | `VARCHAR(500)` | YES |  | 소개 영상 URL |
| `thumbnail_url` | `VARCHAR(500)` | YES |  | 썸네일 URL |
| `available_languages` | `VARCHAR(255)` | YES |  | 가능한 언어 |
| `lesson_price_amount` | `DECIMAL(10,2)` | YES |  | 수업 가격 |
| `available_time_note` | `VARCHAR(500)` | YES |  | 수업 가능 시간 설명 |
| `payment_method` | `VARCHAR(100)` | YES |  | 지급수단 |
| `status` | `VARCHAR(20)` | NO | INDEX | 튜터 프로필 승인 상태 |
| `submitted_at` | `TIMESTAMP(6)` | YES |  | 승인요청 제출 시각 |
| `approved_at` | `TIMESTAMP(6)` | YES |  | 승인 시각 |
| `rejected_at` | `TIMESTAMP(6)` | YES |  | 반려 시각 |
| `created_at` | `TIMESTAMP(6)` | NO |  | 생성 시각 |
| `updated_at` | `TIMESTAMP(6)` | NO |  | 수정 시각 |

제약조건:

| 이름 | 유형 | 컬럼 |
| --- | --- | --- |
| `PRIMARY` | Primary Key | `id` |
| `uk_tutor_profiles_user` | Unique | `user_id` |
| `fk_tutor_profiles_user` | Foreign Key | `user_id -> users.id` |

인덱스:

| 이름 | 컬럼 | 목적 |
| --- | --- | --- |
| `idx_tutor_profiles_status` | `status` | Experts 목록에서 승인된 튜터 조회 |

status 값:

```text
DRAFT
PENDING
APPROVED
REJECTED
```

상태 의미:

| 상태 | 의미 | Experts 노출 |
| --- | --- | --- |
| `DRAFT` | 작성 중 또는 재작성 필요 | 미노출 |
| `PENDING` | 관리자 승인 대기 | 미노출 |
| `APPROVED` | 관리자 승인 완료 | 노출 |
| `REJECTED` | 관리자 반려 | 미노출 |

승인요청 필수값:

```text
display_name
short_introduction
about_me
what_i_offer
category
available_languages
lesson_price_amount
available_time_note
payment_method
```

현재 구현 규칙:

- `POST /api/tutors/me/switch` 호출 시 프로필이 없으면 `DRAFT`로 생성된다.
- `POST /api/tutors/me/switch` 호출 시 사용자에게 `TUTOR` role이 부여되고 `active_role = TUTOR`가 된다.
- 튜터 프로필 저장은 임시저장 성격이며 승인 상태가 자동으로 `APPROVED`가 되지 않는다.
- 필수값을 채운 후 제출하면 `PENDING` 상태가 된다.
- 관리자는 `PENDING` 상태만 승인 또는 반려할 수 있다.
- 승인되면 `APPROVED`가 되고 Experts 목록에 노출된다.
- 승인된 프로필을 수정하면 `DRAFT`로 돌아가고 Experts 목록 노출이 중단된다.
- 반려된 프로필을 수정하면 `DRAFT`로 돌아간다.

## Flyway 마이그레이션 이력

| 버전 | 파일 | 내용 |
| --- | --- | --- |
| V1 | `V1__auth_user_schema.sql` | users, user_roles, refresh_tokens 생성 |
| V2 | `V2__add_user_last_login_at.sql` | users.last_login_at 추가 |
| V3 | `V3__create_tutor_profiles.sql` | tutor_profiles 생성 |

## 로컬 관리자 계정

로컬 seed 기준 관리자 계정:

```text
ID: admin@admin.com
Password: admin1234
Role: ADMIN, STUDENT
activeRole: ADMIN
```

주의:

- seed 파일은 `application-local.yml`의 Flyway location에 포함될 때만 적용된다.
- 이미 DB가 생성된 상태에서 seed 파일만 수정해도 Flyway는 기존 적용 이력을 다시 실행하지 않는다.
- 기존 로컬 DB에 관리자 계정이 없으면 별도 INSERT 또는 DB 초기화가 필요하다.

## 설계 기준

인증 계정과 튜터 활동 정보는 분리한다.

```text
users / user_roles
계정, 인증, 보유 role, 현재 모드

tutor_profiles
튜터 프로필, 승인 상태, Experts 노출 상태
```

`active_role = TUTOR`는 튜터 모드로 진입했다는 뜻이다.

`tutor_profiles.status = APPROVED`만 판매 가능 튜터 및 Experts 노출 기준이다.

이 구분을 유지해야 예약, 결제, 정산, 리뷰 기능을 추가할 때 계정 권한과 판매 가능 상태가 섞이지 않는다.
