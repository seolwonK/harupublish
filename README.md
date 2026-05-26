<div align="center">

<img src="docs/images/haru-readme-banner.svg" alt="Haru" width="100%">

# Haru

한국어와 한국 문화를 배우는 학생이 튜터를 찾고, 결제, 예약, 수업 입장, 후기까지 이어갈 수 있는 튜터 매칭 서비스입니다.

`Spring Boot 3.5` · `Java 21` · `MySQL 8` · `Flyway` · `Next.js 15` · `React 19` · `Docker Compose`

</div>

---

## 현재 구현 범위

Haru는 학생, 튜터, 관리자가 같은 서비스 안에서 역할별 기능을 사용할 수 있도록 구성되어 있습니다.

| 영역 | 구현 내용 |
| --- | --- |
| 인증 | 회원가입, 로그인, 로그아웃, JWT access/refresh token, 내 정보 조회 |
| 사용자 | 프로필 조회/수정, active role 변경 |
| 튜터 | 튜터 모드 전환, 레슨 프로필 저장, 이미지 업로드, YouTube 소개 영상 미리보기, 승인 요청 |
| 튜터 센터 | 프로필 작성 진행률, 일정 관리 진입, 공개 시간 요약 |
| 일정 | 튜터 수업 가능 시간 등록/조회, 공개 일정 조회 |
| 결제 | Lemon Squeezy checkout 생성, 결제 상태 조회/동기화, 웹훅, 환불 요청 |
| 예약 | 결제 완료 회차 기반 예약, 내 예약 조회, 취소, 수업방 입장 가능 여부 |
| 후기 | 완료 예약 후기 작성, 튜터 공개 프로필 후기/평점 조회 |
| 관리자 | 승인 대기 튜터 목록, 튜터 승인/반려 |
| 프론트 | 홈, 튜터 목록/상세, 결제, 예약, 튜터센터, 관리자, 채팅 화면 |

## 화면 자료

### 서비스 개요

<img src="docs/images/design-overview.png" alt="Haru service overview" width="100%">

### 홈 화면 컨셉

<img src="docs/images/design-home.png" alt="Haru home concept" width="100%">

### API 테스트 콘솔

<img src="docs/images/test-console.png" alt="API Test Console" width="100%">

### Swagger UI

<img src="docs/images/swagger-ui.png" alt="Swagger UI" width="100%">

## 프로젝트 구조

```text
.
├─ docker-compose.yml
├─ Dockerfile
├─ build.gradle
├─ src
│  ├─ main/java/com/haru
│  │  ├─ auth
│  │  ├─ booking
│  │  ├─ common
│  │  ├─ payment
│  │  ├─ review
│  │  ├─ schedule
│  │  ├─ tutor
│  │  └─ user
│  └─ main/resources
│     ├─ db/migration
│     ├─ db/seed/local
│     └─ static/test-ui.html
├─ frontend
│  └─ src/app
│     ├─ account
│     ├─ admin
│     ├─ bookings
│     ├─ chat
│     ├─ payments
│     ├─ tutor
│     └─ tutors
└─ docs
```

## 사전 준비

필수 설치:

- Docker Desktop
- Node.js 20 이상 권장
- npm

선택 설치:

- Java 21
- Git
- MySQL 클라이언트

백엔드를 Docker로만 실행할 경우 로컬 Java/Gradle 설치는 필수가 아닙니다. Docker 이미지 빌드 단계에서 Gradle wrapper가 사용됩니다.

## 빠른 실행

루트 디렉터리에서 백엔드와 MySQL을 먼저 실행합니다.

```powershell
cd C:\Users\A\Desktop\haru
docker compose up --build -d
```

실행 확인:

```powershell
docker compose ps
docker compose logs -f backend
```

프론트엔드는 별도 터미널에서 실행합니다.

```powershell
cd C:\Users\A\Desktop\haru\frontend
npm install
npm run dev
```

접속 주소:

| 서비스 | 주소 |
| --- | --- |
| 프론트엔드 | http://localhost:3000 |
| 백엔드 API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| API 테스트 콘솔 | http://localhost:8080/test-ui.html |
| MySQL | `localhost:3306` |

## 로컬 계정

Docker Compose는 `local` 프로필과 Flyway local seed를 사용합니다.

관리자 계정:

| 항목 | 값 |
| --- | --- |
| 이메일 | `admin@admin.com` |
| 비밀번호 | `admin1234` |
| 권한 | `ADMIN`, `TUTOR`, `STUDENT` |

튜터 테스트 계정:

| 튜터 | 이메일 | 비밀번호 |
| --- | --- | --- |
| Hana Korean Tutor | `tutor.korean@haru.local` | `admin1234` |
| Minjun K-pop Coach | `tutor.kpop@haru.local` | `admin1234` |
| Sora K-beauty Guide | `tutor.beauty@haru.local` | `admin1234` |
| Yujin Seoul Local | `tutor.travel@haru.local` | `admin1234` |

테스트 튜터는 승인 완료 상태이며, 예약 테스트용 공개 스케줄과 샘플 후기가 seed로 들어갑니다.

## 실행 방법 상세

### 1. 백엔드와 MySQL 실행

```powershell
cd C:\Users\A\Desktop\haru
docker compose up --build -d
```

이 명령은 다음을 수행합니다.

- MySQL 8.4 컨테이너 실행
- Spring Boot 백엔드 이미지 빌드
- Flyway migration 실행
- local seed 적용
- 업로드 파일 저장 볼륨 연결

Compose 포트:

| 컨테이너 | 호스트 포트 | 컨테이너 포트 |
| --- | --- | --- |
| `mysql` | `3306` | `3306` |
| `backend` | `8080` | `8080` |

MySQL 접속 정보:

| 항목 | 값 |
| --- | --- |
| Host | `localhost` |
| Port | `3306` |
| Database | `haru` |
| Username | `haru` |
| Password | `1q2w3e4r!` |
| Root Password | `1q2w3e4r!` |

### 2. 프론트엔드 실행

```powershell
cd C:\Users\A\Desktop\haru\frontend
npm install
npm run dev
```

기본 API 주소는 `http://localhost:8080`입니다. 바꿔야 하면 `frontend/.env.local`을 만들고 아래처럼 설정합니다.

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

### 3. 브라우저에서 확인

1. http://localhost:3000 접속
2. `로그인` 클릭
3. 관리자 또는 튜터 테스트 계정으로 로그인
4. 주요 화면 확인

주요 화면:

| 화면 | 경로 |
| --- | --- |
| 홈 | `/` |
| 튜터 목록 | `/tutors` |
| 튜터 상세 | `/tutors/{id}` |
| 내 예약 | `/bookings` |
| 결제 | `/payments` |
| 튜터 센터 | `/tutor/dashboard` |
| 튜터 프로필 관리 | `/tutor/profile` |
| 튜터 일정 관리 | `/tutor/schedule` |
| 관리자 | `/admin` |
| 튜터 승인 관리 | `/admin/tutors` |

## 자주 쓰는 Docker 명령

상태 확인:

```powershell
docker compose ps
```

백엔드 로그 확인:

```powershell
docker compose logs -f backend
```

MySQL 로그 확인:

```powershell
docker compose logs -f mysql
```

컨테이너 재빌드:

```powershell
docker compose up --build -d
```

컨테이너 중지:

```powershell
docker compose down
```

DB와 업로드 볼륨까지 모두 삭제하고 초기화:

```powershell
docker compose down -v
docker compose up --build -d
```

주의: `docker compose down -v`는 MySQL 데이터와 업로드 파일 볼륨을 삭제합니다.

## 3306 포트 충돌 해결

MySQL이 이미 로컬에서 `3306` 포트를 사용 중이면 Compose 실행 시 포트 바인딩 오류가 납니다.

확인:

```powershell
Get-NetTCPConnection -LocalPort 3306 -State Listen
```

해결 방법:

1. 기존 로컬 MySQL을 중지하고 다시 실행합니다.
2. 또는 `docker-compose.yml`에서 MySQL 포트를 임시로 바꿉니다.

예:

```yaml
ports:
  - "3307:3306"
```

현재 프로젝트 기본값은 `3306:3306`입니다.

## 로컬 백엔드 직접 실행

Docker MySQL만 띄우고 Spring Boot를 로컬에서 직접 실행할 수도 있습니다.

```powershell
cd C:\Users\A\Desktop\haru
docker compose up -d mysql
.\gradlew.bat bootRun
```

로컬 실행 시 기본 `application.yml`은 아래 DB를 바라봅니다.

```text
jdbc:mysql://localhost:3306/haru
username: haru
password: haru
```

Compose MySQL 기본 비밀번호는 `1q2w3e4r!`이므로, 로컬 `bootRun`에서는 환경 변수로 맞춰 실행하는 편이 안전합니다.

```powershell
$env:SPRING_DATASOURCE_USERNAME="haru"
$env:SPRING_DATASOURCE_PASSWORD="1q2w3e4r!"
$env:SPRING_FLYWAY_LOCATIONS="classpath:db/migration,classpath:db/seed/local"
.\gradlew.bat bootRun
```

## 빌드와 테스트

백엔드 테스트:

```powershell
cd C:\Users\A\Desktop\haru
.\gradlew.bat test --no-daemon
```

백엔드 빌드:

```powershell
.\gradlew.bat build --no-daemon
```

프론트엔드 빌드:

```powershell
cd C:\Users\A\Desktop\haru\frontend
npm run build
```

프론트엔드 타입 체크:

```powershell
npm run typecheck
```

## 결제 설정

현재 결제 방식은 Lemon Squeezy를 기준으로 구현되어 있습니다.

개발 환경에서는 `LEMON_SQUEEZY_ENABLED=false`가 기본값입니다. 이 경우 checkout 생성 시 mock paid checkout 설정에 따라 테스트 결제 흐름을 사용할 수 있습니다.

실제 Lemon Squeezy 연동에 필요한 환경 변수:

```env
LEMON_SQUEEZY_ENABLED=true
LEMON_SQUEEZY_API_KEY=...
LEMON_SQUEEZY_STORE_ID=...
LEMON_SQUEEZY_VARIANT_ID=...
LEMON_SQUEEZY_SIGNING_SECRET=...
LEMON_SQUEEZY_REDIRECT_URL=http://localhost:3000/payments
LEMON_SQUEEZY_TEST_MODE=true
```

웹훅 엔드포인트:

```text
POST /api/payments/webhooks/lemon-squeezy
POST /api/payments/webhooks/lemonsqueezy
```

## Jitsi / 수업방 설정

수업방 입장은 예약 상태와 시간 조건을 기준으로 API에서 제어합니다.

JaaS를 실제로 붙일 경우 필요한 환경 변수:

```env
JITSI_JAAS_ENABLED=true
JITSI_JAAS_APP_ID=...
JITSI_JAAS_KEY_ID=...
JITSI_JAAS_PRIVATE_KEY_PEM=...
JITSI_JAAS_DOMAIN=8x8.vc
JITSI_JAAS_TOKEN_TTL_MINUTES=120
```

민감한 Jitsi JWT, private key, 토큰 파일은 저장소에 커밋하지 않습니다.

## Render 배포

이 프로젝트 백엔드는 루트 `Dockerfile` 기준으로 `Render` Web Service에 배포할 수 있습니다.

### 1. Render 서비스 생성

권장 설정:

| 항목 | 값 |
| --- | --- |
| Service type | `Web Service` |
| Runtime | `Docker` |
| Root Directory | `/` |
| Dockerfile Path | `./Dockerfile` |
| Health Check Path | `/swagger-ui.html` |

루트의 `render.yaml`을 사용하면 기본 서비스 설정을 자동으로 불러올 수 있습니다.

### 2. Render 환경변수

필수 환경변수:

```env
SPRING_DATASOURCE_URL=jdbc:mysql://<host>:3306/<database>?useSSL=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password>
HARU_JWT_SECRET=<32-byte-plus-random-secret>
HARU_CORS_ALLOWED_ORIGIN_PATTERNS=https://<frontend-domain>,https://*.vercel.app
```

선택 환경변수:

```env
LEMON_SQUEEZY_ENABLED=true
LEMON_SQUEEZY_API_KEY=...
LEMON_SQUEEZY_STORE_ID=...
LEMON_SQUEEZY_VARIANT_ID=...
LEMON_SQUEEZY_SIGNING_SECRET=...
LEMON_SQUEEZY_REDIRECT_URL=https://<frontend-domain>/payments
LEMON_SQUEEZY_TEST_MODE=true
LEMON_SQUEEZY_CUSTOM_PRICE_EXCHANGE_RATE=1400

JITSI_JAAS_ENABLED=true
JITSI_JAAS_APP_ID=...
JITSI_JAAS_KEY_ID=...
JITSI_JAAS_PRIVATE_KEY_PEM=...
JITSI_JAAS_DOMAIN=8x8.vc
JITSI_JAAS_TOKEN_TTL_MINUTES=120
```

`Render`는 `PORT`를 자동 주입하므로 별도 설정할 필요가 없습니다.

### 3. 데이터베이스 주의사항

이 백엔드는 현재 MySQL 기준으로 migration과 운영 로직이 구성되어 있습니다. 따라서 `Render`에 백엔드만 올릴 경우에도 MySQL 호환 외부 DB를 함께 준비해야 합니다.

또한 업로드 파일은 로컬 디스크 `/app/uploads`를 사용하므로, 인스턴스 재배포 시 파일이 유지되어야 한다면 외부 스토리지 또는 영구 디스크 전략을 추가로 고려해야 합니다.

## 데이터베이스

Flyway가 DB 스키마를 관리합니다.

```text
src/main/resources/db/migration
src/main/resources/db/seed/local
```

주요 테이블:

| 테이블 | 설명 |
| --- | --- |
| `users` | 사용자 계정 |
| `user_roles` | 사용자 권한 |
| `refresh_tokens` | refresh token 저장/회전 |
| `tutor_profiles` | 튜터 프로필, 가격, 승인 상태 |
| `tutor_schedule_slots` | 튜터 공개 가능 시간 |
| `bookings` | 학생-튜터 예약 |
| `payments` | 결제, 환불, provider 상태 |
| `reviews` | 예약 후기와 공개 평점 |
| `flyway_schema_history` | migration 이력 |

현재 주요 migration:

| 파일 | 설명 |
| --- | --- |
| `V1__auth_user_schema.sql` | 사용자, 권한, refresh token |
| `V3__create_tutor_profiles.sql` | 튜터 프로필 |
| `V6__create_tutor_schedule_slots.sql` | 튜터 일정 |
| `V7__create_bookings.sql` | 예약 |
| `V8__create_payments.sql` | 결제 |
| `V9__add_lemon_squeezy_payment_fields.sql` | Lemon Squeezy provider 필드 |
| `V11__create_reviews.sql` | 후기 |
| `R__local_admin_seed.sql` | 로컬 관리자 계정 |
| `R__local_tutor_schedule_seed.sql` | 로컬 튜터/스케줄/후기 데이터 |

## API 요약

| 영역 | 엔드포인트 |
| --- | --- |
| Auth | `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, `GET /api/auth/me` |
| User | `GET /api/users/me`, `PATCH /api/users/me`, `PATCH /api/users/me/active-role` |
| Tutor | `POST /api/tutors/me/switch`, `GET/PUT /api/tutors/me/profile`, `POST /api/tutors/me/profile/submit`, `GET /api/tutors`, `GET /api/tutors/{id}` |
| Schedule | `GET/PUT /api/tutors/me/schedule`, `GET /api/tutors/{id}/schedule` |
| Booking | `POST /api/bookings`, `GET /api/bookings/me`, `GET /api/bookings/{id}`, `PATCH /api/bookings/{id}/cancel`, `POST /api/bookings/{id}/join` |
| Payment | `POST /api/payments/checkout`, `GET /api/payments/me`, `GET /api/payments/{id}`, `POST /api/payments/{id}/refund-request` |
| Review | `POST /api/bookings/{id}/reviews`, `GET /api/tutors/{id}/reviews` |
| Admin | `GET /api/admin/tutors/pending`, `PATCH /api/admin/tutors/{id}/approve`, `PATCH /api/admin/tutors/{id}/reject` |

자세한 요청/응답은 Swagger UI에서 확인합니다.

```text
http://localhost:8080/swagger-ui.html
```

## 개발 메모

- 공개 튜터 목록에는 `APPROVED` 상태만 노출됩니다.
- 튜터 프로필의 이미지, 썸네일, 소개 영상, 정산 수단은 선택 항목입니다.
- 튜터 소개 영상 미리보기는 YouTube URL을 지원합니다.
- 예약은 결제 완료 회차가 있어야 생성할 수 있습니다.
- 후기는 완료된 예약에 대해서만 작성할 수 있습니다.
- 업로드 파일은 Docker volume `haru-uploads`에 저장됩니다.
- DB 초기화가 필요하면 `docker compose down -v` 후 다시 `docker compose up --build -d`를 실행합니다.

## 문제 해결

### 프론트에서 백엔드 연결 실패

확인:

```powershell
docker compose ps
Invoke-RestMethod http://localhost:8080/v3/api-docs
```

프론트 환경 변수:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

### 로그인 계정이 안 맞는 경우

로컬 DB seed가 이전 상태일 수 있습니다. 데이터 초기화 후 다시 실행합니다.

```powershell
docker compose down -v
docker compose up --build -d
```

### 이미지 업로드 후 컨테이너 재생성 시 이미지가 사라지는 경우

현재 Compose는 아래 볼륨을 사용합니다.

```yaml
volumes:
  - haru-uploads:/app/uploads
```

볼륨을 삭제하지 않는 한 업로드 파일은 컨테이너 재생성 후에도 유지됩니다.

## 참고 문서

| 문서 | 설명 |
| --- | --- |
| [docs/DATABASE_SCHEMA.md](docs/DATABASE_SCHEMA.md) | DB 테이블/컬럼 문서 |
| [docs/API_PLANNING.md](docs/API_PLANNING.md) | API 설계와 구현 현황 |
| [docs/TUTOR_ROLE_AND_PROFILE_FLOW.md](docs/TUTOR_ROLE_AND_PROFILE_FLOW.md) | 튜터 역할/프로필 승인 흐름 |
| [docs/IMPLEMENTATION_STATUS_VS_PPT.md](docs/IMPLEMENTATION_STATUS_VS_PPT.md) | 기획안 대비 구현 현황 |
| [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) | 프로젝트 개요 |
| [docs/BACKEND_ARCHITECTURE_PLAN.md](docs/BACKEND_ARCHITECTURE_PLAN.md) | 백엔드 구조 계획 |
| [docs/IMPLEMENTATION_ROADMAP.md](docs/IMPLEMENTATION_ROADMAP.md) | 구현 로드맵 |
