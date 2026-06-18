import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./lib/auth";
import { hasRelayConfigured } from "./lib/relayUrl";
import { AppShell } from "./routes/AppShell";
import { CatalogEditorTab } from "./routes/CatalogEditorTab";
import { CardioRoutinesTab } from "./routes/CardioRoutinesTab";
import { RoutinesLayout } from "./routes/RoutinesLayout";
import { StretchRoutinesTab } from "./routes/StretchRoutinesTab";
import { WeightRoutinesTab } from "./routes/WeightRoutinesTab";
import { SettingsTab } from "./routes/SettingsTab";
import { SetupRoute } from "./routes/SetupRoute";
import { UnlockRoute } from "./routes/UnlockRoute";

export function App() {
  return (
    <AuthProvider>
      <Gate />
    </AuthProvider>
  );
}

function Gate() {
  const { status, loading } = useAuth();

  if (loading) {
    return (
      <div className="h-full flex items-center justify-center text-muted">
        <span className="font-bold text-heading text-lg">ERV</span>
        <span className="ml-2">Loading…</span>
      </div>
    );
  }

  return (
    <Routes>
      <Route path="/setup" element={<SetupRoute />} />
      <Route path="/unlock" element={<UnlockRoute />} />
      <Route path="/app" element={<AppShell />}>
        <Route index element={<Navigate to="routines/weight" replace />} />
        <Route path="routines" element={<RoutinesLayout />}>
          <Route index element={<Navigate to="weight" replace />} />
          <Route path="weight" element={<WeightRoutinesTab />} />
          <Route path="stretch" element={<StretchRoutinesTab />} />
          <Route path="cardio" element={<CardioRoutinesTab />} />
        </Route>
        <Route path="catalog" element={<CatalogEditorTab />} />
        <Route path="settings" element={<SettingsTab />} />
      </Route>
      <Route path="*" element={<RootRedirect status={status} />} />
    </Routes>
  );
}

function RootRedirect({
  status,
}: {
  status: ReturnType<typeof useAuth>["status"];
}) {
  if (!status?.has_state) return <Navigate to="/setup" replace />;
  if (!status.unlocked) return <Navigate to="/unlock" replace />;
  if (!hasRelayConfigured(status)) return <Navigate to="/setup" replace />;
  return <Navigate to="/app" replace />;
}
