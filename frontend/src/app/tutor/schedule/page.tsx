"use client";

import { useEffect, useMemo, useState } from "react";
import { dateRangeFromToday, haruApi, ScheduleSlotResponse } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, BackButton, EmptyState, Field, Sidebar } from "../../components";

const SLOT_MINUTES = 30;
const QUICK_DATE_DAYS = 10;
const QUICK_BLOCKS = [
  { label: "오전", times: ["09:00", "09:30", "10:00", "10:30"] },
  { label: "오후", times: ["13:00", "13:30", "14:00", "14:30"] },
  { label: "저녁", times: ["19:00", "19:30", "20:00", "20:30"] }
];

function roundUpToNextSlot(date: Date) {
  const next = new Date(date);
  next.setSeconds(0, 0);

  const minutes = next.getMinutes();
  const remainder = minutes % SLOT_MINUTES;
  if (remainder !== 0) {
    next.setMinutes(minutes + (SLOT_MINUTES - remainder));
  }

  if (next <= date) {
    next.setMinutes(next.getMinutes() + SLOT_MINUTES);
  }

  return next;
}

function toLocalInputValue(date: Date) {
  const offset = date.getTimezoneOffset() * 60000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function fromLocalInputValue(value: string) {
  return new Date(value);
}

function normalizeSlotValue(value: string) {
  if (!value) return "";
  if (isAlignedLocalValue(value)) {
    return value;
  }
  return toLocalInputValue(roundUpToNextSlot(fromLocalInputValue(value)));
}

function nextSlotInputValue() {
  return toLocalInputValue(roundUpToNextSlot(new Date()));
}

function isAlignedLocalValue(value: string) {
  if (!value) return true;
  const date = fromLocalInputValue(value);
  return date.getSeconds() === 0 && date.getMilliseconds() === 0 && date.getMinutes() % SLOT_MINUTES === 0;
}

function buildTimeOptions() {
  const times: string[] = [];

  for (let hour = 6; hour <= 23; hour += 1) {
    times.push(`${String(hour).padStart(2, "0")}:00`);
    times.push(`${String(hour).padStart(2, "0")}:30`);
  }

  return times;
}

function formatDateChipLabel(value: string) {
  return new Date(`${value}T00:00:00`).toLocaleDateString("ko-KR", {
    month: "short",
    day: "numeric",
    weekday: "short"
  });
}

function formatSelectedDateLabel(value: string) {
  return new Date(`${value}T00:00:00`).toLocaleDateString("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "long"
  });
}

function formatDraftSlotLabel(value: string) {
  return new Date(value).toLocaleString("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function quickDateOptions(baseDate: string) {
  const anchor = new Date(`${baseDate}T00:00:00`);

  return Array.from({ length: QUICK_DATE_DAYS }, (_, index) => {
    const next = new Date(anchor);
    next.setDate(anchor.getDate() + index);
    const value = toLocalInputValue(next).slice(0, 10);
    return {
      value,
      label: formatDateChipLabel(value)
    };
  });
}

function combineDateAndTime(dateValue: string, timeValue: string) {
  return `${dateValue}T${timeValue}`;
}

function slotValueFor(dateValue: string, timeValue: string) {
  return normalizeSlotValue(combineDateAndTime(dateValue, timeValue));
}

function uniqueSortedSlots(values: string[]) {
  return Array.from(new Set(values.filter(Boolean).map((value) => normalizeSlotValue(value)).filter(Boolean))).sort();
}

export default function TutorSchedulePage() {
  const { accessToken } = useAuth();
  const [minSlot, setMinSlot] = useState<string | null>(null);
  const minDate = minSlot?.slice(0, 10) ?? "";
  const [slots, setSlots] = useState<ScheduleSlotResponse[]>([]);
  const [draftSlots, setDraftSlots] = useState<string[]>([]);
  const [selectedDate, setSelectedDate] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const timeOptions = useMemo(() => buildTimeOptions(), []);
  const dateOptions = useMemo(() => (minDate ? quickDateOptions(minDate) : []), [minDate]);
  const normalizedDraftSlots = useMemo(() => uniqueSortedSlots(draftSlots), [draftSlots]);
  const selectedDateSlots = useMemo(
    () => normalizedDraftSlots.filter((slot) => slot.startsWith(selectedDate)),
    [normalizedDraftSlots, selectedDate]
  );

  useEffect(() => {
    const initialMinSlot = nextSlotInputValue();
    setMinSlot(initialMinSlot);
    setSelectedDate((current) => current || initialMinSlot.slice(0, 10));
  }, []);

  useEffect(() => {
    if (!accessToken) return;
    const range = dateRangeFromToday(30);
    haruApi
      .getMySchedule(accessToken, range.from, range.to)
      .then((schedule) => {
        setSlots(schedule.slots);
        if (schedule.slots.length > 0) {
          const nextDraftSlots = schedule.slots.map((slot) => toLocalInputValue(new Date(slot.startAt)));
          setDraftSlots(nextDraftSlots);
          setSelectedDate(nextDraftSlots[0].slice(0, 10));
        }
      })
      .catch((err: Error) => setError(err.message));
  }, [accessToken]);

  function replaceDraftSlots(nextSlots: string[]) {
    setDraftSlots(uniqueSortedSlots(nextSlots));
  }

  function toggleSlot(timeValue: string) {
    if (!selectedDate) return;
    const nextSlot = slotValueFor(selectedDate, timeValue);
    if (!nextSlot) return;

    if (normalizedDraftSlots.includes(nextSlot)) {
      replaceDraftSlots(normalizedDraftSlots.filter((slot) => slot !== nextSlot));
      return;
    }

    replaceDraftSlots([...normalizedDraftSlots, nextSlot]);
  }

  function addQuickBlock(times: string[]) {
    if (!minSlot || !selectedDate) return;
    const nextSlots = times
      .map((time) => slotValueFor(selectedDate, time))
      .filter((value) => value >= minSlot);

    replaceDraftSlots([...normalizedDraftSlots, ...nextSlots]);
    setMessage(`${formatSelectedDateLabel(selectedDate)}에 빠른 시간대를 추가했습니다.`);
  }

  function removeSlotValue(value: string) {
    replaceDraftSlots(normalizedDraftSlots.filter((slot) => slot !== value));
  }

  function clearSelectedDateSlots() {
    replaceDraftSlots(normalizedDraftSlots.filter((slot) => !slot.startsWith(selectedDate)));
  }

  function isSlotSelected(timeValue: string) {
    return selectedDateSlots.includes(slotValueFor(selectedDate, timeValue));
  }

  function isPastTime(timeValue: string) {
    if (!minSlot || !selectedDate) return true;
    return combineDateAndTime(selectedDate, timeValue) < minSlot;
  }

  async function save() {
    if (!accessToken || !minSlot) return;
    setError(null);
    setMessage(null);

    const uniqueSlots = normalizedDraftSlots;

    if (uniqueSlots.length === 0) {
      setError("등록할 수업 가능 시간을 1개 이상 추가해주세요.");
      return;
    }

    setDraftSlots(uniqueSlots);

    try {
      const schedule = await haruApi.replaceMySchedule(
        accessToken,
        uniqueSlots.map((value) => ({ startAt: new Date(value).toISOString() }))
      );
      setSlots(schedule.slots);
      setDraftSlots(schedule.slots.map((slot) => toLocalInputValue(new Date(slot.startAt))));
      setMessage("스케줄을 저장했습니다. 날짜를 고른 뒤 시간 버튼으로 여러 슬롯을 빠르게 추가할 수 있습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "스케줄 저장에 실패했습니다.");
    }
  }

  return (
    <main className="dashboard-layout">
      <Sidebar />
      <section className="dashboard-main">
        <header className="admin-title">
          <div>
            <BackButton />
            <h1>스케줄 관리</h1>
            <p>학생이 예약할 수 있는 시간을 30분 단위로 등록합니다.</p>
          </div>
          <button>30분 단위</button>
        </header>

        {message ? <ApiNotice type="success">{message}</ApiNotice> : null}
        {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
        {!accessToken ? <EmptyState title="로그인이 필요합니다" body="스케줄 관리는 튜터 계정으로 로그인해야 합니다." /> : null}

        <section className="panel schedule-editor">
          <div className="schedule-help">
            <strong>입력 규칙</strong>
            <p>날짜를 먼저 고르고 시간 버튼을 눌러 추가하세요. 같은 버튼을 다시 누르면 해제되고, 저장 시 중복은 자동 제거됩니다.</p>
          </div>

          {!minSlot ? (
            <EmptyState title="시간 선택기를 준비하는 중" body="현재 시간 기준으로 가장 빠른 예약 슬롯을 계산하고 있습니다." />
          ) : (
            <>
              <div className="schedule-builder-grid">
                <section className="schedule-picker-card">
                  <Field label="날짜 선택" hint="빠른 날짜 버튼 또는 직접 날짜 선택을 사용할 수 있습니다.">
                    <input type="date" min={minDate} value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} />
                  </Field>

                  <div className="date-chip-row">
                    {dateOptions.map((dateOption) => (
                      <button
                        className={selectedDate === dateOption.value ? "selected" : ""}
                        key={dateOption.value}
                        type="button"
                        onClick={() => setSelectedDate(dateOption.value)}
                      >
                        {dateOption.label}
                      </button>
                    ))}
                  </div>

                  <div className="schedule-quick-actions">
                    {QUICK_BLOCKS.map((block) => (
                      <button className="ghost-button" key={block.label} type="button" onClick={() => addQuickBlock(block.times)}>
                        {block.label} 2시간 추가
                      </button>
                    ))}
                  </div>
                </section>

                <section className="schedule-picker-card">
                  <div className="schedule-picker-head">
                    <div>
                      <strong>{formatSelectedDateLabel(selectedDate)}</strong>
                      <p>30분 버튼을 눌러 빠르게 추가하거나 해제하세요.</p>
                    </div>
                    <span>{selectedDateSlots.length}개 선택됨</span>
                  </div>

                  <div className="time-grid schedule-time-grid">
                    {timeOptions.map((timeValue) => (
                      <button
                        className={isSlotSelected(timeValue) ? "time-pill selected" : "time-pill"}
                        key={timeValue}
                        type="button"
                        onClick={() => toggleSlot(timeValue)}
                        disabled={isPastTime(timeValue)}
                      >
                        {timeValue}
                      </button>
                    ))}
                  </div>
                </section>
              </div>

              <section className="schedule-selection-list">
                <div className="schedule-selection-head">
                  <div>
                    <strong>저장 예정 시간</strong>
                    <p>선택된 슬롯만 저장됩니다.</p>
                  </div>
                  <button className="ghost-button" type="button" onClick={clearSelectedDateSlots} disabled={selectedDateSlots.length === 0}>
                    선택 날짜 비우기
                  </button>
                </div>

                {normalizedDraftSlots.length === 0 ? (
                  <EmptyState title="아직 선택한 시간이 없습니다" body="날짜를 고르고 시간 버튼을 눌러 예약 가능 시간을 추가해주세요." />
                ) : (
                  normalizedDraftSlots.map((slot) => (
                    <div className="schedule-selection-item" key={slot}>
                      <div>
                        <strong>{formatDraftSlotLabel(slot)}</strong>
                        <p>{isAlignedLocalValue(slot) ? "30분 슬롯으로 정리됨" : "저장 시 다음 슬롯으로 보정됨"}</p>
                      </div>
                      <button className="ghost-button" type="button" onClick={() => removeSlotValue(slot)}>
                        삭제
                      </button>
                    </div>
                  ))
                )}
              </section>
            </>
          )}

          <div className="button-row">
            <button className="primary-button" type="button" onClick={() => void save()} disabled={!minSlot}>
              전체 저장
            </button>
          </div>
        </section>

        <section className="panel schedule-list">
          <h2>등록된 시간</h2>
          {slots.length === 0 ? <EmptyState title="등록된 시간이 없습니다" body="위에서 수업 가능 시간을 추가한 뒤 저장해주세요." /> : null}
          <div className="booking-list">
            {slots.map((slot) => (
              <article className="booking-item" key={slot.id}>
                <div>
                  <strong>{new Date(slot.startAt).toLocaleString("ko-KR")}</strong>
                  <p>{new Date(slot.endAt).toLocaleString("ko-KR")} 종료</p>
                </div>
              </article>
            ))}
          </div>
        </section>
      </section>
    </main>
  );
}
