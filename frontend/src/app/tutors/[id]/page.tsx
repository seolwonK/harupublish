"use client";

import { CalendarDays, CheckCircle2, Globe2, MapPin, MessageCircle, Play, ShieldCheck, Video } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, use, useEffect, useMemo, useState } from "react";
import { dateRangeFromToday, HaruApiError, haruApi, ReviewListResponse, ScheduleSlotResponse, toMoney, TutorProfileResponse, youtubeEmbedUrl } from "../../api";
import { useAuth } from "../../auth";
import { clearPendingBookingIntent, writePendingBookingIntent } from "../../booking-intent";
import { ApiNotice, AppHeader, Avatar, Badge, Button, categoryLabel, EmptyState, IconMeta, Rating, SectionHeader, TimePill, TutorPortrait } from "../../components";
import { tutors } from "../../data";

function formatDate(value: string) {
  return new Date(value).toLocaleDateString("ko-KR", { month: "long", day: "numeric", weekday: "short" });
}

function formatTime(value: string) {
  return new Date(value).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
}

function TutorProfilePageContent({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const numericId = Number(id);
  const router = useRouter();
  const searchParams = useSearchParams();
  const { accessToken } = useAuth();
  const [tutor, setTutor] = useState<TutorProfileResponse | null>(null);
  const [slots, setSlots] = useState<ScheduleSlotResponse[]>([]);
  const [reviewSummary, setReviewSummary] = useState<ReviewListResponse | null>(null);
  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null);
  const [loading, setLoading] = useState(Number.isFinite(numericId));
  const [actionLoading, setActionLoading] = useState(false);
  const [createdBookingId, setCreatedBookingId] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const mockTutor = tutors[0];
  const scheduleRange = useMemo(() => dateRangeFromToday(30), []);
  const requestedSlotId = useMemo(() => {
    const value = Number(searchParams.get("slot"));
    return Number.isFinite(value) ? value : null;
  }, [searchParams]);

  function preferredSlotIdFrom(nextSlots: ScheduleSlotResponse[], preferredSlotId: number | null) {
    if (preferredSlotId && nextSlots.some((slot) => slot.id === preferredSlotId && !slot.booked)) {
      return preferredSlotId;
    }
    return nextSlots.find((slot) => !slot.booked)?.id ?? null;
  }

  async function refreshSchedule(preferredSlotId: number | null) {
    if (!Number.isFinite(numericId)) {
      return [] as ScheduleSlotResponse[];
    }

    const schedule = await haruApi.getPublicSchedule(numericId, scheduleRange.from, scheduleRange.to);
    setSlots(schedule.slots);
    setSelectedSlotId(preferredSlotIdFrom(schedule.slots, preferredSlotId));
    return schedule.slots;
  }

  useEffect(() => {
    if (!Number.isFinite(numericId)) {
      setLoading(false);
      return;
    }

    Promise.all([haruApi.getTutor(numericId), haruApi.getPublicSchedule(numericId, scheduleRange.from, scheduleRange.to), haruApi.getTutorReviews(numericId)])
      .then(([profile, schedule, reviews]) => {
        setTutor(profile);
        setSlots(schedule.slots);
        setReviewSummary(reviews);
        setSelectedSlotId(preferredSlotIdFrom(schedule.slots, requestedSlotId));
      })
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, [numericId, requestedSlotId, scheduleRange.from, scheduleRange.to]);

  const displayName = tutor?.displayName ?? mockTutor.name;
  const languages = tutor?.availableLanguages?.join(" · ") ?? mockTutor.languages;
  const profileIntro = tutor?.shortIntroduction ?? "목표와 관심사에 맞춰 한국어 회화와 문화를 함께 알려주는 튜터입니다.";
  const profileImageUrl = tutor?.profileImageUrl || tutor?.thumbnailUrl;
  const introVideoEmbedUrl = youtubeEmbedUrl(tutor?.introVideoUrl);
  const averageRating = reviewSummary?.reviewCount ? reviewSummary.averageRating : 0;
  const reviewCount = reviewSummary?.reviewCount ?? 0;
  const selectedSlot = useMemo(() => slots.find((slot) => slot.id === selectedSlotId), [selectedSlotId, slots]);
  const selectedDate = selectedSlot ? formatDate(selectedSlot.startAt) : null;
  const slotDates = useMemo(() => Array.from(new Set(slots.map((slot) => formatDate(slot.startAt)))).slice(0, 6), [slots]);
  const visibleSlots = useMemo(
    () => slots.filter((slot) => !selectedDate || formatDate(slot.startAt) === selectedDate),
    [selectedDate, slots]
  );
  const unitPrice = tutor?.lessonPrice25Amount;

  function loginRedirectTarget() {
    const slotSuffix = selectedSlotId ? `?slot=${selectedSlotId}` : "";
    return `/tutors/${numericId}${slotSuffix}#booking`;
  }

  async function createBooking(token: string, successMessage: string) {
    if (!tutor?.id || !selectedSlotId) {
      throw new Error("예약 가능한 시간을 선택해 주세요.");
    }

    const currentSlotId = selectedSlotId;
    const booking = await haruApi.createBooking(token, {
      tutorProfileId: tutor.id,
      scheduleSlotId: currentSlotId,
      lessonDurationMinutes: 25
    });
    clearPendingBookingIntent();
    setSlots((currentSlots) => currentSlots.map((slot) => (
      slot.id === currentSlotId ? { ...slot, booked: true } : slot
    )));
    setCreatedBookingId(booking.id);
    setMessage(successMessage);
    return booking;
  }

  async function payAndBook() {
    if (!accessToken) {
      router.push(`/login?redirect=${encodeURIComponent(loginRedirectTarget())}`);
      return;
    }
    if (!tutor?.id || !selectedSlotId) {
      setError("예약 가능한 시간을 선택해 주세요.");
      return;
    }

    setError(null);
    setMessage(null);
    setCreatedBookingId(null);
    setActionLoading(true);
    try {
      clearPendingBookingIntent();
      await createBooking(accessToken, "예약이 완료되었습니다. 내 예약에서 일정을 확인할 수 있습니다.");
    } catch (err) {
      const alreadyBooked = err instanceof HaruApiError
        && err.code === "INVALID_REQUEST"
        && err.message.includes("already booked");
      const needsPayment = err instanceof HaruApiError
        && err.code === "INVALID_REQUEST"
        && err.message.includes("Complete payment for this tutor before booking a lesson.");

      if (alreadyBooked) {
        try {
          await refreshSchedule(selectedSlotId);
        } catch {
          // Keep the booking error visible even if the schedule refresh fails.
        }
        setError("방금 다른 수강생이 이 시간을 먼저 예약했습니다. 다른 시간을 선택해 주세요.");
        return;
      }

      if (!needsPayment) {
        setError(err instanceof Error ? err.message : "예약 생성에 실패했습니다.");
        return;
      }

      try {
        const payment = await haruApi.createCheckout(accessToken, {
          tutorProfileId: tutor.id,
          lessonDurationMinutes: 25,
          lessonPackCount: 1,
          paymentMethod: "LEMON_SQUEEZY"
        });

        writePendingBookingIntent({
          tutorProfileId: tutor.id,
          scheduleSlotId: selectedSlotId,
          lessonDurationMinutes: 25,
          paymentId: payment.id,
          createdAt: new Date().toISOString()
        });

        if (payment.status === "PAID") {
          await createBooking(accessToken, "결제가 확인되어 예약이 완료되었습니다. 내 예약에서 일정을 확인할 수 있습니다.");
          return;
        }

        if (payment.checkoutUrl) {
          window.location.href = payment.checkoutUrl;
          return;
        }
        router.push("/payments");
      } catch (paymentError) {
        setError(paymentError instanceof Error ? paymentError.message : "결제 요청 생성에 실패했습니다.");
      }
    } finally {
      setActionLoading(false);
    }
  }

  return (
    <main className="profile-page page-shell compact">
      <AppHeader />
      <section className="profile-hero panel">
        <div className="profile-photo">
          {introVideoEmbedUrl ? (
            <iframe
              className="profile-video-frame"
              src={introVideoEmbedUrl}
              title={`${displayName} 소개 영상`}
              loading="lazy"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
              allowFullScreen
            />
          ) : (
            <>
              <TutorPortrait imageUrl={profileImageUrl} label={displayName.slice(0, 1)} large />
              <span className="play-button disabled" aria-hidden="true">
                <Play size={24} fill="currentColor" />
              </span>
            </>
          )}
        </div>
        <div className="profile-summary">
          <Badge tone="brand">{categoryLabel(tutor?.category)}</Badge>
          <h1>{displayName}</h1>
          <p>{profileIntro}</p>
          <div className="profile-meta-row">
            {reviewCount > 0 ? <Rating value={averageRating} reviews={reviewCount} /> : <span className="muted">아직 리뷰가 없습니다</span>}
            <IconMeta icon={MapPin}>온라인 수업</IconMeta>
            <IconMeta icon={Globe2}>{languages}</IconMeta>
          </div>
          <div className="profile-cta-row">
            <Button as="a" href="#booking">
              <CalendarDays size={16} /> 수업 예약하기
            </Button>
            <Button as="a" href={tutor?.id ? `/chat?tutorId=${tutor.id}&name=${encodeURIComponent(displayName)}` : "/chat"} variant="secondary">
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
          <SectionHeader
            eyebrow="Reviews"
            title="학생 후기"
            action={reviewCount > 0 ? <Rating value={averageRating} reviews={reviewCount} /> : null}
          />
          {reviewCount === 0 ? (
            <EmptyState title="아직 공개된 후기가 없습니다" body="완료한 수업의 학생 후기가 작성되면 이곳에 표시됩니다." />
          ) : (
            <div className="review-list">
              {reviewSummary?.reviews.map((review) => (
                <article className="review" key={review.id}>
                  <Avatar label={review.reviewerName.slice(0, 1)} />
                  <div>
                    <strong>{review.reviewerName}</strong>
                    <Rating value={review.rating} />
                    <p>{review.body}</p>
                    <span>{new Date(review.createdAt).toLocaleDateString("ko-KR")}</span>
                  </div>
                </article>
              ))}
            </div>
          )}
        </div>

        <aside className="booking-panel panel" id="booking">
          <SectionHeader eyebrow="Booking" title="25분 수업 예약" description="남은 결제 회차가 있으면 바로 예약되고, 없으면 Lemon Squeezy 결제창으로 이동합니다." />

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
            {slotDates.map((date) => {
              const dateSlots = slots.filter((slot) => formatDate(slot.startAt) === date);
              const firstAvailableSlot = dateSlots.find((slot) => !slot.booked);

              return (
                <button
                  className={selectedSlot && formatDate(selectedSlot.startAt) === date ? "selected" : ""}
                  key={date}
                  onClick={() => setSelectedSlotId(firstAvailableSlot?.id ?? null)}
                  disabled={!firstAvailableSlot}
                >
                  {date}
                </button>
              );
            })}
          </div>

          <h3>시간 선택 <span>내 현지 시간 기준</span></h3>
          <div className="time-grid">
            {visibleSlots.slice(0, 12).map((slot) => (
              <TimePill
                selected={slot.id === selectedSlotId}
                key={slot.id}
                onClick={() => setSelectedSlotId(slot.id)}
                disabled={slot.booked}
              >
                {formatTime(slot.startAt)}
              </TimePill>
            ))}
          </div>
          {slots.length === 0 ? <EmptyState title="예약 가능한 시간이 없습니다" body="튜터가 공개 일정을 등록하면 예약할 수 있습니다." /> : null}

          <div className="booking-total">
            <span>예상 금액</span>
            <strong>{toMoney(unitPrice)}</strong>
          </div>

          <Button className="wide booking-primary-cta" onClick={() => void payAndBook()} disabled={actionLoading || !selectedSlotId || Boolean(selectedSlot?.booked) || createdBookingId !== null}>
            <CheckCircle2 size={16} /> {actionLoading ? "진행 중..." : "Lemon Squeezy로 결제 및 예약"}
          </Button>

          {createdBookingId ? (
            <Button as="a" href="/bookings" variant="secondary" className="wide">
              내 예약으로 이동
            </Button>
          ) : null}

          <p className="notice">
            <ShieldCheck size={14} /> Lemon Squeezy 결제가 끝나면 결제 내역 화면에서 예약을 자동으로 완료하고, 수업 시작 10분 전부터 내 예약에서 Jitsi 수업방에 입장할 수 있습니다.
          </p>
        </aside>
      </section>
    </main>
  );
}

export default function TutorProfilePage({ params }: { params: Promise<{ id: string }> }) {
  return (
    <Suspense fallback={null}>
      <TutorProfilePageContent params={params} />
    </Suspense>
  );
}
