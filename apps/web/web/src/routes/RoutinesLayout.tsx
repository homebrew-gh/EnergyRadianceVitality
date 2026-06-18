import { NavLink, Outlet } from "react-router-dom";

function subNavClass({ isActive }: { isActive: boolean }) {
  return isActive ? "btn-primary text-sm" : "btn-ghost text-sm";
}

export function RoutinesLayout() {
  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-2xl font-bold text-heading">Routines</h2>
        <p className="text-sm text-muted mt-1">
          Build weight, stretch, and cardio routines. Changes publish to your relay and sync to
          ERV on your phone.
        </p>
      </div>
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
