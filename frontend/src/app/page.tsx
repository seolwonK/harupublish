"use client";

import {
  ArrowRight,
  BookOpenCheck,
  CalendarCheck,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Heart,
  MessageCircle,
  Search,
  ShieldCheck,
  Sparkles,
  Star,
  WalletCards
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import {
  BookingResponse,
  dateRangeFromToday,
  ExpertListResponse,
  haruApi,
  PaymentResponse,
  resolveAssetUrl,
  ScheduleSlotResponse,
  toMoney
} from "./api";
import { ApiNotice, AppHeader, Badge, Button, EmptyState, SectionHeader, statusLabel } from "./components";
import { useAuth } from "./auth";

const DEFAULT_TUTOR_IMAGE = "/images/default-tutor-profile.png";
const categories = ["한국어 회화", "TOPIK", "발음 교정", "비즈니스", "K-콘텐츠"];

const serviceSteps = [
  {
    icon: Search,
    title: "목표에 맞는 튜터 탐색",
    body: "관심 주제, 언어, 가격을 기준으로 오늘 바로 시작할 수 있는 한국 튜터를 찾습니다."
  },
  {
    icon: CalendarCheck,
    title: "가능한 시간 바로 예약",
    body: "튜터가 공개한 일정에서 내 시간대에 맞는 슬롯을 고르고 예약까지 이어갑니다."
  },
  {
    icon: MessageCircle,
    title: "수업 전후 채팅",
    body: "필요한 자료와 질문을 채팅에서 정리하고 Jitsi Meet 수업 흐름을 놓치지 않습니다."
  }
];

type HomeTutor = {
  id: number;
  name: string;
  tag: string;
  subject: string;
  languages: string;
  price: string;
  imageUrl: string | null;
};

function categoryLabel(category: ExpertListResponse["category"]) {
  const labels = {
    KOREAN: "한국어 회화",
    KPOP: "K-POP 한국어",
    KBEAUTY: "K-뷰티 한국어",
    OTHER: "맞춤 한국 문화"
  };
  return category ? labels[category] : "맞춤 한국어";
}

function formatLessonTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit"
  }).format(new Date(value));
}

function formatSlotTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false
  }).format(new Date(value));
}

function tutorFromExpert(expert: ExpertListResponse): HomeTutor {
  const languages = expert.availableLanguages?.filter(Boolean);
  return {
    id: expert.tutorProfileId,
    name: expert.displayName ?? "Haru 튜터",
    tag: categoryLabel(expert.category),
    subject: expert.shortIntroduction ?? categoryLabel(expert.category),
    languages: languages?.length ? languages.join(" · ") : "언어 정보 업데이트 예정",
    price: `25분 ${toMoney(expert.lessonPrice25Amount)} / 50분 ${toMoney(expert.lessonPrice50Amount)}`,
    imageUrl: expert.thumbnailUrl || expert.profileImageUrl
  };
}

function TutorImage({ src, className = "" }: { src?: string | null; className?: string }) {
  const [imageSrc, setImageSrc] = useState(resolveAssetUrl(src) ?? DEFAULT_TUTOR_IMAGE);

  useEffect(() => {
    setImageSrc(resolveAssetUrl(src) ?? DEFAULT_TUTOR_IMAGE);
  }, [src]);

  return (
    <img
      className={className}
      src={imageSrc}
      alt=""
      onError={() => {
        if (imageSrc !== DEFAULT_TUTOR_IMAGE) setImageSrc(DEFAULT_TUTOR_IMAGE);
      }}
    />
  );
}

function HomeTutorCard({ tutor, featured = false }: { tutor: HomeTutor; featured?: boolean }) {
  return (
    <article className={`home-tutor-card ${featured ? "featured" : ""}`}>
      <div className="home-tutor-card-top">
        <Badge tone={featured ? "orange" : "brand"}>{featured ? "인기" : tutor.tag}</Badge>
        <button aria-label={`${tutor.name} 관심 튜터 저장`}>
          <Heart size={18} />
        </button>
      </div>
      <div className="home-tutor-profile">
        <TutorImage src={tutor.imageUrl} />
        <div>
          <strong>{tutor.name}</strong>
          <p>{tutor.subject}</p>
          <div className="home-rating">
            <Star size={14} fill="currentColor" />
            <span>추천 튜터</span>
          </div>
          <span>{tutor.languages}</span>
        </div>
      </div>
      <div className="home-tutor-price">
        <Clock3 size={14} />
        <strong>{tutor.price}</strong>
      </div>
      <div className="home-tutor-actions">
        <a href={`/tutors/${tutor.id}`}>프로필 보기</a>
        <a className="primary" href={`/tutors/${tutor.id}`}>
          예약하기
        </a>
      </div>
    </article>
  );
}

export default function HomePage() {
  const { accessToken, loading: authLoading } = useAuth();
  const [experts, setExperts] = useState<ExpertListResponse[]>([]);
  const [slots, setSlots] = useState<ScheduleSlotResponse[]>([]);
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [payments, setPayments] = useState<PaymentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [privateLoading, setPrivateLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [privateError, setPrivateError] = useState<string | null>(null);

  useEffect(() => {
    haruApi
      .getTutors()
      .then(setExperts)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const homeTutors = useMemo(() => experts.slice(0, 4).map(tutorFromExpert), [experts]);
  const heroTutor = homeTutors[0] ?? null;

  useEffect(() => {
    if (!heroTutor) {
      setSlots([]);
      return;
    }
    const { from, to } = dateRangeFromToday(14);
    haruApi
      .getPublicSchedule(heroTutor.id, from, to)
      .then((response) => setSlots(response.slots ?? []))
      .catch(() => setSlots([]));
  }, [heroTutor]);

  useEffect(() => {
    if (authLoading) return;
    if (!accessToken) {
      setBookings([]);
      setPayments([]);
      setPrivateLoading(false);
      return;
    }

    setPrivateLoading(true);
    setPrivateError(null);
    Promise.all([haruApi.getMyBookings(accessToken), haruApi.getMyPayments(accessToken)])
      .then(([bookingResponse, paymentResponse]) => {
        setBookings(bookingResponse.bookings ?? []);
        setPayments(paymentResponse.payments ?? []);
      })
      .catch((err: Error) => setPrivateError(err.message))
      .finally(() => setPrivateLoading(false));
  }, [accessToken, authLoading]);

  const upcomingBooking = useMemo(() => {
    const now = Date.now();
    return bookings
      .filter((booking) => booking.status === "RESERVED" && new Date(booking.startAt).getTime() >= now)
      .sort((a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime())[0];
  }, [bookings]);

  const latestPayment = useMemo(
    () => [...payments].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0],
    [payments]
  );

  const firstSlot = slots[0];

  return (
    <main className="home-shell">
      <AppHeader />
      <div className="home-notices">
        {error ? <ApiNotice type="error">튜터 목록을 불러오지 못했습니다. {error}</ApiNotice> : null}
        {privateError ? <ApiNotice type="error">예약/결제 정보를 불러오지 못했습니다. {privateError}</ApiNotice> : null}
      </div>

      <section className="home-hero">
        <div className="home-hero-copy">
          <Badge tone="orange">Live Korean Tutors</Badge>
          <h1>
            오늘의 한국어를
            <br />
            내 하루에 붙이는 <span>1:1 수업</span>
          </h1>
          <p>
            Haru는 한국어와 한국 문화를 배우는 학생이 신뢰할 수 있는 튜터를 찾고, 예약과 결제, Jitsi Meet 수업까지 한 번에 이어가도록 돕습니다.
          </p>
          <div className="hero-search-row">
            <div className="hero-search" role="search">
              <Search size={19} />
              <span>수업 주제, 튜터 이름, 관심사를 검색해보세요</span>
              <a href="#experts">
                튜터 찾기 <ArrowRight size={16} />
              </a>
            </div>
            <a className="secondary-cta" href="#today">
              오늘 할 일
            </a>
          </div>
          <div className="category-row" aria-label="추천 수업 주제">
            {categories.map((category) => (
              <a href="#experts" key={category}>
                {category}
              </a>
            ))}
          </div>
        </div>

        <div className="hero-product" aria-label="Haru 예약 흐름 미리보기">
          <div className="mini-window">
            {heroTutor ? (
              <>
                <div className="preview-profile">
                  <TutorImage src={heroTutor.imageUrl} />
                  <div>
                    <strong>{heroTutor.name}</strong>
                    <p>
                      {heroTutor.subject} · {heroTutor.languages}
                    </p>
                    <div className="home-rating">
                      <Star size={14} fill="currentColor" />
                      <span>실시간 예약 가능</span>
                    </div>
                  </div>
                </div>
                <div className="preview-tags">
                  <span>{heroTutor.tag}</span>
                  <span>{heroTutor.price.split(" / ")[0]}</span>
                  <span>프로필 인증 완료</span>
                </div>
                <div className="schedule-preview">
                  <div className="preview-title-row">
                    <strong>예약 가능한 시간</strong>
                    <span>한국 시간 기준</span>
                  </div>
                  <div className="time-preview-grid">
                    {slots.slice(0, 3).map((slot, index) => (
                      <button className={index === 1 ? "selected" : ""} key={slot.id}>
                        {formatSlotTime(slot.startAt)}
                      </button>
                    ))}
                    {slots.length === 0 ? <button disabled>일정 준비 중</button> : null}
                  </div>
                </div>
                <div className="join-preview">
                  <BookOpenCheck size={22} />
                  <div>
                    <strong>{firstSlot ? formatLessonTime(firstSlot.startAt) : "예약 가능한 시간이 곧 열립니다"}</strong>
                    <span>{firstSlot ? "첫 수업 예약 가능" : "튜터가 일정을 등록하면 예약할 수 있어요"}</span>
                  </div>
                  <ChevronRight size={20} />
                </div>
              </>
            ) : (
              <EmptyState
                title={loading ? "튜터 목록을 불러오는 중입니다" : "등록된 튜터가 없습니다"}
                body={loading ? "백엔드에서 승인된 튜터를 확인하고 있어요." : "튜터 프로필이 승인되면 이 영역에 표시됩니다."}
              />
            )}
          </div>
        </div>
      </section>

      <section className="service-strip" aria-label="서비스 흐름">
        {serviceSteps.map((step) => {
          const Icon = step.icon;
          return (
            <article className="service-step" key={step.title}>
              <span>
                <Icon size={24} />
              </span>
              <div>
                <h2>{step.title}</h2>
                <p>{step.body}</p>
              </div>
            </article>
          );
        })}
      </section>

      <section className="home-action-row" id="today">
        <article className="upcoming-lesson-card">
          <SectionHeader eyebrow="Next Session" title="다가오는 수업" />
          {accessToken && upcomingBooking ? (
            <div className="upcoming-lesson-row">
              <div className="lesson-icon">
                <CheckCircle2 size={24} />
              </div>
              <div>
                <strong>튜터 #{upcomingBooking.tutorProfileId} · {upcomingBooking.lessonDurationMinutes}분 수업</strong>
                <span>{formatLessonTime(upcomingBooking.startAt)} · {upcomingBooking.joinAvailable ? "입장 가능" : "예약 완료"}</span>
              </div>
              <Button as="a" href="/bookings">수업 입장</Button>
              <Button as="a" href="/chat" variant="secondary">메시지</Button>
            </div>
          ) : (
            <div className="upcoming-lesson-row">
              <div className="lesson-icon">
                <Sparkles size={24} />
              </div>
              <div>
                <strong>{privateLoading ? "예약을 불러오는 중입니다" : accessToken ? "예정된 수업이 없습니다" : "로그인하면 다음 수업이 표시됩니다"}</strong>
                <span>{accessToken ? "추천 튜터를 둘러보고 첫 수업을 예약해보세요." : "내 예약과 결제 상태를 로그인 후 확인할 수 있어요."}</span>
              </div>
              <Button as="a" href={accessToken ? "/#experts" : "/login"}>{accessToken ? "튜터 찾기" : "로그인"}</Button>
            </div>
          )}
        </article>
        <a className="payment-status-card" href={accessToken ? "/payments" : "/login"}>
          <WalletCards size={30} />
          <div>
            <Badge tone="blue">Secure Payment</Badge>
            <strong>{latestPayment ? `최근 결제 ${toMoney(latestPayment.totalAmount)}` : accessToken ? "결제 내역이 없습니다" : "예약과 결제 상태 확인"}</strong>
            <span>
              {latestPayment
                ? `${statusLabel(latestPayment.status)} · 총 ${payments.length}건의 결제 내역`
                : accessToken
                  ? "결제가 완료되면 상태와 환불 요청을 여기에서 확인합니다."
                  : "로그인하면 결제 상태를 백엔드에서 불러옵니다."}
            </span>
          </div>
          <ShieldCheck size={20} />
        </a>
      </section>

      <section className="home-experts" id="experts">
        <SectionHeader
          eyebrow="Featured Tutors"
          title="추천 튜터"
          description="승인된 튜터를 실제 데이터로 보여줍니다. 원하는 수업을 고르면 프로필과 예약 화면으로 이어집니다."
          action={<Button as="a" href="/tutor/profile" variant="secondary">튜터로 등록</Button>}
        />
        {homeTutors.length > 0 ? (
          <div className="home-tutor-grid">
            {homeTutors.map((tutor, index) => (
              <HomeTutorCard featured={index === 1} key={tutor.id} tutor={tutor} />
            ))}
          </div>
        ) : (
          <EmptyState
            title={loading ? "튜터를 불러오는 중입니다" : "표시할 튜터가 없습니다"}
            body={loading ? "백엔드 API에서 튜터 목록을 가져오고 있어요." : "승인된 튜터가 등록되면 이 영역이 실제 데이터로 채워집니다."}
          />
        )}
      </section>
    </main>
  );
}
