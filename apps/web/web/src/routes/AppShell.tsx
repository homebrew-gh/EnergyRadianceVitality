import { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import clsx from "clsx";
import { useAuth } from "../lib/auth";
import { passphraseIsSet } from "../lib/passphrase";
import { CatalogEditorProvider } from "../lib/catalogEditorData";
import { EquipmentProvider } from "../lib/equipmentData";
import { TrainingHistoryProvider } from "../lib/trainingHistoryData";
import { TrainingProfileProvider } from "../lib/trainingProfileData";
import { TrainingProvider } from "../lib/trainingData";
import { ErvLogo, ErvSunMark } from "../components/ErvLogo";
import { NavIcon, type NavIconName } from "../components/NavIcon";
import { RelayStatus } from "../components/RelayStatus";

type NavItem = { to: string; label: string; icon: NavIconName };
type NavGroup = { title: string; items: NavItem[] };

/** The sidebar, in the order the PC Workout Builder Review's navigation plan lays out. */
export const NAV_GROUPS: NavGroup[] = [
  {
    title: "Train",
    items: [
      { to: "/app/workouts", label: "Workouts", icon: "workouts" },
      { to: "/app/planner", label: "Planner", icon: "planner" },
      { to: "/app/progress", label: "Progress", icon: "progress" },
    ],
  },
  {
    title: "Library",
    items: [
      { to: "/app/routines", label: "Routines", icon: "routines" },
      { to: "/app/exercises", label: "Exercises", icon: "exercises" },
      { to: "/app/equipment", label: "Equipment", icon: "equipment" },
      { to: "/app/media", label: "Media", icon: "media" },
    ],
  },
  {
    title: "You",
    items: [
      { to: "/app/profile", label: "Training Profile", icon: "profile" },
      { to: "/app/settings", label: "Settings", icon: "settings" },
    ],
  },
];

const COLLAPSED_KEY = "erv.sidebarCollapsed";

function readCollapsed(): boolean {
  try {
    return window.localStorage.getItem(COLLAPSED_KEY) === "1";
  } catch {
    return false;
  }
}

function writeCollapsed(collapsed: boolean) {
  try {
    window.localStorage.setItem(COLLAPSED_KEY, collapsed ? "1" : "0");
  } catch {
    // Private windows can refuse storage; the sidebar just opens expanded next time.
  }
}

function sidebarLinkClass(collapsed: boolean) {
  return ({ isActive }: { isActive: boolean }) =>
    clsx(
      "flex items-center gap-3 rounded-xl py-2 text-sm transition",
      collapsed ? "justify-center px-2" : "px-3",
      isActive
        ? "bg-white/20 font-semibold text-white shadow-sm"
        : "text-white/82 hover:bg-white/10 hover:text-white",
    );
}

function Sidebar({
  collapsed,
  onToggleCollapsed,
  onClose,
}: {
  collapsed: boolean;
  /** Absent in the phone-width drawer, which closes instead of folding. */
  onToggleCollapsed?: () => void;
  onClose?: () => void;
}) {
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

  const linkClass = sidebarLinkClass(collapsed);

  return (
    <div className="app-header flex h-full flex-col gap-4 overflow-y-auto px-3 py-4">
      <div className={clsx("flex items-center gap-2", collapsed ? "justify-center" : "justify-between px-1")}>
        {collapsed ? <ErvSunMark size={30} /> : <ErvLogo variant="onDark" size="sm" />}
        {onClose ? (
          <button
            type="button"
            className="rounded-lg p-1.5 text-white/82 hover:bg-white/10 hover:text-white"
            onClick={onClose}
            aria-label="Close menu"
          >
            <NavIcon name="close" />
          </button>
        ) : null}
      </div>

      <nav className="flex flex-1 flex-col gap-4" aria-label="Main">
        {NAV_GROUPS.map((group) => (
          <div key={group.title} className="space-y-1">
            {collapsed ? (
              <div className="mx-2 border-t border-white/20" aria-hidden />
            ) : (
              <div className="px-3 text-xs font-semibold text-white/60">{group.title}</div>
            )}
            {group.items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={linkClass}
                title={collapsed ? item.label : undefined}
                aria-label={collapsed ? item.label : undefined}
              >
                <NavIcon name={item.icon} className="shrink-0" />
                {collapsed ? null : <span className="truncate">{item.label}</span>}
              </NavLink>
            ))}
          </div>
        ))}
      </nav>

      <div className="space-y-1 border-t border-white/20 pt-3">
        {passphraseIsSet(status) ? (
          <button
            type="button"
            className={clsx(linkClass({ isActive: false }), "w-full disabled:opacity-50")}
            onClick={() => void onLock()}
            disabled={locking}
            title={collapsed ? "Lock" : undefined}
            aria-label={collapsed ? "Lock" : undefined}
          >
            <NavIcon name="lock" className="shrink-0" />
            {collapsed ? null : <span>{locking ? "Locking…" : "Lock"}</span>}
          </button>
        ) : null}
        {onToggleCollapsed ? (
          <button
            type="button"
            className={clsx(linkClass({ isActive: false }), "w-full")}
            onClick={onToggleCollapsed}
            title={collapsed ? "Expand sidebar" : undefined}
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
          >
            <NavIcon name={collapsed ? "expand" : "collapse"} className="shrink-0" />
            {collapsed ? null : <span>Collapse</span>}
          </button>
        ) : null}
      </div>
    </div>
  );
}

export function AppShell() {
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    setDrawerOpen(false);
  }, [location.pathname]);

  const toggleCollapsed = () => {
    setCollapsed((was) => {
      writeCollapsed(!was);
      return !was;
    });
  };

  return (
    <TrainingProvider>
      <CatalogEditorProvider>
        <EquipmentProvider>
          <TrainingProfileProvider>
            <TrainingHistoryProvider>
              <div className="flex min-h-full">
                <aside
                  className={clsx(
                    "sticky top-0 hidden h-screen shrink-0 md:block",
                    collapsed ? "w-[4.5rem]" : "w-56",
                  )}
                >
                  <Sidebar collapsed={collapsed} onToggleCollapsed={toggleCollapsed} />
                </aside>

                {drawerOpen ? (
                  <div className="fixed inset-0 z-40 md:hidden">
                    <button
                      type="button"
                      className="absolute inset-0 bg-black/40"
                      aria-label="Close menu"
                      onClick={() => setDrawerOpen(false)}
                    />
                    <aside className="relative h-full w-64 shadow-xl">
                      <Sidebar collapsed={false} onClose={() => setDrawerOpen(false)} />
                    </aside>
                  </div>
                ) : null}

                <div className="flex min-w-0 flex-1 flex-col">
                  <header className="app-header flex items-center gap-3 px-4 py-2 shadow-md md:hidden">
                    <button
                      type="button"
                      className="rounded-lg p-1.5 text-white hover:bg-white/10"
                      onClick={() => setDrawerOpen(true)}
                      aria-label="Open menu"
                    >
                      <NavIcon name="menu" />
                    </button>
                    <ErvLogo variant="onDark" size="sm" />
                  </header>
                  <RelayStatus />
                  <main className="w-full flex-1 px-4 py-5 md:px-6">
                    <Outlet />
                  </main>
                </div>
              </div>
            </TrainingHistoryProvider>
          </TrainingProfileProvider>
        </EquipmentProvider>
      </CatalogEditorProvider>
    </TrainingProvider>
  );
}
