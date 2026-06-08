"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { haruApi } from "./api";

export type DisplayCurrency = "USD" | "KRW";

type CurrencyContextValue = {
  displayCurrency: DisplayCurrency;
  /** USD -> KRW 환율. null이면 환율 미확보(USD 단독 표시). */
  fxRate: number | null;
  fxRateSource: string | null;
  setDisplayCurrency: (currency: DisplayCurrency) => void;
};

const CurrencyContext = createContext<CurrencyContextValue | null>(null);
const STORAGE_KEY = "haru.displayCurrency";

/** KOR 로케일이면 KRW, 그 외에는 USD를 기본값으로 추론한다. */
function detectDefaultCurrency(): DisplayCurrency {
  if (typeof navigator === "undefined") return "USD";
  const locales = [navigator.language, ...(navigator.languages ?? [])].filter(Boolean);
  const isKorean = locales.some((locale) => {
    const lower = locale.toLowerCase();
    return lower === "ko" || lower.startsWith("ko-") || lower.endsWith("-kr");
  });
  return isKorean ? "KRW" : "USD";
}

function readStoredCurrency(): DisplayCurrency | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  return raw === "USD" || raw === "KRW" ? raw : null;
}

export function CurrencyProvider({ children }: { children: React.ReactNode }) {
  const [displayCurrency, setDisplayCurrencyState] = useState<DisplayCurrency>("USD");
  const [fxRate, setFxRate] = useState<number | null>(null);
  const [fxRateSource, setFxRateSource] = useState<string | null>(null);

  // 로케일/저장값 기반 초기 통화 결정 (클라이언트에서만 — SSR 하이드레이션 안전)
  useEffect(() => {
    setDisplayCurrencyState(readStoredCurrency() ?? detectDefaultCurrency());
  }, []);

  // 최신 USD->KRW 환율 로드. 실패 시 USD 단독 표시로 폴백.
  useEffect(() => {
    let cancelled = false;
    haruApi
      .getLatestExchangeRate("USD", "KRW")
      .then((rate) => {
        if (cancelled) return;
        const value = Number(rate?.rate);
        if (Number.isFinite(value) && value > 0) {
          setFxRate(value);
          setFxRateSource(rate?.source ?? null);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setFxRate(null);
          setFxRateSource(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const setDisplayCurrency = useCallback((currency: DisplayCurrency) => {
    setDisplayCurrencyState(currency);
    if (typeof window !== "undefined") {
      window.localStorage.setItem(STORAGE_KEY, currency);
    }
  }, []);

  // 환율이 없으면 KRW를 강제하지 않고 USD로 떨어뜨려 표시 일관성 유지.
  const effectiveCurrency: DisplayCurrency = displayCurrency === "KRW" && fxRate === null ? "USD" : displayCurrency;

  const value = useMemo<CurrencyContextValue>(
    () => ({
      displayCurrency: effectiveCurrency,
      fxRate,
      fxRateSource,
      setDisplayCurrency
    }),
    [effectiveCurrency, fxRate, fxRateSource, setDisplayCurrency]
  );

  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>;
}

export function useCurrency() {
  const context = useContext(CurrencyContext);
  if (!context) {
    throw new Error("useCurrency must be used within CurrencyProvider");
  }
  return context;
}
