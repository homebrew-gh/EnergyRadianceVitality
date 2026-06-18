import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { CatalogEditorProvider } from "../lib/catalogEditorData";
import { TrainingProvider } from "../lib/trainingData";
import { RelayStatus } from "../components/RelayStatus";

function navLinkClass({ isActive }: { isActive: boolean }) {
  return isActive ? "btn-primary text-sm" : "btn-ghost text-sm";
}

export function AppShell() {
  const { status, lock } = useAuth();
  const navigate = useNavigate();
  const [locking, setLocking] = useState(false);

  const onLock = async () => {
    if (locking) return;
    setLocking(true);
    try {
      await lock();
      navigate("/unlock", { replace: true });
    } finally {
      setLocking(false);
    }
  };

  return (
    <TrainingProvider>
      <CatalogEditorProvider>
        <div className="min-h-full flex flex-col">
          <header className="app-header px-4 py-3 flex items-center justify-between shadow-md">
            <div>
              <p className="text-lg font-bold tracking-tight">ERV</p>
              <p className="text-xs opacity-90">Relay-synced training planner</p>
            </div>
            <div className="flex items-center gap-2">
              {status?.npub ? (
                <span className="text-xs opacity-80 hidden sm:inline font-mono">
                  {status.npub.slice(0, 12)}…
                </span>
              ) : null}
              <NavLink
                to="/app/settings"
                className={({ isActive }) =>
                  isActive
                    ? "btn-primary text-sm"
                    : "btn-ghost text-sm !text-white !border-white/40"
                }
              >
                Settings
              </NavLink>
              <button
                type="button"
                className="btn-ghost text-sm !text-white !border-white/40"
                onClick={() => void onLock()}
                disabled={locking}
              >
                {locking ? "Locking…" : "Lock"}
              </button>
            </div>
          </header>
          <RelayStatus />
          <main className="flex-1 p-4 max-w-5xl mx-auto w-full">
            <nav className="flex flex-wrap gap-2 mb-4">
              <NavLink to="/app/routines" className={navLinkClass}>
                Routines
              </NavLink>
              <NavLink to="/app/catalog" className={navLinkClass}>
                Catalog
              </NavLink>
              <NavLink to="/app/settings" className={navLinkClass}>
                Settings
              </NavLink>
            </nav>
            <Outlet />
          </main>
        </div>
      </CatalogEditorProvider>
    </TrainingProvider>
  );
}
