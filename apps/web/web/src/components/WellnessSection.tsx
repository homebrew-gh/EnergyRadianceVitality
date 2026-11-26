import { SectionHeader } from "./FieldLabel";
import { wellnessHasData, type HeatColdTotals, type WellnessSummary } from "../lib/wellness";

function plural(n: number, word: string): string {
  return `${n} ${word}${n === 1 ? "" : "s"}`;
}

function minutesText(minutes: number): string {
  if (minutes < 1) return `${Math.round(minutes * 60)} s`;
  if (minutes < 120) return `${Math.round(minutes)} min`;
  return `${Math.round((minutes / 60) * 10) / 10} h`;
}

function signed(value: number, unit: string): string {
  if (Math.abs(value) < 0.05) return `no change`;
  return `${value > 0 ? "+" : ""}${value} ${unit}`;
}

function Tile({ label, value, detail }: { label: string; value: string; detail?: string | null }) {
  return (
    <div className="rounded-card bg-[var(--erv-surface)]/70 p-3 space-y-1">
      <p className="text-xs text-muted">{label}</p>
      <p className="text-lg font-semibold text-heading">{value}</p>
      {detail ? <p className="text-xs text-muted">{detail}</p> : null}
    </div>
  );
}

function heatColdDetail(t: HeatColdTotals): string | null {
  const parts = [minutesText(t.minutes)];
  if (t.averageTemp != null) parts.push(`avg ${t.averageTemp} ${t.tempUnit}`);
  return parts.join(" · ");
}

/**
 * Read-only wellness totals for the period picked at the top of Progress: Hot + Cold, light
 * therapy, supplements, fasting and waist. Logged and edited on the phone.
 */
export function WellnessSection({ summary, periodLabel }: { summary: WellnessSummary; periodLabel: string }) {
  if (!wellnessHasData(summary)) return null;
  const { sauna, cold, light, supplements, fasting, waist } = summary;
  return (
    <section className="card p-5 space-y-4">
      <div className="space-y-1">
        <SectionHeader>Wellness</SectionHeader>
        <p className="text-xs text-muted">
          {periodLabel}. Logged on your phone; edit entries there.
        </p>
      </div>
      <div className="grid gap-3 sm:grid-cols-3">
        {sauna.sessions > 0 ? (
          <Tile label="Sauna" value={plural(sauna.sessions, "session")} detail={heatColdDetail(sauna)} />
        ) : null}
        {cold.sessions > 0 ? (
          <Tile label="Cold Plunge" value={plural(cold.sessions, "session")} detail={heatColdDetail(cold)} />
        ) : null}
        {summary.contrastSessions > 0 ? (
          <Tile label="Contrast" value={plural(summary.contrastSessions, "session")} />
        ) : null}
        {light.sessions > 0 ? (
          <Tile label="Light Therapy" value={plural(light.sessions, "session")} detail={minutesText(light.minutes)} />
        ) : null}
        {supplements.daysLogged > 0 ? (
          <Tile
            label="Supplements"
            value={`${supplements.daysLogged} of ${summary.periodDays} days`}
            detail={plural(supplements.intakes, "intake")}
          />
        ) : null}
        {fasting.completed > 0 ? (
          <Tile
            label="Fasting"
            value={plural(fasting.completed, "fast")}
            detail={`avg ${fasting.averageHours} h · longest ${fasting.longestHours} h`}
          />
        ) : null}
        {waist ? (
          <Tile
            label="Waist"
            value={`${waist.latest} ${waist.unit}`}
            detail={waist.change != null ? signed(waist.change, waist.unit) : null}
          />
        ) : null}
      </div>
    </section>
  );
}
