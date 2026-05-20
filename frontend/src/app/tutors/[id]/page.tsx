"use client";

import { CalendarDays, Globe2, MapPin, MessageCircle, Play, ShieldCheck } from "lucide-react";
import { use, useEffect, useMemo, useState } from "react";
import { dateRangeFromToday, haruApi, PaymentMethod, ScheduleSlotResponse, toMoney, TutorProfileResponse } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, Avatar, Badge, Button, categoryLabel, EmptyState, IconMeta, Rating, SectionHeader, TimePill, TutorPortrait } from "../../components";
import { reviews, tutors } from "../../data";

export default function TutorProfilePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const numericId = Number(id);
  const { accessToken } = useAuth();
  const [tutor, setTutor] = useState<TutorProfileResponse | null>(null);
  const [slots, setSlots] = useState<ScheduleSlotResponse[]>([]);
  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null);
  const [duration, setDuration] = useState(25);
  const [lessonPackCount, setLessonPackCount] = useState(1);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>("LEMON_SQUEEZY");
  const [loading, setLoading] = useState(Number.isFinite(numericId));
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
  const profileIntro = tutor?.shortIntroduction ?? "목표에 맞춰 한국어 회화와 한국 문화를 함께 알려드리는 튜터입니다.";
  const profileImageUrl = tutor?.profileImageUrl || tutor?.thumbnailUrl;
  const selectedSlot = useMemo(() => slots.find((slot) => slot.id === selectedSlotId), [selectedSlotId, slots]);
  const unitPrice = duration === 25 ? tutor?.lessonPrice25Amount : tutor?.lessonPrice50Amount;

  async function createCheckout() {
    if (!accessToken) {
      setError("결제 요청을 만들려면 먼저 로그인해주세요.");
      return;
    }
    if (!tutor?.id) {
      setError("튜터 프로필을 먼저 불러와야 합니다.");
      return;
    }

    setError(null);
    setMessage(null);
    try {
      const payment = await haruApi.createCheckout(accessToken, {
        tutorProfileId: tutor.id,
        lessonDurationMinutes: duration,
        lessonPackCount,
        paymentMethod
      });
      if (payment.checkoutUrl) {
        window.location.href = payment.checkoutUrl;
        return;
      }
      setMessage(`결제 요청이 생성되었습니다. 결제 ID: ${payment.id}, 총액: ${toMoney(payment.totalAmount)}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "결제 요청 생성에 실패했습니다.");
    }
  }

  async function createBooking() {
    if (!accessToken) {
      setError("예약하려면 먼저 로그인해주세요.");
      return;
    }
    if (!tutor?.id || !selectedSlotId) {
      setError("예약 가능한 시간을 먼저 선택해주세요.");
      return;
    }

    setError(null);
    setMessage(null);
    try {
      const booking = await haruApi.createBooking(accessToken, {
        tutorProfileId: tutor.id,
        scheduleSlotId: selectedSlotId,
        lessonDurationMinutes: duration
      });
      setMessage(`예약이 생성되었습니다. 예약 ID: ${booking.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "예약 생성에 실패했습니다.");
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
            <Button as="a" href="#booking">예약하기</Button>
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
          {loading ? <EmptyState title="튜터 정보를 불러오는 중" body="프로필과 공개 일정을 조회하고 있습니다." /> : null}

          <SectionHeader eyebrow="About" title="About me" />
          <p>{tutor?.aboutMe ?? "안녕하세요. Haru에서 한국어를 배우는 학생을 위해 목표와 관심사에 맞춘 수업을 준비하고 있습니다."}</p>

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
          <SectionHeader eyebrow="Booking" title="수업 예약" description="수업 길이, 패키지, 시간을 순서대로 선택하세요." />
          <h3>수업 길이</h3>
          <div className="price-options">
            <button className={duration === 25 ? "selected" : ""} onClick={() => setDuration(25)}>
              <span>25분</span>
              <strong>{toMoney(tutor?.lessonPrice25Amount)}</strong>
            </button>
            <button className={duration === 50 ? "selected" : ""} onClick={() => setDuration(50)}>
              <span>50분</span>
              <strong>{toMoney(tutor?.lessonPrice50Amount)}</strong>
            </button>
          </div>

          <h3>패키지</h3>
          <div className="package-grid">
            {[1, 5, 10].map((count) => (
              <button className={lessonPackCount === count ? "selected" : ""} key={count} onClick={() => setLessonPackCount(count)}>
                {count}회
              </button>
            ))}
          </div>

          <h3>결제 수단</h3>
          <select className="date-select" value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value as PaymentMethod)}>
            <option value="LEMON_SQUEEZY">Lemon Squeezy</option>
            <option value="CARD">Card</option>
            <option value="PAYPAL">PayPal</option>
            <option value="SIMPLE_PAY">Simple Pay</option>
          </select>

          <h3>날짜</h3>
          <button className="date-select">
            {selectedSlot ? new Date(selectedSlot.startAt).toLocaleDateString("ko-KR") : "예약 가능한 시간 없음"}
            <CalendarDays size={16} />
          </button>

          <h3>시간 선택 <span>(한국 시간)</span></h3>
          <div className="time-grid">
            {slots.slice(0, 9).map((slot) => (
              <TimePill selected={slot.id === selectedSlotId} key={slot.id} onClick={() => setSelectedSlotId(slot.id)}>
                {new Date(slot.startAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })}
              </TimePill>
            ))}
          </div>
          {slots.length === 0 ? <EmptyState title="등록된 시간이 없습니다" body="튜터가 공개 일정을 등록하면 예약할 수 있습니다." /> : null}

          <div className="booking-total">
            <span>예상 금액</span>
            <strong>{toMoney(unitPrice)} · {lessonPackCount}회</strong>
          </div>
          <Button className="wide" onClick={() => void createBooking()}>
            Book now
          </Button>
          <Button className="wide" variant="secondary" onClick={() => void createCheckout()}>
            Lemon Squeezy 결제하기
          </Button>
          <Button as="a" href="/chat" variant="ghost" className="wide">
            Send message
          </Button>
          <p className="notice">
            <ShieldCheck size={14} /> 예약 생성은 로그인한 학생 계정으로만 가능합니다.
          </p>
        </aside>
      </section>
    </main>
  );
}
