# Haru 머니 모델 설계 (다국어 통화 + 수수료 런타임 조정 + 정산 + Haru Credits)

> 상태: **설계 검수 NO-GO → 비즈니스 결정 2건 확정 후 수정→구현.** (작성 2026-06-04)
> 출처: 2026-05-28 중간점검 회의 확정 정책 + 코드 스캔(2026-06-04) + 설계/검수 워크플로우(be-money-architect / fe-money-architect / money-design-critic).

## 0. 확정 정책 (기준값)
- 학생 수수료 **10%** — 차감 X, **수업료에 포함 표시**.
- 튜터 플랫폼 수수료 **15%** — 튜터 수익에서 차감, 완료 후 "사이버 머니(USD)" 즉시 표시.
- 인출 수수료 **3%**(PayPal/Payoneer) / **5%**(국내계좌 포함).
- 회차권 할인 5회 **5%**, 10회 **10%**.
- 초기 프로모: 연말까지 강사 10명 면제(단 인출 5%는 차감 → 별도 페이백).
- Haru Credits: 환불 전용 가상화폐(USD 고정), 현금환불 없음, 계정 12개월 비활성 만료.
- 취소: 3시간 전까지 취소/변경, 3시간 이내·노쇼 = 환불 0 + 수업료 100% 튜터 귀속.
- 통화: 다국어 통화 전환(USD base, KOR 로케일 KRW 표시). MoR: Lemon Squeezy / Dodo Payments.

## 1. ✅ 비즈니스 결정 확정 (2026-06-04)
- **(A) 50분 수업 = MVP 25분만.** Payment.checkout에 `durationMinutes==25` 가드 추가(50분 결제 차단). 50분 가격필드/UI는 다음 라운드. → 정산 매칭이 단일 duration(25)으로 단순화.
- **(B) 수수료 = 모델1(마크업).** 튜터 희망가 F 기준.
  - 계산식(HALF_UP 2자리): `subtotal=F×pack` → `discount=subtotal×discountRate(pack)` → `discounted=subtotal−discount`(=튜터 gross, pack할인 공유) → `studentFee=discounted×studentFeeRate(0.10)` → `total=discounted+studentFee`(학생 결제·표시가, "10% 포함" 단일가 표시).
  - 정산: 튜터 gross(=discounted) → `platformFee=discounted×0.15` → 튜터 net=discounted×0.85. per-lesson=discounted/pack.
  - ⚠️ 가정(확인필요): 회차권 할인은 튜터 gross에서 공유 차감(기존 코드 구조 유지). 플랫폼 흡수로 바꾸려면 별도 결정.
  - 참고: 현 코드는 이미 `total=discounted+studentFee` 구조(모델1) → 학생측은 rate 5%→10% + 단일가 표시만, 신규는 **튜터 15% 정산**.

## 2. 통화 모델
- 진실 원장 = **USD 단일**. KRW는 표시 파생.
- tutor_profiles: `price_currency` + `lesson_price_25/50_usd_amount`(등록시점 환율 USD 스냅샷, 환율변동 격리).
- payments: `display_currency`, `fx_rate_used`, `fx_rate_source`, `fx_captured_at` 스냅샷.
- exchange_rates 캐시 + **공개 GET `/api/exchange-rates/latest?base=USD&quote=KRW`** (검수 지적: 카탈로그 KRW 표시에 필수).
- Haru Credits·튜터 사이버머니·정산 = 전부 USD 액면(표시단에서만 KRW 병기).

## 3. 마이그레이션 (Flyway, V15~)
| Ver | 파일 | 목적 |
|---|---|---|
| V15 | create_platform_settings | 런타임 수수료/정책 싱글톤+이력(append-only) + 기본행 seed |
| V16 | add_currency_and_fee_snapshot_to_payments | 통화/적용요율 스냅샷, student_fee 의미='포함' |
| V17 | add_price_currency_to_tutor_profiles | 가격 입력통화 + USD 스냅샷 |
| V18 | create_exchange_rates | USD→KRW 환율 캐시 |
| V19 | create_tutor_earning_ledger | 튜터 사이버머니 원장(15% 차감 후 적립) |
| V20 | create_withdrawals | 인출요청 + 3/5% 수수료 + 상태머신 |
| V21 | create_monthly_settlements | 월정산 집계 + 상태머신 |
| V22 | create_haru_credits | 학생 환불크레딧 계정/원장 + 12개월 만료 |
| V23 | create_promo_fee_waiver_grants | 초기 강사 10명 면제 매핑 |
| V24 | add_booking_settlement_fields | bookings.completion_state/settled/earning + BookingStatus.NO_SHOW |

> ⚠️ MySQL 전용 DDL(utf8mb4/InnoDB)은 **`db/migration/mysql/`(={vendor}) 분리** 필수. 공통 ALTER는 메인. `ddl-auto=validate`라 누락 시 테스트/부팅 실패.

## 4. 신규 패키지/엔티티
- `com.haru.settings`: PlatformSettings(엔티티) + FeePolicy(주입 VO) + PlatformSettingsService.currentFeePolicy().
- `com.haru.money`: ExchangeRate + FxService(표시 환산).
- `com.haru.settlement`: TutorEarningLedger, Withdrawal, MonthlySettlement, PromoFeeWaiverGrant + SettlementService + @Scheduled SettlementJob.
- `com.haru.credit`: HaruCreditAccount, HaruCreditLedger + CreditService(발급/사용 FIFO/만료배치).

## 5. Payment 리팩토링
- `Payment.java:31-33` static 상수 제거 → `FeePolicy` 주입(`Payment.checkout(..., FeePolicy)`), `PaymentService.checkout:83-90`에서 currentFeePolicy 주입.
- 계산: total = discounted(표시가). 학생수수료는 **더하지 않고** total 내 역산(정보성). 적용요율을 payments에 스냅샷(소급 금지).
- `PaymentResponse`에 **displayCurrency, appliedStudentFeeRate(+fxRateUsed) 추가**(검수 지적: FE 10%포함·통화표시 의존).
- ⚠️ `studentFeeAmount` 의미 '추가'→'포함 역산'으로 변경 → FE 라벨 동시 수정(연결검증).

## 6. 정산 도메인
- 완료 확정: @Scheduled가 end_at 경과 booking을 COMPLETED/NO_SHOW로 영속(멱등: settled=0만), `LESSON_EARNED`(net=gross×0.85) 적립 → 즉시 사이버머니.
- gross 단가 = payment per-unit USD를 (학생,튜터,duration) FIFO 소진으로 booking에 귀속(`ensureRemainingLessonCredit`와 동일 키).
- 노쇼/late-cancel: `NO_SHOW_EARNED`(학생 환불 0). BookingStatus.NO_SHOW 추가, consumed 카운트 보정(CANCELLED만 제외).
- 인출: HOLD(-)로 즉시 차감(이중인출 방지) → REQUESTED→APPROVED→PAID, REJECT 시 REVERSED(+). 잔액 `SELECT FOR UPDATE` 직렬화.
- 월정산: (tutor, year, month) 유니크, OPEN→CLOSED→FINALIZED→PAID. 인출과 독립(집계/리포트용).

## 7. Haru Credits
- 환불=크레딧 발급(REFUND_ISSUED, +). 사용 시 SPENT(-) FIFO. 만료 = **계정 12개월 비활성 단일기준**(검수: lot expires_at 이원화 금지).
- ⚠️ 환불 승인→크레딧 발급 트리거 엔드포인트 신설 필요(예 `POST /api/admin/payments/{id}/refund-approve`). provider 실환불(`markRefundedByProvider`)과 일반취소 크레딧 경로 분기로 이중환불 차단.

## 8. API 계약 (camelCase, ApiResponse<T> 래퍼)
| Method | Path | Auth |
|---|---|---|
| GET/PUT | /api/admin/settings/fees | ADMIN |
| POST/DELETE | /api/admin/tutors/{id}/promo-waiver | ADMIN |
| GET | /api/tutors/me/earnings | TUTOR(본인) |
| POST/GET | /api/tutors/me/withdrawals | TUTOR(본인) |
| GET | /api/tutors/me/settlements | TUTOR(본인) |
| GET | /api/credits/me | 학생 본인 |
| GET | /api/admin/withdrawals | ADMIN |
| PATCH | /api/admin/withdrawals/{id}/approve\|paid\|reject | ADMIN |
| PATCH | /api/admin/settlements/{id}/status | ADMIN |
| GET | /api/exchange-rates/latest | 공개/auth (신규, 검수 보강) |
| POST | /api/admin/payments/{id}/refund-approve | ADMIN (신규, 검수 보강) |

> SecurityConfig: `/api/admin/**`=hasRole(ADMIN) 자동커버. `/api/tutors/me/**`=authenticated(이미 매칭) + **서비스단 TUTOR 롤/소유 검증 추가**.

## 9. ⛔ 검수자 필수 수정 (MUST-FIX, 구현 전 반영)
1. **[치명] 50분 정산 경로** — 결정 (A).
2. **[치명] 학생10%·튜터15% gross 수식** — 결정 (B), `platform_gross_usd` 컬럼 스냅샷.
3. **[높음] MySQL DDL `db/migration/mysql/` 벤더 분리** (validate 부팅 보장).
4. **[높음] LemonSqueezy `LARGE_LOCAL_PRICE_THRESHOLD=1000` 휴리스틱** USD단일원장 기준 재검토(임계 제거/통화 명시).
5. **[높음] 환불=크레딧 E2E 계약 신설** (refund-approve→REFUND_ISSUED).
6. **[높음] PaymentResponse 확장 + FE 라벨 동시수정** (displayCurrency/appliedStudentFeeRate, '포함 수수료').
7. **[중] FX 표시 GET 엔드포인트 신설**.
8. **[중] 노쇼 정밀추적** — 참석/입장 로그 도입 전엔 late-cancel만 100% 귀속, 한계 명시.
9. **[중] @EnableScheduling + SettlementJob/CreditExpiryJob 신설** (없으면 적립/만료 미발생).
10. **[중] 프로모 페이백 원장 기입 규칙+멱등키** (unique(withdrawal_id, PAYBACK)).
11. **[낮] /api/tutors/me/** 서비스단 TUTOR 롤/소유 검증.

## 10. 구현 순서 (검수 반영)
1) V15 settings + FeePolicy → 2) V16 + Payment 리팩토링(+PaymentResponse) + FE 결제표시 동시수정 → 3) V17/V18 통화 + FxService + FX GET → 4) V24 booking 취소/완료 분기 → 5) V19/V21 정산 + SettlementJob → 6) V22/V23 크레딧 + 환불승인 → 7) V20 인출 → 8) API/프론트(admin/settings·withdrawals, tutor/earnings, credits) → 9) E2E 회귀(스냅샷 소급금지 검증).

> 전체 원본 산출물: 세션 워크플로우 `wf_d7fc7cef-4a9` 결과 참조.
