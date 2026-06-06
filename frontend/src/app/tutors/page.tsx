"use client";

import { ArrowRight, Globe2, Search, Star } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { ExpertListResponse, haruApi, resolveAssetUrl, resolveStudentPrice25, toMoney } from "../api";
import { ApiNotice, AppHeader, Badge, Button, EmptyState, SectionHeader, categoryLabel } from "../components";

const DEFAULT_TUTOR_IMAGE = "/images/default-tutor-profile.png";

function tutorImage(tutor: ExpertListResponse) {
  return resolveAssetUrl(tutor.thumbnailUrl || tutor.profileImageUrl) ?? DEFAULT_TUTOR_IMAGE;
}

function priceLabel(tutor: ExpertListResponse) {
  return `25분 ${toMoney(resolveStudentPrice25(tutor))} / 50분 ${toMoney(tutor.lessonPrice50Amount)}`;
}

export default function TutorsPage() {
  const [tutors, setTutors] = useState<ExpertListResponse[]>([]);
  const [query, setQuery] = useState(() =>
    typeof window === "undefined" ? "" : new URLSearchParams(window.location.search).get("q") ?? ""
  );
  const [category, setCategory] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    haruApi
      .getTutors()
      .then(setTutors)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  const categories = useMemo(() => {
    const values = Array.from(new Set(tutors.map((tutor) => tutor.category).filter(Boolean)));
    return ["ALL", ...values];
  }, [tutors]);

  const filteredTutors = useMemo(() => {
    const normalizedQuery = query.trim().toLowerCase();
    return tutors.filter((tutor) => {
      const matchesCategory = category === "ALL" || tutor.category === category;
      const searchable = [
        tutor.displayName,
        tutor.shortIntroduction,
        tutor.availableLanguages?.join(" "),
        categoryLabel(tutor.category)
      ]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      return matchesCategory && (!normalizedQuery || searchable.includes(normalizedQuery));
    });
  }, [category, query, tutors]);

  return (
    <main className="page-shell tutors-page">
      <AppHeader />

      <section className="tutors-toolbar panel">
        <SectionHeader
          eyebrow="All Tutors"
          title="전체 튜터"
          description="승인된 모든 튜터를 한곳에서 확인하고 원하는 프로필로 이동해 수업을 예약합니다."
          action={
            <Button as="a" href="/" variant="secondary">
              홈으로
            </Button>
          }
        />
        <div className="tutors-filter-row">
          <label className="tutors-search">
            <Search size={18} />
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="튜터 이름, 언어, 소개 검색" />
          </label>
          <div className="tutors-category-tabs" aria-label="카테고리 필터">
            {categories.map((item) => (
              <button className={category === item ? "selected" : ""} key={item} onClick={() => setCategory(item ?? "ALL")}>
                {item === "ALL" ? "전체" : categoryLabel(item)}
              </button>
            ))}
          </div>
        </div>
      </section>

      {error ? <ApiNotice type="error">튜터 목록을 불러오지 못했습니다. {error}</ApiNotice> : null}
      {loading ? <EmptyState title="튜터를 불러오는 중입니다" body="승인된 튜터 목록을 확인하고 있습니다." /> : null}
      {!loading && filteredTutors.length === 0 ? (
        <EmptyState title="조건에 맞는 튜터가 없습니다" body="검색어를 줄이거나 다른 카테고리를 선택해 주세요." />
      ) : null}

      <section className="tutors-list-grid" aria-label="전체 튜터 목록">
        {filteredTutors.map((tutor) => (
          <article className="tutors-list-card" key={tutor.tutorProfileId}>
            <img
              src={tutorImage(tutor)}
              alt={`${tutor.displayName ?? "Haru 튜터"} 프로필`}
              width={220}
              height={220}
              loading="lazy"
              decoding="async"
              onError={(event) => {
                if (event.currentTarget.src !== new URL(DEFAULT_TUTOR_IMAGE, window.location.origin).toString()) {
                  event.currentTarget.src = DEFAULT_TUTOR_IMAGE;
                }
              }}
            />
            <div className="tutors-list-content">
              <div className="tutors-list-head">
                <Badge tone="brand">{categoryLabel(tutor.category)}</Badge>
                <span className="tutors-rating">
                  <Star size={14} fill="currentColor" />
                  {(tutor.reviewCount ?? 0) > 0 ? `${Number(tutor.averageRating ?? 0).toFixed(1)} (${tutor.reviewCount})` : "신규"}
                </span>
              </div>
              <h2>{tutor.displayName ?? "Haru 튜터"}</h2>
              <p>{tutor.shortIntroduction ?? "한국어와 한국 문화를 함께 알려드려요."}</p>
              <div className="tutors-meta-row">
                <span>
                  <Globe2 size={15} />
                  {(tutor.availableLanguages ?? ["Korean"]).join(" · ")}
                </span>
                <strong>{priceLabel(tutor)}</strong>
              </div>
            </div>
            <div className="tutors-card-actions">
              <Button as="a" href={`/tutors/${tutor.tutorProfileId}`} variant="secondary">
                프로필 보기
              </Button>
              <Button as="a" href={`/tutors/${tutor.tutorProfileId}`}>
                예약하기 <ArrowRight size={16} />
              </Button>
            </div>
          </article>
        ))}
      </section>
    </main>
  );
}
