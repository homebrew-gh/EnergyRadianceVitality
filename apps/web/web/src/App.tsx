import { Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./lib/auth";
import { SessionWatcher } from "./lib/sessionWatcher";
import { hasRelayConfigured } from "./lib/relayUrl";
import { AppShell } from "./routes/AppShell";
import { CatalogEditorTab } from "./routes/CatalogEditorTab";
import { EquipmentTab } from "./routes/EquipmentTab";
import { MediaGalleryTab } from "./routes/MediaGalleryTab";
import { PlannerTab } from "./routes/PlannerTab";
import { ProfileTab } from "./routes/ProfileTab";
import { ProgressTab } from "./routes/ProgressTab";
import { CardioRoutinesTab } from "./routes/CardioRoutinesTab";
import { RoutinesLayout } from "./routes/RoutinesLayout";
import { StretchRoutinesTab } from "./routes/StretchRoutinesTab";
import { WeightRoutinesTab } from "./routes/WeightRoutinesTab";
import { WorkoutsLibraryPage } from "./routes/WorkoutsLibraryPage";
import { NewWorkoutRedirect, WorkoutEditorPage } from "./routes/WorkoutEditorPage";
import { SettingsTab } from "./routes/SettingsTab";
import { ErvLogo } from "./components/ErvLogo";
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
      <div className="h-full flex flex-col items-center justify-center gap-3 text-muted">
        <ErvLogo variant="onLight" size="lg" showWordmark />
        <span className="text-sm">Loading…</span>
      </div>
    );
  }

  return (
    <>
      <SessionWatcher />
      <Routes>
      <Route path="/setup" element={<SetupRoute />} />
      <Route path="/unlock" element={<UnlockRoute />} />
      <Route path="/app" element={<AppShell />}>
        <Route index element={<Navigate to="workouts" replace />} />
        <Route path="routines" element={<RoutinesLayout />}>
          <Route index element={<Navigate to="weight" replace />} />
          <Route path="weight" element={<WeightRoutinesTab />} />
          <Route path="stretch" element={<StretchRoutinesTab />} />
          <Route path="cardio" element={<CardioRoutinesTab />} />
        </Route>
        <Route path="exercises" element={<CatalogEditorTab />} />
        {/* Old address from before the sidebar; kept so bookmarks still work. */}
        <Route path="catalog" element={<Navigate to="/app/exercises" replace />} />
        <Route path="equipment" element={<EquipmentTab />} />
        <Route path="media" element={<MediaGalleryTab />} />
        <Route path="planner" element={<PlannerTab />} />
        <Route path="profile" element={<ProfileTab />} />
        <Route path="progress" element={<ProgressTab />} />
        <Route path="workouts" element={<WorkoutsLibraryPage />} />
        <Route path="workouts/new" element={<NewWorkoutRedirect />} />
        <Route path="workouts/:workoutId/edit" element={<WorkoutEditorPage />} />
        <Route path="settings" element={<SettingsTab />} />
      </Route>
      <Route path="*" element={<RootRedirect status={status} />} />
    </Routes>
    </>
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
