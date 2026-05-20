# Backend Architecture Plan

> Historical planning document. 이 문서는 초기 아키텍처 방향을 남긴 기록이며, 현재 구현 상태와 API 계약은 README, Swagger, API_PLANNING.md, DATABASE_SCHEMA.md를 기준으로 확인한다.

## 목표

이 문서는 Spring Boot 백엔드 구현 시 따를 기준 디렉토리와 패키지 분리 방향을 정의했던 초기 계획 문서다. 현재는 프로젝트와 일부 Java 코드가 구현되어 있으므로 현행 상태는 README와 API_PLANNING.md를 기준으로 확인한다.

## 기준 디렉토리 구조

```text
src/main/java/com/haru
  HaruApplication.java
  common
    config
    response
    exception
    security
    util
  auth
    controller
    service
    dto
    entity
    repository
  user
    controller
    service
    dto
    entity
    repository
  tutor
    controller
    service
    dto
    entity
    repository
  schedule
    controller
    service
    dto
    entity
    repository
  booking
    controller
    service
    dto
    entity
    repository
  payment
    controller
    service
    dto
    entity
    repository
  settlement
    controller
    service
    dto
    entity
    repository
  chat
    controller
    service
    dto
    entity
    repository
  notification
    service
    dto
    entity
    repository
  review
    controller
    service
    dto
    entity
    repository
  report
    controller
    service
    dto
    entity
    repository
  admin
    controller
    service
    dto
  video
    controller
    service
    dto
    entity
    repository
```

테스트 구조는 동일한 패키지 흐름을 따른다.

```text
src/test/java/com/haru
  auth
  user
  tutor
  schedule
  booking
  payment
```

## 패키지 분리 방향

### common

- API 공통 응답 형식
- 전역 예외 처리
- Spring Security 설정
- 인증 principal, 권한 검사 유틸
- 날짜/시간 변환 유틸
- 공통 enum 또는 base entity 후보

### auth

- 로그인, 회원가입, 토큰 재발급, 로그아웃
- Refresh Token 저장/폐기
- 소셜 로그인은 인터페이스만 고려하고 실제 연동은 후순위

### user

- 사용자 기본 정보
- 표준 시간대
- 계정 상태
- 역할 보유 여부와 activeRole

### tutor

- 튜터 프로필 등록/수정/조회
- 관리자 승인 상태
- 수업 옵션과 가격
- 가능한 언어, 카테고리
- 지급 수단은 settlement와 경계를 협의

### schedule

- 튜터 가능 시간
- UTC 저장, 사용자 시간대 표시 기준
- 예약 가능 슬롯 조회

### booking

- 예약 생성/변경/취소/완료
- 수업 입장 가능 여부
- Lessons to Schedule와 Balance 정책 후보

### payment

- 결제 요청, 금액 계산, 결제 상태
- 회차권, 할인, 학생 수수료
- 실제 PG 연동은 adapter 계층으로 분리 예정

### settlement

- 튜터 수익 반영
- 인출 요청과 정산 상태
- 플랫폼 수수료, 송금 수수료 정책

### chat/notification

- 학생-튜터 채팅
- 예약 변경/취소/리마인더 알림
- 이메일 발송은 외부 provider adapter로 분리

### admin

- 운영자 조회/승인/상태 변경 API
- 도메인별 쓰기 로직은 각 도메인 service를 호출
- 관리자 전용 aggregate 조회 DTO 제공

## Layer 역할

### Controller

- HTTP 요청/응답 담당
- 인증 사용자와 권한 확인
- Request DTO validation 실행
- 비즈니스 판단은 Service에 위임

### Service

- 도메인 규칙과 트랜잭션 경계 담당
- 예약 가능 여부, 취소 가능 시간, 수익 반영 등 핵심 정책 처리
- 외부 연동은 adapter 또는 client 인터페이스를 통해 호출

### Repository

- Entity 영속화와 조회
- 복잡한 검색은 QueryDSL 또는 Specification 도입 후보
- MVP 초기에는 명확한 단건/목록 조회부터 시작

### Entity

- DB 테이블과 직접 매핑되는 도메인 상태
- 상태 enum을 통해 예약/결제/정산/승인 흐름을 명시
- API 응답에 Entity를 직접 노출하지 않는다.

### DTO

- Request/Response 명세 전용
- 도메인 내부 객체와 외부 API 표현을 분리
- Admin DTO는 운영 화면 요구사항에 맞춘 projection 형태 허용

## 공통 응답 설계 방향

모든 API는 성공/실패 응답 구조를 통일한다.

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

실패 응답은 에러 코드와 사용자 표시 메시지를 분리한다.

```json
{
  "success": false,
  "error": {
    "code": "BOOKING_NOT_CANCELABLE",
    "message": "Lesson can only be cancelled up to 3 hours before start time."
  }
}
```

## 예외 처리 설계 방향

- `BusinessException`: 도메인 정책 위반
- `UnauthorizedException`: 인증 실패
- `ForbiddenException`: 권한 부족
- `NotFoundException`: 리소스 없음
- `ValidationException`: 요청값 검증 실패
- `ExternalProviderException`: 결제, 이메일, Jitsi 등 외부 연동 실패

Global Exception Handler에서 HTTP status와 Haru error code를 일관되게 매핑한다.

## 보안 구조 설계 방향

- Access Token 기반 stateless 인증
- Refresh Token은 서버 저장소에 보관해 폐기 가능하게 설계
- Role은 보유 권한 집합, activeRole은 현재 사용 모드로 구분
- Student 기능은 기본 가입 사용자에게 허용
- Tutor 기능은 TUTOR 역할과 승인 상태를 함께 확인
- Admin 기능은 ADMIN 역할만 허용

## 시간대 설계 방향

- 서버 저장 기준은 UTC
- 사용자 프로필의 `standardTimeZone`을 표시 기준으로 사용
- 예약 가능 시간, 예약 시작/종료 시간, 알림 예약 시간은 UTC로 계산
- 클라이언트에는 사용자 시간대와 변환된 시간을 함께 내려주는 방향을 권장
