"use client";

import { ArrowLeft, ExternalLink, Loader2, Video } from "lucide-react";
import { useParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { BookingJoinResponse, haruApi } from "../../../api";
import { useAuth } from "../../../auth";
import { ApiNotice, Button, SectionHeader } from "../../../components";

type JitsiMeetApi = {
  dispose: () => void;
};

type JitsiConstructorOptions = {
  roomName: string;
  jwt?: string;
  parentNode: HTMLElement;
  width: string;
  height: string;
  configOverwrite?: Record<string, unknown>;
  interfaceConfigOverwrite?: Record<string, unknown>;
};

declare global {
  interface Window {
    JitsiMeetExternalAPI?: new (domain: string, options: JitsiConstructorOptions) => JitsiMeetApi;
  }
}

function loadJitsiScript(domain: string, roomName: string) {
  const appId = roomName.includes("/") ? roomName.split("/")[0] : null;
  const src = appId ? `https://${domain}/${appId}/external_api.js` : `https://${domain}/external_api.js`;
  const existing = document.querySelector<HTMLScriptElement>(`script[src="${src}"]`);
  if (existing?.dataset.loaded === "true") {
    return Promise.resolve();
  }

  return new Promise<void>((resolve, reject) => {
    const script = existing ?? document.createElement("script");
    script.src = src;
    script.async = true;
    script.onload = () => {
      script.dataset.loaded = "true";
      resolve();
    };
    script.onerror = () => reject(new Error("Jitsi script could not be loaded."));
    if (!existing) {
      document.body.appendChild(script);
    }
  });
}

export default function ClassroomPage() {
  const params = useParams<{ id: string }>();
  const { accessToken, loading: authLoading } = useAuth();
  const parentRef = useRef<HTMLDivElement | null>(null);
  const apiRef = useRef<JitsiMeetApi | null>(null);
  const [join, setJoin] = useState<BookingJoinResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (authLoading) {
      return;
    }
    if (!accessToken) {
      setLoading(false);
      return;
    }

    const bookingId = Number(params.id);
    if (!Number.isFinite(bookingId)) {
      setError("Invalid booking id.");
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);
    haruApi
      .joinBooking(accessToken, bookingId)
      .then(setJoin)
      .catch((err: Error) => setError(err.message))
      .finally(() => setLoading(false));
  }, [accessToken, authLoading, params.id]);

  useEffect(() => {
    if (!join?.joinAvailable || !join.domain || !join.roomName || !parentRef.current) {
      return;
    }

    let cancelled = false;
    const domain = join.domain;
    const roomName = join.roomName;
    const jwt = join.jwt;

    loadJitsiScript(domain, roomName)
      .then(() => {
        if (cancelled || !parentRef.current || !window.JitsiMeetExternalAPI) return;
        apiRef.current?.dispose();
        parentRef.current.innerHTML = "";
        const options: JitsiConstructorOptions = {
          roomName,
          parentNode: parentRef.current,
          width: "100%",
          height: "100%",
          configOverwrite: {
            prejoinPageEnabled: true
          },
          interfaceConfigOverwrite: {
            MOBILE_APP_PROMO: false
          }
        };
        if (jwt) {
          options.jwt = jwt;
        }
        apiRef.current = new window.JitsiMeetExternalAPI(domain, options);
      })
      .catch((err: Error) => setError(err.message));

    return () => {
      cancelled = true;
      apiRef.current?.dispose();
      apiRef.current = null;
    };
  }, [join]);

  return (
    <main className="classroom-page page-shell compact">
      <section className="classroom-header">
        <Button as="a" href="/bookings" variant="secondary">
          <ArrowLeft size={16} /> Back
        </Button>
        <SectionHeader eyebrow="Jitsi Meet" title="Classroom" description="Join opens 10 minutes before the lesson starts." />
        {join?.joinUrl ? (
          <Button as="a" href={join.joinUrl} variant="ghost">
            <ExternalLink size={16} /> Open
          </Button>
        ) : null}
      </section>

      {!accessToken ? <ApiNotice type="error">Login is required to join this lesson.</ApiNotice> : null}
      {error ? <ApiNotice type="error">{error}</ApiNotice> : null}
      {authLoading || loading ? (
        <section className="classroom-state">
          <Loader2 size={28} className="spin-icon" />
          <p>Preparing classroom...</p>
        </section>
      ) : null}
      {!loading && join && !join.joinAvailable ? (
        <section className="classroom-state">
          <Video size={30} />
          <p>{join.message}</p>
        </section>
      ) : null}
      <section className="classroom-frame" ref={parentRef} aria-label="Jitsi classroom" />
    </main>
  );
}
