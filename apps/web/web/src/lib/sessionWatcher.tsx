import { useCallback, useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { setUnauthorizedHandler } from "./api";
import { useAuth } from "./auth";

export type UnlockLocationState = {
  reason?: "session-expired";
};

function isAppPath(pathname: string): boolean {
  return pathname === "/app" || pathname.startsWith("/app/");
}

export function SessionWatcher() {
  const { status, loading, refresh } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const redirectToUnlock = useCallback(
    (reason?: UnlockLocationState["reason"]) => {
      if (!isAppPath(location.pathname)) return;
      navigate("/unlock", {
        replace: true,
        state: reason ? { reason } : undefined,
      });
    },
    [location.pathname, navigate],
  );

  useEffect(() => {
    setUnauthorizedHandler(() => {
      void refresh().then((next) => {
        if (next && !next.unlocked) {
          redirectToUnlock("session-expired");
        }
      });
    });
    return () => setUnauthorizedHandler(null);
  }, [refresh, redirectToUnlock]);

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState !== "visible") return;
      void refresh().then((next) => {
        if (next && !next.unlocked) {
          redirectToUnlock("session-expired");
        }
      });
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [refresh, redirectToUnlock]);

  useEffect(() => {
    if (loading || !status || status.unlocked) return;
    redirectToUnlock("session-expired");
  }, [status, loading, redirectToUnlock]);

  return null;
}
