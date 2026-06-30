import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { CatalogEditorProvider } from "../lib/catalogEditorData";
import { EquipmentProvider } from "../lib/equipmentData";
import { TrainingHistoryProvider } from "../lib/trainingHistoryData";
import { TrainingProfileProvider } from "../lib/trainingProfileData";
import { TrainingProvider } from "../lib/trainingData";
import { ErvLogo } from "../components/ErvLogo";
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
        <EquipmentProvider>
          <TrainingProfileProvider>
            <TrainingHistoryProvider>
          <div className="min-h-full flex flex-col">
          <header className="app-header px-4 py-4 shadow-md">
            <div className="mx-auto flex w-full max-w-6xl items-center justify-between gap-4">
              <ErvLogo variant="onDark" showTagline size="md" />
              <div className="hidden md:block text-sm text-white/82">
                Build on web. Train on Android. Sync through your relay.
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
            </div>
          </header>
          <RelayStatus />
          <main className="flex-1 w-full max-w-6xl mx-auto px-4 py-5">
            <nav className="glass-panel sticky top-3 z-10 mb-5 flex flex-wrap gap-2 rounded-[24px] p-2">
              <NavLink to="/app/workouts" className={navLinkClass}>
                Workout Builder
              </NavLink>
              <NavLink to="/app/planner" className={navLinkClass}>
                Planner
              </NavLink>
              <NavLink to="/app/routines" className={navLinkClass}>
                Routines
              </NavLink>
              <NavLink to="/app/catalog" className={navLinkClass}>
                Catalog
              </NavLink>
              <NavLink to="/app/equipment" className={navLinkClass}>
                Equipment
              </NavLink>
              <NavLink to="/app/media" className={navLinkClass}>
                Media
              </NavLink>
              <NavLink to="/app/profile" className={navLinkClass}>
                Profile
              </NavLink>
              <NavLink to="/app/progress" className={navLinkClass}>
                Progress
              </NavLink>
              <NavLink to="/app/settings" className={navLinkClass}>
                Settings
              </NavLink>
            </nav>
            <Outlet />
          </main>
        </div>
            </TrainingHistoryProvider>
          </TrainingProfileProvider>
        </EquipmentProvider>
      </CatalogEditorProvider>
    </TrainingProvider>
  );
}
