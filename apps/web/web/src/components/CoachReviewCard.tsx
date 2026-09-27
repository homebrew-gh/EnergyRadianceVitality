import { useEffect, useMemo, useState } from "react";
import { buildAnalysisContext } from "../lib/aiContext";
import { api, type AiContextLevel, type AiProvider } from "../lib/api";
import { streamAi } from "../lib/aiClient";
import { parseCoachReview } from "../lib/coachReview";
import type { TrainingContextBundleInput } from "../lib/trainingContextBundle";
import { SectionHeader } from "./FieldLabel";
import { ContextPreviewModal } from "./ContextPreviewModal";

const HINT_KEY = "erv-coach-review-hint-dismissed";
const CACHE_PREFIX = "erv-coach-review";

type CachedReview = {
  contextHash: string;
  content: string;
  model: string;
  promptVersion: string;
  generatedAt: string;
};

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
  const raw = window.localStorage.getItem(`${CACHE_PREFIX}:${period}:${hash}`);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as CachedReview;
    return parsed.contextHash === hash ? parsed : null;
  } catch {
    return null;
  }
}

function writeCache(period: string, review: CachedReview) {
  const prefix = `${CACHE_PREFIX}:${period}:`;
  for (let index = window.localStorage.length - 1; index >= 0; index -= 1) {
    const key = window.localStorage.key(index);
    if (key?.startsWith(prefix)) window.localStorage.removeItem(key);
  }
  window.localStorage.setItem(`${prefix}${review.contextHash}`, JSON.stringify(review));
}

export function CoachReviewCard({ bundleInput, periodWeeks, periodLabel }: CoachReviewCardProps) {
  const [enabled, setEnabled] = useState(false);
  const [contextLevel, setContextLevel] = useState<AiContextLevel>("STANDARD");
  const [modelId, setModelId] = useState("");
  const [provider, setProvider] = useState<string>("OFF");
  const [alwaysPreview, setAlwaysPreview] = useState(true);
  const [settingsReady, setSettingsReady] = useState(false);
  const [hintDismissed, setHintDismissed] = useState(
    () => window.localStorage.getItem(HINT_KEY) === "1",
  );
  const [previewOpen, setPreviewOpen] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const [content, setContent] = useState("");
  const [model, setModel] = useState("");
  const [promptVersion, setPromptVersion] = useState("");
  const [generatedAt, setGeneratedAt] = useState("");
  const [error, setError] = useState<string | null>(null);

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
        setModelId(settings.analysis_model_id);
        setProvider(settings.provider);
        setAlwaysPreview(settings.always_show_context_preview);
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
    setModel(cached?.model ?? "");
    setPromptVersion(cached?.promptVersion ?? "");
    setGeneratedAt(cached?.generatedAt ?? "");
    setError(null);
  }, [analysis.hash, periodWeeks]);

  const parsed = useMemo(() => parseCoachReview(content), [content]);

  const runReview = async () => {
    setStreaming(true);
    setError(null);
    setContent("");
    let text = "";
    let nextModel = modelId;
    let nextVersion = "";
    try {
      await streamAi(
        {
          task: "analysis",
          model_id: modelId || undefined,
          context: analysis.context,
          user_prompt: `Review this athlete for ${periodLabel}. Compare the exercises programmed in saved workouts and weight routines with the logged working weights and session counts. Answer only under the five required headings.`,
          response_format: "markdown",
        },
        (event) => {
          if (event.event === "delta") {
            text += event.data;
            setContent(text);
          } else if (event.event === "done") {
            try {
              const meta = JSON.parse(event.data) as { model?: string; promptVersion?: string };
              if (meta.model) nextModel = meta.model;
              if (meta.promptVersion) nextVersion = meta.promptVersion;
            } catch {
              nextVersion = "";
            }
          } else if (event.event === "error") {
            throw new Error(event.data);
          }
        },
      );
      const generated = new Date().toISOString();
      setModel(nextModel);
      setPromptVersion(nextVersion);
      setGeneratedAt(generated);
      if (text.trim()) {
        writeCache(periodKey(periodWeeks), {
          contextHash: analysis.hash,
          content: text,
          model: nextModel,
          promptVersion: nextVersion,
          generatedAt: generated,
        });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not review training.");
    } finally {
      setStreaming(false);
    }
  };

  const onReview = () => {
    if (alwaysPreview) setPreviewOpen(true);
    else void runReview();
  };

  const onCopy = async () => {
    const markdown = parsed.sections.map((section) => `## ${section.title}\n\n${section.body}`).join("\n\n");
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
              window.localStorage.setItem(HINT_KEY, "1");
              setHintDismissed(true);
            }}
          >
            Dismiss
          </button>
        </p>
      </section>
    );
  }

  return (
    <section className="card space-y-4 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h3 className="font-semibold text-heading">Coach review</h3>
          <p className="mt-1 text-sm text-muted">
            A read-only review of {periodLabel.toLowerCase()} using your profile, baseline, equipment,
            and recent sessions. It does not change your library.
          </p>
        </div>
        <button type="button" className="btn-primary" disabled={streaming} onClick={onReview}>
          {streaming ? "Reviewing…" : content ? "Regenerate" : "Review my training"}
        </button>
      </div>

      {error ? (
        <p className="text-sm text-error" role="alert">
          {error}
        </p>
      ) : null}

      {parsed.sections.length > 0 ? (
        <div className="space-y-4">
          {parsed.sections.map((section) => (
            <div key={section.title} className="space-y-1">
              <SectionHeader>{section.title}</SectionHeader>
              <p className="whitespace-pre-wrap text-sm text-heading">{section.body}</p>
            </div>
          ))}
        </div>
      ) : streaming ? (
        <p className="text-sm text-muted">Waiting for the first section…</p>
      ) : null}

      {content ? (
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
