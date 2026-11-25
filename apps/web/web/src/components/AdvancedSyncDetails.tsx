import { CARDIO_ROUTINES_D_TAG } from "../lib/cardioTraining";
import { CARDIO_CATALOG_D_TAG, STRETCH_CATALOG_D_TAG, WEIGHT_CATALOG_D_TAG } from "../lib/catalog";
import { FITNESS_EQUIPMENT_D_TAG } from "../lib/fitnessEquipment";
import { MEDIA_LIBRARY_D_TAG } from "../lib/mediaLibrary";
import { PROGRAMS_MASTER_D_TAG } from "../lib/programTraining";
import { STRETCHING_ROUTINES_D_TAG } from "../lib/stretchTraining";
import { useTrainingHistory } from "../lib/trainingHistoryData";
import { COACH_REVIEW_D_TAG } from "../lib/coachReviewSync";
import { TRAINING_PROFILE_D_TAG } from "../lib/trainingProfile";
import { WEIGHT_EXERCISES_D_TAG, WEIGHT_ROUTINES_D_TAG } from "../lib/weightTraining";
import { WORKOUTS_LIBRARY_D_TAG } from "../lib/workoutTraining";
import { SectionHeader } from "./FieldLabel";
import { SyncHealthDashboard } from "./SyncHealthDashboard";
import { TrainingRelayDiagnostics } from "./TrainingRelayDiagnostics";

/** Which page publishes which relay record. */
const SYNC_TAGS: { page: string; dTag: string }[] = [
  { page: "Workouts", dTag: WORKOUTS_LIBRARY_D_TAG },
  { page: "Planner", dTag: PROGRAMS_MASTER_D_TAG },
  { page: "Weight routines", dTag: WEIGHT_ROUTINES_D_TAG },
  { page: "Stretch routines", dTag: STRETCHING_ROUTINES_D_TAG },
  { page: "Cardio routines", dTag: CARDIO_ROUTINES_D_TAG },
  { page: "Custom weight exercises", dTag: WEIGHT_EXERCISES_D_TAG },
  { page: "Exercise catalog", dTag: WEIGHT_CATALOG_D_TAG },
  { page: "Stretch catalog", dTag: STRETCH_CATALOG_D_TAG },
  { page: "Cardio catalog", dTag: CARDIO_CATALOG_D_TAG },
  { page: "Equipment", dTag: FITNESS_EQUIPMENT_D_TAG },
  { page: "Training Profile", dTag: TRAINING_PROFILE_D_TAG },
  { page: "Media", dTag: MEDIA_LIBRARY_D_TAG },
  { page: "Coach review (Progress)", dTag: COACH_REVIEW_D_TAG },
  { page: "Day logs (from the phone)", dTag: "erv/weight/YYYY-MM-DD, erv/cardio/YYYY-MM-DD" },
];

/**
 * Relay and sync details for troubleshooting. These used to be spread across the pages; they
 * live here so the everyday pages stay in plain words.
 */
export function AdvancedSyncDetails() {
  const { relayMeta, relayRecords, weightLogs, cardioLogs } = useTrainingHistory();

  return (
    <details className="card p-5 space-y-4 [&_summary]:cursor-pointer">
      <summary className="font-semibold text-heading">Advanced</summary>
      <p className="text-sm text-muted">
        Relay and sync details for when something does not show up where you expect it.
      </p>

      <SyncHealthDashboard />

      <section className="space-y-2">
        <SectionHeader>Relay read</SectionHeader>
        <p className="text-sm text-muted">
          {relayMeta
            ? `${relayMeta.relay_events_fetched} events fetched (limit ${relayMeta.relay_fetch_limit}), ${
                weightLogs.length + cardioLogs.length
              } day logs readable.${
                relayMeta.relay_fetch_possibly_truncated ? " The relay may have cut the list short." : ""
              }`
            : "Not loaded yet."}
        </p>
      </section>

      <TrainingRelayDiagnostics records={relayRecords} />

      <section className="space-y-2">
        <SectionHeader>Sync tags</SectionHeader>
        <p className="text-sm text-muted">The relay record each page reads and publishes.</p>
        <table className="w-full text-sm">
          <tbody>
            {SYNC_TAGS.map((row) => (
              <tr key={row.dTag} className="border-t border-[var(--erv-outline-variant)]">
                <td className="py-1.5 pr-3 text-muted">{row.page}</td>
                <td className="py-1.5 font-mono text-xs break-all">{row.dTag}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </details>
  );
}
