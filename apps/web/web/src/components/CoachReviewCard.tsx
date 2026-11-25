import { useEffect, useMemo, useState } from "react";
import { buildAnalysisContext } from "../lib/aiContext";
import { api, type AiContextLevel, type AiProvider } from "../lib/api";
import { streamAi } from "../lib/aiClient";
import {
  coachReviewToMarkdown,
  liftTrendLabel,
  parseCoachReview,
  parseCoachReviewJson,
  statusLabel,
  type CoachReviewJson,
} from "../lib/coachReview";
import type { TrainingContextBundleInput } from "../lib/trainingContextBundle";
import { FieldLabel, SectionHeader } from "./FieldLabel";
import { ContextPreviewModal } from "./ContextPreviewModal";

const HINT_KEY = "erv-coach-review-hint-dismissed";
const CACHE_PREFIX = "erv-coach-review";

type CachedReview = {
  contextHash: string;
  content: string;
  /** "json" for prompt v3 reviews; older cached reviews are Markdown. */
  format?: "json" | "markdown";
  model: string;
  promptVersion: string;
  generatedAt: string;
};

const REVIEW_PROMPT = (periodLabel: string) =>
  `Review this athlete for ${periodLabel}. Compare what is planned in saved workouts with liftTrends and weeklyTraining, and say what to do next session for the lifts that matter most.`;

function trendClass(trend: string): string {
  if (trend === "up" || trend === "improving" || trend === "on_track" || trend === "ok") {
    return "text-success";
  }
  if (trend === "down" || trend === "slipping" || trend === "behind") return "text-error";
  if (trend === "flat" || trend === "watch") return "text-amber-600";
  return "text-heading";
}

type CoachReviewCardProps = {
  bundleInput: TrainingContextBundleInput;
  periodWeeks: number | null;
  periodLabel: string;
};

function providerLabel(provider: AiProvider | string): string {
  if (provider === "MAPLE") return "Maple TEE (off-box, metered)";
  if (provider === "OPENAI_COMPAT") return "LAN / self-hosted";
  return "AI coach off";
}

function periodKey(periodWeeks: number | null): string {
  return periodWeeks == null ? "all" : String(periodWeeks);
}

function readCache(period: string, hash: string): CachedReview | null {
  try {
    const raw = window.localStorage.getItem(`${CACHE_PREFIX}:${period}:${hash}`);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CachedReview;
    return parsed.contextHash === hash ? parsed : null;
  } catch {
    return null;
  }
}

function writeCache(period: string, review: CachedReview) {
  try {
    const prefix = `${CACHE_PREFIX}:${period}:`;
    for (let index = window.localStorage.length - 1; index >= 0; index -= 1) {
      const key = window.localStorage.key(index);
      if (key?.startsWith(prefix)) window.localStorage.removeItem(key);
    }
    window.localStorage.setItem(`${prefix}${review.contextHash}`, JSON.stringify(review));
  } catch {
    // Storage full or blocked: the review still shows, it just is not cached.
  }
}

/** Collect one streamed reply. Throws on a stream error event. */
async function streamText(
  request: Parameters<typeof streamAi>[0],
  onProgress: (characters: number) => void,
): Promise<{ text: string; model: string; promptVersion: string }> {
  let text = "";
  let model = request.model_id ?? "";
  let promptVersion = "";
  await streamAi(request, (event) => {
    if (event.event === "delta") {
      text += event.data;
      onProgress(text.length);
    } else if (event.event === "done") {
      try {
        const meta = JSON.parse(event.data) as { model?: string; promptVersion?: string };
        if (meta.model) model = meta.model;
        if (meta.promptVersion) promptVersion = meta.promptVersion;
      } catch {
        promptVersion = "";
      }
    } else if (event.event === "error") {
      throw new Error(event.data);
    }
  });
  return { text, model, promptVersion };
}

export function CoachReviewCard({ bundleInput, periodWeeks, periodLabel }: CoachReviewCardProps) {
  const [enabled, setEnabled] = useState(false);
  const [contextLevel, setContextLevel] = useState<AiContextLevel>("STANDARD");
  const [settingsModelId, setSettingsModelId] = useState("");
  const [models, setModels] = useState<string[]>([]);
  const [chosenModel, setChosenModel] = useState("");
  const [provider, setProvider] = useState<string>("OFF");
  const [alwaysPreview, setAlwaysPreview] = useState(true);
  const [settingsReady, setSettingsReady] = useState(false);
  const [hintDismissed, setHintDismissed] = useState(() => {
    try {
      return window.localStorage.getItem(HINT_KEY) === "1";
    } catch {
      return false;
    }
  });
  const [previewOpen, setPreviewOpen] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [progress, setProgress] = useState("");
  const [content, setContent] = useState("");
  const [format, setFormat] = useState<"json" | "markdown">("json");
  const [model, setModel] = useState("");
  const [promptVersion, setPromptVersion] = useState("");
  const [generatedAt, setGeneratedAt] = useState("");
  const [error, setError] = useState<string | null>(null);

  const modelId = chosenModel || settingsModelId;

  const analysis = useMemo(
    () => buildAnalysisContext(bundleInput, { contextLevel, periodWeeks }),
    [bundleInput, contextLevel, periodWeeks],
  );

  useEffect(() => {
    let cancelled = false;
    void api
      .getAiSettings()
      .then((settings) => {
        if (cancelled) return;
        setEnabled(settings.enabled);
        setContextLevel(settings.context_level);
        setSettingsModelId(settings.analysis_model_id);
        setProvider(settings.provider);
        setAlwaysPreview(settings.always_show_context_preview);
        if (settings.enabled) {
          void api
            .aiModels()
            .then((result) => {
              if (!cancelled) setModels(result.models);
            })
            .catch(() => undefined);
        }
      })
      .catch(() => {
        if (!cancelled) setEnabled(false);
      })
      .finally(() => {
        if (!cancelled) setSettingsReady(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const cached = readCache(periodKey(periodWeeks), analysis.hash);
    setContent(cached?.content ?? "");
    setFormat(cached?.format ?? "markdown");
    setModel(cached?.model ?? "");
    setPromptVersion(cached?.promptVersion ?? "");
    setGeneratedAt(cached?.generatedAt ?? "");
    setError(null);
  }, [analysis.hash, periodWeeks]);

  const review = useMemo<CoachReviewJson | null>(() => {
    if (format !== "json" || !content) return null;
    const parsed = parseCoachReviewJson(content);
    return parsed.ok ? parsed.review : null;
  }, [content, format]);
  const legacy = useMemo(
    () => (format === "markdown" && content ? parseCoachReview(content) : null),
    [content, format],
  );

  const runReview = async () => {
    setStreaming(true);
    setError(null);
    setProgress("Reviewing your training…");
    const request = {
      task: "analysis" as const,
      model_id: modelId || undefined,
      context: analysis.context,
      user_prompt: REVIEW_PROMPT(periodLabel),
      response_format: "json" as const,
    };
    const onProgress = (characters: number) => setProgress(`Writing the review… ${characters} characters`);
    try {
      let reply = await streamText(request, onProgress);
      let parsed = parseCoachReviewJson(reply.text);
      if (!parsed.ok) {
        setProgress("Fixing the review format…");
        reply = await streamText(
          {
            ...request,
            user_prompt: `${request.user_prompt}\n\nYour previous reply could not be used: ${parsed.errors.join(" ")} Reply again with the JSON object only.`,
          },
          onProgress,
        );
        parsed = parseCoachReviewJson(reply.text);
      }
      if (!parsed.ok) {
        throw new Error(
          `The model did not return a usable review (${parsed.errors.join(" ")}). Try again or pick another model.`,
        );
      }
      const generated = new Date().toISOString();
      setContent(reply.text);
      setFormat("json");
      setModel(reply.model || modelId);
      setPromptVersion(reply.promptVersion);
      setGeneratedAt(generated);
      writeCache(periodKey(periodWeeks), {
        contextHash: analysis.hash,
        content: reply.text,
        format: "json",
        model: reply.model || modelId,
        promptVersion: reply.promptVersion,
        generatedAt: generated,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not review training.");
    } finally {
      setStreaming(false);
      setProgress("");
    }
  };

  const onReview = () => {
    if (alwaysPreview) setPreviewOpen(true);
    else void runReview();
  };

  const onCopy = async () => {
    const markdown = review
      ? coachReviewToMarkdown(review)
      : legacy?.sections.map((section) => `## ${section.title}\n\n${section.body}`).join("\n\n");
    try {
      await navigator.clipboard.writeText(markdown || content);
    } catch {
      setError("Could not copy the review.");
    }
  };

  if (!settingsReady) return null;

  if (!enabled) {
    if (hintDismissed) return null;
    return (
      <section className="card p-4">
        <p className="text-sm text-muted">
          Enable AI coach in Settings to review this period.
          <button
            type="button"
            className="btn-ghost ml-2 px-2 py-1 text-xs"
            onClick={() => {
              try {
                window.localStorage.setItem(HINT_KEY, "1");
              } catch {
                // Hint comes back next visit; nothing else depends on it.
              }
              setHintDismissed(true);
            }}
          >
            Dismiss
          </button>
        </p>
      </section>
    );
  }

  const modelOptions =
    settingsModelId && !models.includes(settingsModelId) ? [settingsModelId, ...models] : models;

  return (
    <section className="card space-y-4 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="font-semibold text-heading">Coach review</h3>
          <p className="mt-1 text-sm text-muted">
            A read-only review of {periodLabel.toLowerCase()} using your profile, lift trends,
            equipment, and recent sessions. It does not change your library.
          </p>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <div className="flex flex-col gap-1">
            <label className="label" htmlFor="coach-review-model">
              <FieldLabel>Model</FieldLabel>
            </label>
            <select
              id="coach-review-model"
              className="input max-w-[14rem] py-1 text-sm"
              value={chosenModel}
              disabled={streaming}
              onChange={(event) => setChosenModel(event.target.value)}
            >
              <option value="">
                {settingsModelId ? `${settingsModelId} (Settings)` : "Settings default"}
              </option>
              {modelOptions
                .filter((option) => option !== settingsModelId)
                .map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
            </select>
          </div>
          <button type="button" className="btn-primary" disabled={streaming} onClick={onReview}>
            {streaming ? "Reviewing…" : content ? "Regenerate" : "Review my training"}
          </button>
        </div>
      </div>

      {error ? (
        <p className="text-sm text-error" role="alert">
          {error}
        </p>
      ) : null}

      {streaming ? <p className="text-sm text-muted">{progress}</p> : null}

      {!streaming && review ? <ReviewBody review={review} /> : null}

      {!streaming && legacy && legacy.sections.length > 0 ? (
        <div className="space-y-4">
          <p className="text-xs text-muted">
            This review was made by an older coach prompt. Regenerate for the new layout.
          </p>
          {legacy.sections.map((section) => (
            <div key={section.title} className="space-y-1">
              <SectionHeader>{section.title}</SectionHeader>
              <p className="whitespace-pre-wrap text-sm text-heading">{section.body}</p>
            </div>
          ))}
        </div>
      ) : null}

      {content && !streaming ? (
        <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
          <span>{model || modelId}</span>
          {generatedAt ? <span>{new Date(generatedAt).toLocaleString()}</span> : null}
          {promptVersion ? <span>Prompt {promptVersion}</span> : null}
          <button type="button" className="btn-ghost px-2 py-1 text-xs" onClick={() => void onCopy()}>
            Copy
          </button>
        </div>
      ) : null}

      {previewOpen ? (
        <ContextPreviewModal
          json={analysis.json}
          byteLength={analysis.byteLength}
          modelId={modelId}
          providerLabel={providerLabel(provider)}
          onClose={() => setPreviewOpen(false)}
          onConfirm={() => {
            setPreviewOpen(false);
            void runReview();
          }}
        />
      ) : null}
    </section>
  );
}

function ReviewBody({ review }: { review: CoachReviewJson }) {
  const chips: { label: string; value: string; tone: string }[] = [
    { label: "Consistency", value: statusLabel(review.status.consistency), tone: review.status.consistency },
    { label: "Progress", value: statusLabel(review.status.progress), tone: review.status.progress },
    { label: "Recovery", value: statusLabel(review.status.recovery), tone: review.status.recovery },
    { label: "Confidence", value: review.confidence, tone: "neutral" },
  ];
  return (
    <div className="space-y-4">
      <p className="text-base font-semibold text-heading">{review.headline}</p>

      <div className="flex flex-wrap gap-2">
        {chips.map((chip) => (
          <span
            key={chip.label}
            className="rounded-pill border border-outline px-3 py-1 text-xs text-heading"
          >
            {chip.label} <span className={`font-semibold ${trendClass(chip.tone)}`}>{chip.value}</span>
          </span>
        ))}
      </div>

      {review.lifts.length > 0 ? (
        <div className="space-y-2">
          <SectionHeader>Next session</SectionHeader>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-xs text-muted">
                <tr className="border-b border-outline">
                  <th className="py-1 pr-3 font-semibold">Lift</th>
                  <th className="py-1 pr-3 font-semibold">Trend</th>
                  <th className="py-1 pr-3 font-semibold">Next</th>
                  <th className="py-1 font-semibold">Why</th>
                </tr>
              </thead>
              <tbody>
                {review.lifts.map((lift) => (
                  <tr key={lift.name} className="border-b border-outline align-top last:border-0">
                    <td className="py-2 pr-3 font-medium text-heading">{lift.name}</td>
                    <td className={`py-2 pr-3 ${trendClass(lift.trend)}`}>{liftTrendLabel(lift.trend)}</td>
                    <td className="whitespace-nowrap py-2 pr-3 tabular-nums text-heading">{lift.next}</td>
                    <td className="py-2 text-muted">{lift.why}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}

      <div className="space-y-2">
        <SectionHeader>Next week</SectionHeader>
        <ol className="space-y-2">
          {review.nextWeek.map((item, index) => (
            <li key={item.action} className="flex gap-3 text-sm">
              <span className="font-semibold text-primary">{index + 1}</span>
              <div className="min-w-0">
                <p className="text-heading">{item.action}</p>
                {item.why ? <p className="text-muted">{item.why}</p> : null}
              </div>
            </li>
          ))}
        </ol>
      </div>

      {review.bodyWeight ? (
        <p className="text-sm text-heading">
          <span className="font-semibold">Body weight: </span>
          {review.bodyWeight}
        </p>
      ) : null}

      {review.wins.length > 0 || review.watchOuts.length > 0 ? (
        <details className="rounded-card border border-outline p-3">
          <summary className="cursor-pointer text-sm font-semibold text-heading">
            What went well and watch-outs
          </summary>
          <div className="mt-3 grid gap-4 sm:grid-cols-2">
            {review.wins.length > 0 ? (
              <div className="space-y-1">
                <SectionHeader>What went well</SectionHeader>
                <ul className="list-disc space-y-1 pl-5 text-sm text-heading">
                  {review.wins.map((win) => (
                    <li key={win}>{win}</li>
                  ))}
                </ul>
              </div>
            ) : null}
            {review.watchOuts.length > 0 ? (
              <div className="space-y-1">
                <SectionHeader>Watch-outs</SectionHeader>
                <ul className="list-disc space-y-1 pl-5 text-sm text-heading">
                  {review.watchOuts.map((item) => (
                    <li key={item.text}>
                      {item.text}
                      {item.evidence ? <span className="text-muted"> ({item.evidence})</span> : null}
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
        </details>
      ) : null}

      {review.missingData ? <p className="text-xs text-muted">Missing data: {review.missingData}</p> : null}
    </div>
  );
}
