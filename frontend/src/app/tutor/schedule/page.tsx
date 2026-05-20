"use client";

import { useEffect, useMemo, useState } from "react";
import { dateRangeFromToday, haruApi, ScheduleSlotResponse } from "../../api";
import { useAuth } from "../../auth";
import { ApiNotice, BackButton, EmptyState, Field, Sidebar } from "../../components";

const SLOT_MINUTES = 30;

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

export default function TutorSchedulePage() {
  const { accessToken } = useAuth();
  const minSlot = useMemo(() => nextSlotInputValue(), []);
  const [slots, setSlots] = useState<ScheduleSlotResponse[]>([]);
  const [draftSlots, setDraftSlots] = useState<string[]>([minSlot]);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    const range = dateRangeFromToday(30);
    haruApi
      .getMySchedule(accessToken, range.from, range.to)
      .then((schedule) => {
        setSlots(schedule.slots);
        if (schedule.slots.length > 0) {
          setDraftSlots(schedule.slots.map((slot) => toLocalInputValue(new Date(slot.startAt))));
        }
      })
      .catch((err: Error) => setError(err.message));
  }, [accessToken]);

  function updateSlot(index: number, value: string) {
    setDraftSlots(draftSlots.map((item, itemIndex) => (itemIndex === index ? value : item)));
  }

  function normalizeSlot(index: number) {
    const value = draftSlots[index];
    if (!value) return;

    const normalized = normalizeSlotValue(value);
    if (normalized !== value) {
      updateSlot(index, normalized);
      setMessage("수업 가능 시간은 30분 단위로 등록됩니다. 입력한 시간이 다음 가능한 슬롯으로 보정되었습니다.");
    }
  }

  function removeSlot(index: number) {
    setDraftSlots(draftSlots.filter((_, itemIndex) => itemIndex !== index));
  }

  async function save() {
    if (!accessToken) return;
    setError(null);
    setMessage(null);

    const normalizedSlots = draftSlots
      .filter(Boolean)
      .map((value) => normalizeSlotValue(value))
      .filter(Boolean);
    const uniqueSlots = Array.from(new Set(normalizedSlots)).sort();

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
      setMessage("스케줄을 저장했습니다. 30분 단위가 아닌 입력값은 저장 전에 자동 보정했습니다.");
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
            <p>예: 13:00, 13:30, 14:00처럼 30분 단위만 저장됩니다. 13:01처럼 입력하면 13:30으로 자동 보정합니다.</p>
          </div>
          {draftSlots.map((slot, index) => (
            <div className="schedule-row-editor" key={`${slot}-${index}`}>
              <Field
                label={`수업 가능 시간 ${index + 1}`}
                hint={isAlignedLocalValue(slot) ? "30분 단위로 맞춰져 있습니다." : "저장 시 다음 30분 슬롯으로 보정됩니다."}
              >
                <input
                  type="datetime-local"
                  min={minSlot}
                  step={SLOT_MINUTES * 60}
                  value={slot}
                  onBlur={() => normalizeSlot(index)}
                  onChange={(event) => updateSlot(index, event.target.value)}
                />
              </Field>
              <button className="ghost-button" type="button" onClick={() => removeSlot(index)} disabled={draftSlots.length === 1}>
                삭제
              </button>
            </div>
          ))}
          <div className="button-row">
            <button className="ghost-button" type="button" onClick={() => setDraftSlots([...draftSlots, nextSlotInputValue()])}>
              시간 추가
            </button>
            <button className="primary-button" type="button" onClick={() => void save()}>
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
