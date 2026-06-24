import { useMemo, useState } from "react";
import { SectionHeader } from "./FieldLabel";
import {
  buildTrainingContextJson,
  buildTrainingContextMarkdown,
  trainingContextCompleteness,
  type TrainingContextBundleInput,
} from "../lib/trainingContextBundle";

type TrainingContextExportCardProps = {
  bundleInput: TrainingContextBundleInput;
};

async function copyText(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }
  const ta = document.createElement("textarea");
  ta.value = text;
  ta.style.position = "fixed";
  ta.style.left = "-9999px";
  document.body.appendChild(ta);
  ta.select();
  document.execCommand("copy");
  document.body.removeChild(ta);
}

export function TrainingContextExportCard({ bundleInput }: TrainingContextExportCardProps) {
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const completeness = useMemo(
    () => trainingContextCompleteness(bundleInput),
    [bundleInput],
  );

  const markdown = useMemo(
    () => buildTrainingContextMarkdown(bundleInput),
    [bundleInput],
  );

  const json = useMemo(() => buildTrainingContextJson(bundleInput), [bundleInput]);

  const onCopyMarkdown = async () => {
    setError(null);
    setMessage(null);
    try {
      await copyText(markdown);
      setMessage("Markdown context copied — paste into your AI tool.");
    } catch {
      setError("Could not copy to clipboard.");
    }
  };

  const onCopyJson = async () => {
    setError(null);
    setMessage(null);
    try {
      await copyText(json);
      setMessage("JSON context copied.");
    } catch {
      setError("Could not copy to clipboard.");
    }
  };

  const coreReady = completeness.filter((c) => c.id !== "workouts").every((c) => c.ok);

  return (
    <section className="card p-5 space-y-4">
      <div className="space-y-1">
        <SectionHeader>Copy training context</SectionHeader>
        <p className="text-sm text-muted">
          Dry run for Phase 4 AI — bundles profile, baseline, equipment, and library ids. Paste
          into an external model to generate workouts without in-app AI yet.
        </p>
      </div>

      <ul className="space-y-1 text-sm">
        {completeness.map((item) => (
          <li key={item.id} className="flex flex-wrap items-baseline gap-2">
            <span
              className={
                item.ok ? "text-green-700 font-medium" : "text-amber-700 font-medium"
              }
            >
              {item.ok ? "✓" : "○"} {item.label}
            </span>
            {!item.ok ? <span className="text-muted text-xs">— {item.hint}</span> : null}
          </li>
        ))}
      </ul>

      {!coreReady ? (
        <p className="text-xs text-amber-700">
          Profile, logs, or equipment missing — export still works, but AI output will be less
          personalized.
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <button type="button" className="btn-primary text-sm" onClick={() => void onCopyMarkdown()}>
          Copy markdown for AI
        </button>
        <button type="button" className="btn-ghost text-sm" onClick={() => void onCopyJson()}>
          Copy JSON
        </button>
      </div>

      {message ? <p className="text-sm text-green-700">{message}</p> : null}
      {error ? <p className="text-sm text-red-600">{error}</p> : null}

      <details className="text-xs text-muted">
        <summary className="cursor-pointer select-none">Preview markdown header</summary>
        <pre className="mt-2 max-h-48 overflow-auto rounded bg-[var(--erv-surface-variant)]/50 p-3 whitespace-pre-wrap font-mono text-[11px]">
          {markdown.slice(0, 1200)}
          {markdown.length > 1200 ? "\n\n…" : ""}
        </pre>
      </details>
    </section>
  );
}
