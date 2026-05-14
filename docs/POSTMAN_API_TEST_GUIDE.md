# Postman API Test Guide

이 문서는 현재 구현된 Auth/User API를 Postman으로 직접 테스트하기 위한 가이드다.

## 1. 실행 상태 준비

Docker MySQL이 3306 포트로 실행 중이어야 한다.

```powershell
docker ps --filter name=haru-mysql
```

Spring Boot 앱을 실행한다.

```powershell
.\gradlew.bat bootRun
```

기본 API 주소는 아래와 같다.

```text
http://localhost:8080
```

## 2. Postman Environment

Postman에서 Environment를 하나 만들고 아래 변수를 추가한다.

| Variable | Initial value | Current value |
| --- | --- | --- |
| `baseUrl` | `http://localhost:8080` | `http://localhost:8080` |
| `accessToken` | 비움 | 비움 |
| `refreshToken` | 비움 | 비움 |
| `oldRefreshToken` | 비움 | 비움 |
| `testEmail` | `postman-test@haru.local` | `postman-test@haru.local` |
| `testPassword` | `Password123!` | `Password123!` |

중복 가입 테스트를 반복하려면 `testEmail` 값을 매번 바꾼다.

## 3. 공통 헤더

JSON 요청에는 아래 헤더를 사용한다.

```text
Content-Type: application/json
```

인증이 필요한 API에는 아래 헤더를 추가한다.

```text
Authorization: Bearer {{accessToken}}
```

## 4. Auth API

### 4.1 회원가입

```text
POST {{baseUrl}}/api/auth/signup
```

Body:

```json
{
  "email": "{{testEmail}}",
  "password": "{{testPassword}}",
  "name": "Postman Test User",
  "timeZone": "Asia/Seoul"
}
```

Expected:

```text
200 OK
```

응답의 `data.accessToken`, `data.refreshToken`을 Postman 변수에 저장한다.

Tests 탭:

```javascript
const json = pm.response.json();

pm.test("signup succeeds", function () {
  pm.response.to.have.status(200);
  pm.expect(json.success).to.eql(true);
  pm.expect(json.data.accessToken).to.be.a("string");
  pm.expect(json.data.refreshToken).to.be.a("string");
});

pm.environment.set("accessToken", json.data.accessToken);
pm.environment.set("refreshToken", json.data.refreshToken);
```

### 4.2 중복 회원가입 실패 확인

4.1과 같은 요청을 한 번 더 보낸다.

Expected:

```text
409 Conflict
```

Expected body:

```json
{
  "success": false,
  "error": {
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "..."
  }
}
```

### 4.3 로그인

```text
POST {{baseUrl}}/api/auth/login
```

Body:

```json
{
  "email": "{{testEmail}}",
  "password": "{{testPassword}}"
}
```

Expected:

```text
200 OK
```

Tests 탭:

```javascript
const json = pm.response.json();

pm.test("login succeeds", function () {
  pm.response.to.have.status(200);
  pm.expect(json.data.accessToken).to.be.a("string");
  pm.expect(json.data.refreshToken).to.be.a("string");
});

pm.environment.set("accessToken", json.data.accessToken);
pm.environment.set("refreshToken", json.data.refreshToken);
```

### 4.4 잘못된 비밀번호 로그인 실패 확인

```text
POST {{baseUrl}}/api/auth/login
```

Body:

```json
{
  "email": "{{testEmail}}",
  "password": "wrong-password"
}
```

Expected:

```text
401 Unauthorized
```

Expected error code:

```text
INVALID_CREDENTIALS
```

### 4.5 내 정보 조회

```text
GET {{baseUrl}}/api/auth/me
```

Headers:

```text
Authorization: Bearer {{accessToken}}
```

Expected:

```text
200 OK
```

Expected body fields:

```text
data.email = {{testEmail}}
data.activeRole = STUDENT
data.roles includes STUDENT
```

### 4.6 토큰 갱신

```text
POST {{baseUrl}}/api/auth/refresh
```

Body:

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

Expected:

```text
200 OK
```

Refresh token은 갱신 시 회전된다. 기존 토큰을 재사용하면 실패해야 하므로, Tests 탭에서 기존 토큰을 `oldRefreshToken`에 저장한다.

Tests 탭:

```javascript
const previousRefreshToken = pm.environment.get("refreshToken");
const json = pm.response.json();

pm.test("refresh succeeds and token rotates", function () {
  pm.response.to.have.status(200);
  pm.expect(json.data.refreshToken).to.be.a("string");
  pm.expect(json.data.refreshToken).to.not.eql(previousRefreshToken);
});

pm.environment.set("oldRefreshToken", previousRefreshToken);
pm.environment.set("accessToken", json.data.accessToken);
pm.environment.set("refreshToken", json.data.refreshToken);
```

### 4.7 기존 Refresh Token 재사용 실패 확인

```text
POST {{baseUrl}}/api/auth/refresh
```

Body:

```json
{
  "refreshToken": "{{oldRefreshToken}}"
}
```

Expected:

```text
401 Unauthorized
```

Expected error code:

```text
REFRESH_TOKEN_REUSED
```

### 4.8 로그아웃

```text
POST {{baseUrl}}/api/auth/logout
```

Headers:

```text
Authorization: Bearer {{accessToken}}
```

Body:

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

Expected:

```text
200 OK
```

Expected body:

```json
{
  "success": true,
  "data": null,
  "message": null
}
```

### 4.9 로그아웃 후 Refresh Token 실패 확인

```text
POST {{baseUrl}}/api/auth/refresh
```

Body:

```json
{
  "refreshToken": "{{refreshToken}}"
}
```

Expected:

```text
401 Unauthorized
```

Expected error code:

```text
REFRESH_TOKEN_REUSED
```

## 5. User API

### 5.1 내 프로필 조회

```text
GET {{baseUrl}}/api/users/me
```

Headers:

```text
Authorization: Bearer {{accessToken}}
```

Expected:

```text
200 OK
```

Expected body fields:

```text
data.email = {{testEmail}}
data.activeRole = STUDENT
data.accountStatus = ACTIVE
```

### 5.2 내 프로필 수정

```text
PATCH {{baseUrl}}/api/users/me
```

Headers:

```text
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

Body:

```json
{
  "name": "Updated Postman User",
  "mobileNumber": "+82 10-1111-2222",
  "timeZone": "America/New_York"
}
```

Expected:

```text
200 OK
```

Expected body fields:

```text
data.name = Updated Postman User
data.mobileNumber = +82 10-1111-2222
data.timeZone = America/New_York
```

### 5.3 잘못된 Time Zone 실패 확인

```text
PATCH {{baseUrl}}/api/users/me
```

Headers:

```text
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

Body:

```json
{
  "name": "Updated Postman User",
  "mobileNumber": null,
  "timeZone": "Not/AZone"
}
```

Expected:

```text
400 Bad Request
```

Expected error code:

```text
INVALID_REQUEST
```

### 5.4 Active Role 변경 실패 확인

현재 회원가입 사용자는 `STUDENT` 역할만 가진다. 따라서 `TUTOR`로 active role을 바꾸면 실패해야 한다.

```text
PATCH {{baseUrl}}/api/users/me/active-role
```

Headers:

```text
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

Body:

```json
{
  "activeRole": "TUTOR"
}
```

Expected:

```text
403 Forbidden
```

Expected error code:

```text
ROLE_NOT_ASSIGNED
```

## 6. 응답 형식

성공 응답:

```json
{
  "success": true,
  "data": {},
  "message": null
}
```

실패 응답:

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Error message"
  }
}
```

## 7. 현재 구현된 API 목록

| Method | Path | 인증 |
| --- | --- | --- |
| POST | `/api/auth/signup` | No |
| POST | `/api/auth/login` | No |
| POST | `/api/auth/refresh` | No |
| POST | `/api/auth/logout` | Yes |
| GET | `/api/auth/me` | Yes |
| GET | `/api/users/me` | Yes |
| PATCH | `/api/users/me` | Yes |
| PATCH | `/api/users/me/active-role` | Yes |

