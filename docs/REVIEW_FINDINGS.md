# Haru 머니 코어 PR(#1) 코드리뷰 결과 (2026-06-07)

> 6차원 적대적 멀티에이전트 리뷰. **43건 지적 → 42건 검증 통과(진짜) / 1건 기각.** 각 항목 실제 file:line 근거.

## ✅ 해결 상태 (2026-06-07)
- **Critical(1) + High(11) 전부 수정 완료.** gradle 55 tests 0 fail / npm 24 routes / 머니 적대적 재점검 PASS.
  - #2 정산 FIFO 슬롯 귀속, #3 잔여조정 반올림(Σ회차==Σdiscounted), #4 환불 비관락, #5 V27 UNIQUE(booking_id,entry_type)+멱등키, #6 provider 실환불 가드, #7 JWT 기본시크릿 fail-fast, #8 웹훅 금액·스토어·멱등 검증, #9 튜터 DTO studentPrice25Amount+카탈로그 통일, #10 프로모 면제 UI, #11 환불승인 큐 API+`/admin/refunds`. critical #1: settleEarnedBooking 통합테스트 신규.
- **잔여 Medium(5)·Low(25)는 미수정** — 아래 목록 유지(다음 라운드). 특히 노쇼 100% 튜터귀속 정책-코드 불일치(Medium)는 **비즈니스 확인 필요**.
- 신규 테스트: JwtTokenProviderTest, SettlementSlotFifoIntegrationTest, PaymentRefundConcurrencyAndWebhookTest, AdminRefundRequestsAndStudentPriceTest + PaymentModel1PricingTest 보강.

---

## 🔴 CRITICAL (1)
1. **정산 적립 핵심 경로(settleEarnedBooking)가 통합테스트에서 한 번도 실행 안 됨** — `MoneyCoreIntegrationTest`의 잔액 시드가 `creditPromoPayback`로 우회. 모델1 `net=gross*0.85`·FIFO gross 매칭이 전부 미검증 → 아래 HIGH 머니 버그들이 잡히지 않은 이유. *(MoneyCoreIntegrationTest.java:267-272)*

## 🟠 HIGH (11)
### 머니 정합 (5)
2. **정산 gross FIFO 미구현** — `resolveGrossUsd`가 회차 소진 추적 없이 `findFirst()`(가장 이른 결제)만 사용 → 동일 튜터에 단가 다른 팩 2개 구매 시 튜터 gross 오귀속. *(SettlementService.java:123-135)*
3. **per-lesson 반올림 누수** — `discounted/pack` HALF_UP 회차합 ≠ 팩 gross(±0.02~0.03/팩). 정산·환불 크레딧에 전파. *(Payment.java:331-334)*
4. **환불 승인 동시성** — 비관락 없이 `unusedTotal` 읽어 다중 결제 동시 승인 시 이중환불. *(PaymentRefundService.java:50-95)*
5. **booking 정산 이중계상** — `settled` 가드가 app 레벨뿐, DB 유니크(booking_id) 부재 → 동시 적립 가능. *(SettlementService.java:71-115 / V19 booking_id 비유니크)*
6. **provider 실환불(webhook) 무가드** — `markRefundedByProvider`가 미사용 회차 무관하게 REFUNDED set, 크레딧 경로와 채널 정합 깨짐. *(PaymentService.java:158-161, Payment.java:223-228)*

### 보안 (2)
7. **JWT 시크릿 약한 하드코딩 기본값** — prod 오버라이드 없음 → 토큰 위조·관리자 사칭. *(application.yml:60)*
8. **Lemon Squeezy 웹훅 금액·스토어 미검증 + PAID 멱등/리플레이 방어 부재** — 금액 위변조·재생 가능. *(PaymentService.java:134-174)*

### 프론트-백 E2E 단절 (3)
9. **학생 10% 마크업이 튜터 목록/홈 카드에 누락** — 상세 페이지와 가격 불일치. *(components.tsx:418, page.tsx:99)*
10. **프로모 면제 grant/revoke API 호출 화면 없음** — E2E 단절. *(api.ts:636-644)*
11. **관리자 환불 승인 approveRefund API 호출 화면 없음** — 크레딧 발급 플로우 단절. *(api.ts:693)*

## 🟡 MEDIUM (5)
- FeePolicy 캐시 무효화 race(커밋 전 무효화 + stale 재적재) *(PlatformSettingsService.java:30-39,53-92)*
- 랜딩 가격이 통화토글 무시 USD 고정(상세와 비일관) *(api.ts toMoney, page.tsx:99,386)*
- 노쇼/late-cancel **정책=100% 튜터귀속인데 코드=net=gross\*0.85** 적립(정책-코드 불일치 의심) + 미검증 *(BookingIntegrationTest.java:211-220)*
- 수수료 소급금지(스냅샷) 요율변경 후 과거결제 불변 시나리오 미검증
- Haru Credit 12개월 만료배치·환불 idempotency 미검증

## 🟢 LOW (25, 버킷)
- **머니 잔여**: 환불 크레딧 반올림차, 만료 30일근사 오차, settle 멱등키(booking_id), 동시성/월정산/만료배치 테스트 공백
- **보안**: CORS vercel/localhost 와일드카드 환경무관, 관리자 머니 메서드 @PreAuthorize 부재, 수수료율 100% 상한 허용
- **DB**: ledger withdrawal_id/payment_id FK 부재, platform_settings 단일활성행 DB 미강제, exchange_rates (base,quote,captured_at) 유니크 부재, V15 promo 날짜 하드코딩
- **프론트/디자인**: 보라 이전 미완(베이스 크림/오렌지 잔존), 50분 가격 노출(MVP 25분), 학생 마크업 FE 하드코딩 10%, 인라인 style 2건, .ui-badge-orange 부조화, 머니 색 하드코딩 중복, 11px 마이크로텍스트/저대비, PAID 라벨 '결제완료'→'지급완료'
- **계약**: PayPal/Payoneer 단일컬럼 공유 round-trip 손실, payments 페이지 결제별 fxRate 스냅샷 대신 전역환율

## 권장 수정 순서
1차(머니 정합): #2 #3 #5 + #4 #6 → 정산/환불 금액 정확성. 동반 테스트(#1) 필수.
2차(보안): #7 #8.
3차(E2E): #9 #10 #11.
4차(정리): MEDIUM/LOW 일괄.
