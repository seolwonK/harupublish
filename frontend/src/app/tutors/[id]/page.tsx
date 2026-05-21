"use client";

import { CalendarDays, CheckCircle2, Clock, Globe2, MapPin, MessageCircle, Play, ShieldCheck, Video } from "lucide-react";
import { use, useEffect, useMemo, useState } from "react";
import { dateRangeFromToday, haruApi, PaymentMethod, ScheduleSlotResponse, toMoney, TutorProfileResponse } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, Avatar, Badge, Button, categoryLabel, EmptyState, IconMeta, Rating, SectionHeader, TimePill, TutorPortrait } from "../../components";
import { reviews, tutors } from "../../data";

function formatDate(value: string) {
  return new Date(value).toLocaleDateString("ko-KR", { month: "long", day: "numeric", weekday: "short" });
}

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
}

export default function TutorProfilePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const numericId = Number(id);
  const { accessToken } = useAuth();
  const [tutor, setTutor] = useState<TutorProfileResponse | null>(null);
  const [slots, setSlots] = useState<ScheduleSlotResponse[]>([]);
  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null);
  const [lessonPackCount, setLessonPackCount] = useState(1);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("LEMON_SQUEEZY");
  const [loading, setLoading] = useState(Number.isFinite(numericId));
  const [bookingLoading, setBookingLoading] = useState(false);
  const [checkoutLoading, setCheckoutLoading] = useState(false);
  const [createdBookingId, setCreatedBookingId] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const mockTutor = tutors[0];

  useEffect(() => {
    if (!Number.isFinite(numericId)) {
      setLoading(false);
      return;
    }

    const range = dateRangeFromToday(30);
    Promise.all([haruApi.getTutor(numericId), haruApi.getPublicSchedule(numericId, range.from, range.to)])
      .then(([profile, schedule]) => {
        setTutor(profile);
        setSlots(schedule.slots);
        setSelectedSlotId(schedule.slots[0]?.id ?? null);
      })
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, [numericId]);

  const displayName = tutor?.displayName ?? mockTutor.name;
  const languages = tutor?.availableLanguages?.join(" · ") ?? mockTutor.languages;
  const profileIntro = tutor?.shortIntroduction ?? "목표와 관심사에 맞춰 한국어 회화와 문화를 함께 알려주는 튜터입니다.";
  const profileImageUrl = tutor?.profileImageUrl || tutor?.thumbnailUrl;
  const selectedSlot = useMemo(() => slots.find((slot) => slot.id === selectedSlotId), [selectedSlotId, slots]);
  const slotDates = useMemo(() => Array.from(new Set(slots.map((slot) => formatDate(slot.startAt)))).slice(0, 6), [slots]);
  const unitPrice = tutor?.lessonPrice25Amount;

  async function createCheckout() {
    if (!accessToken) {
      setError("결제를 진행하려면 먼저 로그인해 주세요.");
      return;
    }
    if (!tutor?.id) {
      setError("튜터 정보를 먼저 불러와야 합니다.");
      return;
    }

    setError(null);
    setMessage(null);
    setCheckoutLoading(true);
    try {
      const payment = await haruApi.createCheckout(accessToken, {
        tutorProfileId: tutor.id,
        lessonDurationMinutes: 25,
        lessonPackCount,
        paymentMethod
      });
      if (payment.checkoutUrl) {
        window.location.href = payment.checkoutUrl;
        return;
      }
      setMessage(`결제 요청이 생성되었습니다. 결제 ID ${payment.id}, 총액 ${toMoney(payment.totalAmount)}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "결제 요청 생성에 실패했습니다.");
    } finally {
      setCheckoutLoading(false);
    }
  }

  async function createBooking() {
    if (!accessToken) {
      setError("수업을 예약하려면 먼저 로그인해 주세요.");
      return;
    }
    if (!tutor?.id || !selectedSlotId) {
      setError("예약 가능한 시간을 선택해 주세요.");
      return;
    }

    setError(null);
    setMessage(null);
    setBookingLoading(true);
    try {
      const booking = await haruApi.createBooking(accessToken, {
        tutorProfileId: tutor.id,
        scheduleSlotId: selectedSlotId,
        lessonDurationMinutes: 25
      });
      setCreatedBookingId(booking.id);
      setMessage("예약이 완료되었습니다. 내 예약에서 수업방을 열 수 있습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "예약 생성에 실패했습니다.");
    } finally {
      setBookingLoading(false);
    }
  }

  return (
    <main className="profile-page page-shell compact">
      <section className="profile-hero panel">
        <div className="profile-photo">
          <TutorPortrait imageUrl={profileImageUrl} label={displayName.slice(0, 1)} large />
          <button className="play-button" aria-label="소개 영상 재생">
            <Play size={24} fill="currentColor" />
          </button>
        </div>
        <div className="profile-summary">
          <Badge tone="brand">{categoryLabel(tutor?.category)}</Badge>
          <h1>{displayName}</h1>
          <p>{profileIntro}</p>
          <div className="profile-meta-row">
            <Rating value={4.9} reviews={128} />
            <IconMeta icon={MapPin}>온라인 수업</IconMeta>
            <IconMeta icon={Globe2}>{languages}</IconMeta>
          </div>
          <div className="profile-cta-row">
            <Button as="a" href="#booking">
              <CalendarDays size={16} /> 수업 예약하기
            </Button>
            <Button as="a" href="/chat" variant="secondary">
              <MessageCircle size={16} /> 메시지 보내기
            </Button>
          </div>
        </div>
      </section>

      <section className="profile-detail-grid">
        <div className="profile-content panel">
          {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
          {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
          {createdBookingId ? (
            <div className="booking-success-actions">
              <Button as="a" href="/bookings" variant="primary">
                내 예약 보기
              </Button>
              <Button as="a" href={`/bookings/${createdBookingId}/classroom`} variant="secondary">
                <Video size={16} /> 수업방 확인
              </Button>
            </div>
          ) : null}
          {loading ? <EmptyState title="튜터 정보를 불러오는 중" body="프로필과 공개 일정을 확인하고 있습니다." /> : null}

          <SectionHeader eyebrow="About" title="About me" />
          <p>{tutor?.aboutMe ?? "Haru에서 한국어를 배우는 학생을 위해 목표와 관심사에 맞춘 수업을 준비합니다."}</p>

          <hr />
          <SectionHeader eyebrow="Lessons" title="What I Offer" />
          <div className="chip-row">
            {(tutor?.whatIOffer?.split(",") ?? ["한국어 회화", "TOPIK 준비", "발음 교정", "문법", "한국 문화"]).map((item) => (
              <span className="chip" key={item}>
                {item.trim()}
              </span>
            ))}
          </div>

          <hr />
          <SectionHeader eyebrow="Reviews" title="학생 후기" action={<Rating value={4.9} />} />
          <div className="review-list">
            {reviews.map((review) => (
              <article className="review" key={review.name}>
                <Avatar label={review.avatar} />
                <div>
                  <strong>{review.name}</strong>
                  <Rating value={5} />
                  <p>{review.body}</p>
                  <span>{review.date}</span>
                </div>
              </article>
            ))}
          </div>
        </div>

        <aside className="booking-panel panel" id="booking">
          <SectionHeader eyebrow="Booking" title="25분 수업 예약" description="가능한 시간을 고르면 예약 즉시 Jitsi 수업방이 준비됩니다." />

          <div className="booking-step-card">
            <span>1</span>
            <div>
              <strong>수업 길이</strong>
              <p>현재 예약 v1은 25분 수업만 지원합니다.</p>
            </div>
            <Badge tone="green">25분</Badge>
          </div>

          <h3>날짜</h3>
          <div className="date-chip-row">
            {slotDates.map((date) => (
              <button
                className={selectedSlot && formatDate(selectedSlot.startAt) === date ? "selected" : ""}
                key={date}
                onClick={() => setSelectedSlotId(slots.find((slot) => formatDate(slot.startAt) === date)?.id ?? null)}
              >
                {date}
              </button>
            ))}
          </div>

          <h3>시간 선택 <span>내 현지 시간 기준</span></h3>
          <div className="time-grid">
            {slots.slice(0, 12).map((slot) => (
              <TimePill selected={slot.id === selectedSlotId} key={slot.id} onClick={() => setSelectedSlotId(slot.id)}>
                {formatTime(slot.startAt)}
              </TimePill>
            ))}
          </div>
          {slots.length === 0 ? <EmptyState title="예약 가능한 시간이 없습니다" body="튜터가 공개 일정을 등록하면 예약할 수 있습니다." /> : null}

          <div className="booking-total">
            <span>예상 금액</span>
            <strong>{toMoney(unitPrice)}</strong>
          </div>

          <Button className="wide booking-primary-cta" onClick={() => void createBooking()} disabled={bookingLoading || !selectedSlotId}>
            <CheckCircle2 size={16} /> {bookingLoading ? "예약 중..." : "25분 수업 예약하기"}
          </Button>
          {createdBookingId ? (
            <Button as="a" href="/bookings" variant="secondary" className="wide">
              내 예약으로 이동
            </Button>
          ) : null}

          <hr />
          <SectionHeader eyebrow="Payment" title="패키지 결제" description="여러 회차 결제는 결제 페이지로 이어집니다." />
          <div className="package-grid">
            {[1, 5, 10].map((count) => (
              <button className={lessonPackCount === count ? "selected" : ""} key={count} onClick={() => setLessonPackCount(count)}>
                {count}회
              </button>
            ))}
          </div>
          <select className="date-select" value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value as PaymentMethod)}>
            <option value="LEMON_SQUEEZY">Lemon Squeezy</option>
            <option value="CARD">Card</option>
            <option value="PAYPAL">PayPal</option>
            <option value="SIMPLE_PAY">Simple Pay</option>
          </select>
          <Button className="wide" variant="secondary" onClick={() => void createCheckout()} disabled={checkoutLoading}>
            {checkoutLoading ? "결제 준비 중..." : "패키지 결제하기"}
          </Button>
          <p className="notice">
            <ShieldCheck size={14} /> 수업 시작 10분 전부터 내 예약에서 Jitsi 수업방에 입장할 수 있습니다.
          </p>
        </aside>
      </section>
    </main>
  );
}
