"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { AuthTokenResponse, haruApi, SessionTokens, UserMeResponse } from "./api";

type AuthContextValue = {
  user: UserMeResponse | null;
  tokens: SessionTokens | null;
  loading: boolean;
  accessToken: string | null;
  login: (email: string, password: string) => Promise<void>;
  signup: (body: { email: string; password: string; name: string; timeZone: string }) => Promise<void>;
  logout: () => Promise<void>;
  refreshMe: () => Promise<UserMeResponse | null>;
  /** refresh token으로 access token을 재발급한다. 실패하면 null (세션 만료). */
  refreshSession: () => Promise<string | null>;
  setAuthResult: (result: AuthTokenResponse) => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);
const STORAGE_KEY = "haru.session";

function readStoredSession(): { tokens: SessionTokens; user: UserMeResponse } | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return null;

  try {
    return JSON.parse(raw) as { tokens: SessionTokens; user: UserMeResponse };
  } catch {
    window.localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

function storeSession(result: AuthTokenResponse) {
  window.localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      tokens: {
        accessToken: result.accessToken,
        refreshToken: result.refreshToken
      },
      user: result.user
    })
  );
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserMeResponse | null>(null);
  const [tokens, setTokens] = useState<SessionTokens | null>(null);
  const [loading, setLoading] = useState(true);

  const setAuthResult = useCallback((result: AuthTokenResponse) => {
    const nextTokens = {
      accessToken: result.accessToken,
      refreshToken: result.refreshToken
    };
    setTokens(nextTokens);
    setUser(result.user);
    storeSession(result);
  }, []);

  const refreshMe = useCallback(async () => {
    if (!tokens?.accessToken) return null;
    const nextUser = await haruApi.me(tokens.accessToken);
    setUser(nextUser);
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ tokens, user: nextUser }));
    return nextUser;
  }, [tokens]);

  useEffect(() => {
    const stored = readStoredSession();
    if (!stored) {
      setLoading(false);
      return;
    }

    setTokens(stored.tokens);
    setUser(stored.user);
    haruApi
      .me(stored.tokens.accessToken)
      .then((nextUser) => {
        setUser(nextUser);
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ tokens: stored.tokens, user: nextUser }));
      })
      .catch(() => {
        window.localStorage.removeItem(STORAGE_KEY);
        setTokens(null);
        setUser(null);
      })
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      setAuthResult(await haruApi.login({ email, password }));
    },
    [setAuthResult]
  );

  const signup = useCallback(
    async (body: { email: string; password: string; name: string; timeZone: string }) => {
      setAuthResult(await haruApi.signup(body));
    },
    [setAuthResult]
  );

  const refreshSession = useCallback(async () => {
    if (!tokens?.refreshToken) return null;
    try {
      const result = await haruApi.refresh(tokens.refreshToken);
      setAuthResult(result);
      return result.accessToken;
    } catch {
      return null;
    }
  }, [setAuthResult, tokens]);

  const logout = useCallback(async () => {
    const refreshToken = tokens?.refreshToken;
    window.localStorage.removeItem(STORAGE_KEY);
    setTokens(null);
    setUser(null);
    if (refreshToken) {
      await haruApi.logout(refreshToken).catch(() => undefined);
    }
  }, [tokens]);

  const value = useMemo(
    () => ({
      user,
      tokens,
      loading,
      accessToken: tokens?.accessToken ?? null,
      login,
      signup,
      logout,
      refreshMe,
      refreshSession,
      setAuthResult
    }),
    [loading, login, logout, refreshMe, refreshSession, setAuthResult, signup, tokens, user]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
