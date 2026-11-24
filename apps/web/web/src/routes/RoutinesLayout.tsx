import { NavLink, Outlet } from "react-router-dom";
import { PageHeader } from "../components/PageHeader";

function subNavClass({ isActive }: { isActive: boolean }) {
  return isActive ? "btn-primary text-sm" : "btn-ghost text-sm";
}

export function RoutinesLayout() {
  return (
    <div className="space-y-4">
      <PageHeader
        title="Routines"
        description="Build weight, stretch and cardio routines. Publish them and they reach your phone on its next sync."
      />
      <nav className="flex flex-wrap gap-2">
        <NavLink to="/app/routines/weight" className={subNavClass}>
          Weight
        </NavLink>
        <NavLink to="/app/routines/stretch" className={subNavClass}>
          Stretch
        </NavLink>
        <NavLink to="/app/routines/cardio" className={subNavClass}>
          Cardio
        </NavLink>
      </nav>
      <Outlet />
    </div>
  );
}
