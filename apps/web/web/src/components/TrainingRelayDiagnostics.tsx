import { useMemo, useState } from "react";
import type { AppDataRecord } from "../lib/api";
import { FieldLabel, SectionHeader } from "./FieldLabel";
import {
  buildTrainingDayDiagnostics,
  trainingDayDiagnosticLabel,
  type TrainingDayDiagnostic,
  type TrainingDaySideDiagnostic,
} from "../lib/trainingHistory";

function statusClass(status: TrainingDaySideDiagnostic["status"]): string {
  switch (status) {
    case "parsed_ok":
      return "text-emerald-700";
    case "tombstone_on_relay":
    case "empty_on_relay":
    case "parse_rejected":
      return "text-amber-700";
    case "decrypt_failed":
      return "text-red-600";
    default:
      return "text-muted";
  }
}

function SideCell({ side }: { side: TrainingDaySideDiagnostic | null }) {
  if (!side) {
    return <span className="text-muted text-xs">— not on relay —</span>;
  }
  return (
    <div className="space-y-1 text-xs">
      <p className={`font-medium ${statusClass(side.status)}`}>
        {trainingDayDiagnosticLabel(side.status)}
        {side.rawCount > 0 || side.parsedCount > 0
          ? ` · ${side.parsedCount}/${side.rawCount} readable`
          : null}
      </p>
      <p className="font-mono text-muted truncate" title={side.dTag}>
        {side.dTag}
      </p>
      {side.decryptError ? (
        <p className="text-red-600 break-words">{side.decryptError}</p>
      ) : null}
      {side.parseNotes.length > 0 ? (
        <ul className="list-disc pl-4 text-muted space-y-0.5">
          {side.parseNotes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

type Props = {
  records: AppDataRecord[];
  /** Dates visible in the current Progress period filter (for highlighting). */
  visibleDates?: Set<string>;
};

export function TrainingRelayDiagnostics({ records, visibleDates }: Props) {
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState("");
  const [onlyIssues, setOnlyIssues] = useState(true);
  const [copiedTag, setCopiedTag] = useState<string | null>(null);

  const rows = useMemo(() => buildTrainingDayDiagnostics(records), [records]);

  const filtered = useMemo(() => {
    let list = rows;
    if (onlyIssues) {
      list = list.filter(
        (r) =>
          r.weight?.status === "tombstone_on_relay" ||
          r.weight?.status === "parse_rejected" ||
          r.weight?.status === "decrypt_failed" ||
          r.weight?.status === "empty_on_relay" ||
          r.cardio?.status === "tombstone_on_relay" ||
          r.cardio?.status === "parse_rejected" ||
          r.cardio?.status === "decrypt_failed" ||
          r.cardio?.status === "empty_on_relay",
      );
    }
    const q = filter.trim();
    if (q) {
      list = list.filter((r) => r.date.includes(q));
    }
    return list;
  }, [rows, onlyIssues, filter]);

  const issueCount = rows.filter((r) => r.issue).length;

  async function handleCopy(side: TrainingDaySideDiagnostic | null) {
    if (!side?.plaintext) return;
    try {
      await navigator.clipboard.writeText(side.plaintext);
      setCopiedTag(side.dTag);
      window.setTimeout(() => setCopiedTag(null), 2000);
    } catch {
      // Clipboard may be blocked; user can expand row and select manually.
    }
  }

  return (
    <section className="card p-5 space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <SectionHeader>Relay diagnostics</SectionHeader>
          <p className="text-sm text-muted mt-1">
            Per-day relay status for strength and cardio day logs. A day with only lifting or only cardio is
            normal — we only flag a side when its tag exists but is cleared or unreadable.
          </p>
        </div>
        <button type="button" className="btn-ghost text-sm" onClick={() => setOpen((v) => !v)}>
          {open ? "Hide" : "Show"} ({rows.length} days on relay
          {issueCount > 0 ? `, ${issueCount} with issues` : ""})
        </button>
      </div>

      {open ? (
        <div className="space-y-3 pt-2 border-t border-[var(--erv-outline-variant)]">
          <div className="flex flex-wrap items-end gap-3">
            <label className="space-y-1">
              <span className="label">
                <FieldLabel>Filter by date</FieldLabel>
              </span>
              <input
                className="input font-mono text-sm w-40"
                placeholder="2026-06-17"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
              />
            </label>
            <label className="flex items-center gap-2 text-sm pb-2">
              <input
                type="checkbox"
                checked={onlyIssues}
                onChange={(e) => setOnlyIssues(e.target.checked)}
              />
              Only days with missing or partial data
            </label>
          </div>

          {filtered.length === 0 ? (
            <p className="text-sm text-muted">No matching day logs on the relay.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm min-w-[36rem]">
                <thead>
                  <tr className="text-left text-muted border-b border-[var(--erv-outline-variant)]">
                    <th className="py-2 pr-3 font-medium">Date</th>
                    <th className="py-2 pr-3 font-medium">Strength</th>
                    <th className="py-2 pr-3 font-medium">Cardio</th>
                    <th className="py-2 font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.slice(0, 40).map((row: TrainingDayDiagnostic) => {
                    const inPeriod =
                      visibleDates == null || visibleDates.size === 0 || visibleDates.has(row.date);
                    return (
                      <tr
                        key={row.date}
                        className={`border-b border-[var(--erv-outline-variant)]/50 align-top ${
                          !inPeriod ? "opacity-60" : ""
                        }`}
                      >
                        <td className="py-3 pr-3 whitespace-nowrap">
                          <span className="font-mono text-xs">{row.date}</span>
                          {!inPeriod ? (
                            <p className="text-[10px] text-muted mt-0.5">Outside period filter</p>
                          ) : null}
                          {row.issue ? (
                            <p className="text-xs text-amber-700 mt-1 max-w-[10rem]">{row.issue}</p>
                          ) : null}
                        </td>
                        <td className="py-3 pr-3 max-w-xs">
                          <SideCell side={row.weight} />
                        </td>
                        <td className="py-3 pr-3 max-w-xs">
                          <SideCell side={row.cardio} />
                        </td>
                        <td className="py-3 whitespace-nowrap space-x-1">
                          {row.weight?.plaintext ? (
                            <button
                              type="button"
                              className="btn-ghost text-xs py-1 px-2"
                              onClick={() => void handleCopy(row.weight)}
                            >
                              {copiedTag === row.weight?.dTag ? "Copied" : "Copy weight JSON"}
                            </button>
                          ) : null}
                          {row.cardio?.plaintext ? (
                            <button
                              type="button"
                              className="btn-ghost text-xs py-1 px-2"
                              onClick={() => void handleCopy(row.cardio)}
                            >
                              {copiedTag === row.cardio?.dTag ? "Copied" : "Copy cardio JSON"}
                            </button>
                          ) : null}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {filtered.length > 40 ? (
                <p className="text-xs text-muted mt-2">
                  Showing 40 of {filtered.length} rows — narrow the date filter to find a specific day.
                </p>
              ) : null}
            </div>
          )}

          <p className="text-xs text-muted">
            If a day shows <strong className="font-medium">Empty on relay</strong> or cardio is{" "}
            <strong className="font-medium">— not on relay —</strong> while Android has the workout,
            open Android Settings → force resync, wait for sync to finish, then Reload on this tab.
            Also try Period → <strong className="font-medium">All time</strong> in case the workout is
            older than 12 weeks.
          </p>
        </div>
      ) : null}
    </section>
  );
}
