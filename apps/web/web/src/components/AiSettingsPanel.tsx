import { useEffect, useState } from "react";
import { FieldLabel, SectionHeader } from "./FieldLabel";
import { SecretInput } from "./SecretInput";
import { completeAi } from "../lib/aiClient";
import {
  ApiError,
  api,
  type AiContextLevel,
  type AiKeyMode,
  type AiProvider,
  type AiSettings,
  type DetectedAiEndpoint,
} from "../lib/api";

const CONTEXT_LEVELS: { id: AiContextLevel; label: string; detail: string }[] = [
  { id: "MINIMAL", label: "Minimal", detail: "Profile, snapshot totals, and ids." },
  { id: "STANDARD", label: "Standard", detail: "Adds guardrails and recent session summaries." },
  { id: "FULL", label: "Full", detail: "Adds recent per-exercise history. Raw day logs stay on the relay." },
];

type FormState = {
  enabled: boolean;
  provider: AiProvider;
  baseUrl: string;
  keyMode: AiKeyMode;
  apiKey: string;
  analysisModelId: string;
  generationModelId: string;
  contextLevel: AiContextLevel;
  alwaysShowContextPreview: boolean;
};

function formFromSettings(settings: AiSettings): FormState {
  const detected = settings.detected_endpoints[0];
  const baseUrl = settings.base_url || detected?.base_url || "";
  const provider =
    settings.provider !== "OFF"
      ? settings.provider
      : detected
        ? "MAPLE"
        : "OFF";
  return {
    enabled: settings.enabled,
    provider,
    baseUrl,
    keyMode: settings.key_mode,
    apiKey: "",
    analysisModelId: settings.analysis_model_id,
    generationModelId: settings.generation_model_id,
    contextLevel: settings.context_level,
    alwaysShowContextPreview: settings.always_show_context_preview,
  };
}

function providerForUrl(url: string, detected: DetectedAiEndpoint[]): AiProvider {
  const trimmed = url.trim().replace(/\/$/, "");
  if (detected.some((endpoint) => endpoint.base_url.replace(/\/$/, "") === trimmed)) {
    return "MAPLE";
  }
  try {
    const host = new URL(trimmed).hostname;
    if (host === "maple-proxy.startos" || host.endsWith(".maple-proxy.startos")) return "MAPLE";
  } catch {
    return trimmed ? "OPENAI_COMPAT" : "OFF";
  }
  return trimmed ? "OPENAI_COMPAT" : "OFF";
}

export function AiSettingsPanel() {
  const [form, setForm] = useState<FormState | null>(null);
  const [detected, setDetected] = useState<DetectedAiEndpoint[]>([]);
  const [maskedKey, setMaskedKey] = useState<string | null>(null);
  const [hasSealedKey, setHasSealedKey] = useState(false);
  const [models, setModels] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [testMessage, setTestMessage] = useState("");
  const [reply, setReply] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void api
      .getAiSettings()
      .then((settings) => {
        if (cancelled) return;
        setForm(formFromSettings(settings));
        setDetected(settings.detected_endpoints);
        setMaskedKey(settings.api_key_masked);
        setHasSealedKey(settings.has_sealed_api_key);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message : "Could not load AI settings.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const applyModels = (next: string[], analysis: string, generation: string) => {
    setModels(next);
    setForm((current) => {
      if (!current) return current;
      return {
        ...current,
        analysisModelId: current.analysisModelId || next[0] || analysis,
        generationModelId: current.generationModelId || next[0] || generation,
      };
    });
  };

  const onTest = async () => {
    if (!form || testing) return;
    setTesting(true);
    setError(null);
    setMessage(null);
    try {
      const result = await api.testAiConnection({
        base_url: form.baseUrl,
        key_mode: form.keyMode,
        api_key: form.apiKey.trim() || undefined,
      });
      if (result.models.length > 0) {
        applyModels(result.models, form.analysisModelId, form.generationModelId);
      }
      if (result.error) {
        setError(result.error);
      } else if (result.health_ok && result.auth_ok) {
        setMessage(
          result.models.length > 0
            ? `Connected. ${result.models.length} models available.`
            : "Connected.",
        );
      } else {
        setError("Maple Proxy did not accept the connection.");
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not test the connection.");
    } finally {
      setTesting(false);
    }
  };

  const onRefreshModels = async () => {
    if (testing) return;
    setTesting(true);
    setError(null);
    setMessage(null);
    try {
      const result = await api.aiModels();
      if (result.error) setError(result.error);
      if (result.models.length > 0 && form) {
        applyModels(result.models, form.analysisModelId, form.generationModelId);
        setMessage(`Loaded ${result.models.length} models.`);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not load models.");
    } finally {
      setTesting(false);
    }
  };

  const onSave = async () => {
    if (!form || saving) return;
    setSaving(true);
    setError(null);
    setMessage(null);
    const provider =
      form.enabled && form.provider === "OFF"
        ? providerForUrl(form.baseUrl, detected)
        : form.provider;
    try {
      const saved = await api.updateAiSettings({
        enabled: form.enabled,
        provider,
        base_url: form.baseUrl,
        key_mode: form.keyMode,
        api_key: form.apiKey.trim() || undefined,
        analysis_model_id: form.analysisModelId,
        generation_model_id: form.generationModelId,
        context_level: form.contextLevel,
        always_show_context_preview: form.alwaysShowContextPreview,
      });
      setForm({ ...formFromSettings(saved), apiKey: "" });
      setDetected(saved.detected_endpoints);
      setMaskedKey(saved.api_key_masked);
      setHasSealedKey(saved.has_sealed_api_key);
      setMessage(saved.enabled ? "AI coach settings saved." : "AI coach is off. Settings saved.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save AI settings.");
    } finally {
      setSaving(false);
    }
  };

  const onSendTest = async () => {
    if (!form || sending || !testMessage.trim()) return;
    setSending(true);
    setError(null);
    setReply(null);
    try {
      const result = await completeAi({
        task: "analysis",
        context: { ervTrainingContextVersion: 1 },
        user_prompt: testMessage.trim(),
        response_format: "markdown",
      });
      setReply(result.content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not reach Maple.");
    } finally {
      setSending(false);
    }
  };

  return (
    <section className="card p-5 space-y-4">
      <div>
        <h3 className="font-semibold text-heading">AI Coach</h3>
        <p className="text-sm text-muted mt-1">
          Optional. When Maple Proxy is installed on this StartOS server, ERV can use it for
          training reviews. Workout generation comes later.
        </p>
      </div>

      {loading ? <p className="text-sm text-muted">Loading AI settings…</p> : null}
      {!form && error ? (
        <p className="text-sm text-error" role="alert">
          {error}
        </p>
      ) : null}

      {form ? (
        <div className="space-y-4">
          <label className="flex items-start gap-3">
            <input
              type="checkbox"
              className="mt-1"
              checked={form.enabled}
              onChange={(event) => {
                const enabled = event.target.checked;
                setForm({
                  ...form,
                  enabled,
                  provider: enabled && form.provider === "OFF"
                    ? providerForUrl(form.baseUrl, detected)
                    : form.provider,
                });
              }}
            />
            <span>
              <span className="block text-sm font-medium text-heading">
                <FieldLabel>Enable AI coach</FieldLabel>
              </span>
              <span className="mt-1 block text-sm text-muted">
                Prompts are processed off this server inside Maple&apos;s encrypted enclave and
                billed to your Maple account. ERV only sends aggregates you can preview.
              </span>
            </span>
          </label>

          {detected.length > 0 ? (
            <div className="space-y-2">
              <SectionHeader>Detected endpoint</SectionHeader>
              {detected.map((endpoint) => {
                const selected = form.baseUrl.replace(/\/$/, "") === endpoint.base_url.replace(/\/$/, "");
                return (
                  <label
                    key={endpoint.base_url}
                    className={`flex cursor-pointer gap-3 rounded-card border p-3 ${
                      selected
                        ? "border-[var(--erv-primary)] bg-[var(--erv-primary-container)]/40"
                        : "border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)]"
                    }`}
                  >
                    <input
                      type="radio"
                      name="ai-endpoint"
                      className="mt-1"
                      checked={selected}
                      onChange={() =>
                        setForm({
                          ...form,
                          baseUrl: endpoint.base_url,
                          provider: "MAPLE",
                        })
                      }
                    />
                    <span>
                      <span className="block font-medium text-heading">{endpoint.label}</span>
                      <span className="block text-xs text-muted">Maple TEE (off-box, metered)</span>
                      <span className="mt-1 block font-mono text-xs break-all text-muted">
                        {endpoint.base_url}
                      </span>
                    </span>
                  </label>
                );
              })}
            </div>
          ) : (
            <p className="text-sm text-muted">
              Maple Proxy was not detected on this server. Install it beside ERV, or enter a URL
              for a desktop proxy or another OpenAI-compatible server.
            </p>
          )}

          <label className="block space-y-1" htmlFor="ai-base-url">
            <FieldLabel className="text-sm font-medium">Server URL</FieldLabel>
            <input
              id="ai-base-url"
              className="input w-full font-mono text-sm"
              value={form.baseUrl}
              placeholder="http://maple-proxy.startos:8080"
              onChange={(event) => {
                const baseUrl = event.target.value;
                setForm({
                  ...form,
                  baseUrl,
                  provider: providerForUrl(baseUrl, detected),
                });
              }}
            />
            <span className="block text-xs text-muted">
              {form.provider === "MAPLE"
                ? "Maple TEE (off-box, metered)"
                : form.provider === "OPENAI_COMPAT"
                  ? "LAN / self-hosted"
                  : "Choose Maple Proxy or enter a server URL."}
            </span>
          </label>

          <fieldset className="space-y-2">
            <SectionHeader>API key</SectionHeader>
            <label className="flex items-start gap-2 text-sm">
              <input
                type="radio"
                name="ai-key-mode"
                className="mt-1"
                checked={form.keyMode === "PROXY_HELD"}
                onChange={() => setForm({ ...form, keyMode: "PROXY_HELD", apiKey: "" })}
              />
              <span>Key stays in Maple Proxy. ERV does not store it.</span>
            </label>
            <label className="flex items-start gap-2 text-sm">
              <input
                type="radio"
                name="ai-key-mode"
                className="mt-1"
                checked={form.keyMode === "ERV_SEALED"}
                onChange={() => setForm({ ...form, keyMode: "ERV_SEALED" })}
              />
              <span>Store a key in ERV for a proxy that has none of its own.</span>
            </label>
            {form.keyMode === "ERV_SEALED" ? (
              <div className="space-y-1">
                <label className="label" htmlFor="ai-api-key">
                  <FieldLabel>API key</FieldLabel>
                </label>
                <SecretInput
                  id="ai-api-key"
                  value={form.apiKey}
                  autoComplete="off"
                  placeholder={maskedKey ?? "Paste the Maple API key"}
                  onChange={(event) => setForm({ ...form, apiKey: event.target.value })}
                />
                {maskedKey ? (
                  <p className="text-xs text-muted">Stored key {maskedKey}. Leave blank to keep it.</p>
                ) : hasSealedKey ? (
                  <p className="text-xs text-muted">A key is stored but could not be opened. Enter it again.</p>
                ) : null}
              </div>
            ) : null}
          </fieldset>

          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn-primary" onClick={() => void onTest()} disabled={testing || saving}>
              {testing ? "Testing…" : "Test connection"}
            </button>
            <button type="button" className="btn-ghost" onClick={() => void onRefreshModels()} disabled={testing || saving}>
              Refresh models
            </button>
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            <ModelSelect
              id="ai-analysis-model"
              label="Analysis model"
              value={form.analysisModelId}
              models={models}
              onChange={(analysisModelId) => setForm({ ...form, analysisModelId })}
            />
            <ModelSelect
              id="ai-generation-model"
              label="Generation model"
              value={form.generationModelId}
              models={models}
              onChange={(generationModelId) => setForm({ ...form, generationModelId })}
            />
          </div>
          <p className="text-xs text-muted">
            Reviews can use a smaller model. Workout generation can use a larger one. Pricing
            stays on your Maple plan.
          </p>

          <fieldset className="space-y-2">
            <SectionHeader>Context level</SectionHeader>
            {CONTEXT_LEVELS.map((level) => (
              <label key={level.id} className="flex items-start gap-2 text-sm">
                <input
                  type="radio"
                  name="ai-context-level"
                  className="mt-1"
                  checked={form.contextLevel === level.id}
                  onChange={() => setForm({ ...form, contextLevel: level.id })}
                />
                <span>
                  <span className="font-medium text-heading">{level.label}</span>
                  <span className="block text-muted">{level.detail}</span>
                </span>
              </label>
            ))}
          </fieldset>

          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              className="mt-1"
              checked={form.alwaysShowContextPreview}
              onChange={(event) =>
                setForm({ ...form, alwaysShowContextPreview: event.target.checked })
              }
            />
            <span>Always show context preview before a coach request.</span>
          </label>

          {error ? (
            <p className="text-sm text-error" role="alert">
              {error}
            </p>
          ) : null}
          {message ? (
            <p className="text-sm text-[var(--erv-success)]" role="status">
              {message}
            </p>
          ) : null}

          <button type="button" className="btn-primary" onClick={() => void onSave()} disabled={saving || testing || sending}>
            {saving ? "Saving…" : "Save AI settings"}
          </button>

          <div className="space-y-2 border-t border-[var(--erv-outline-variant)] pt-4">
            <SectionHeader>Relay check</SectionHeader>
            <p className="text-sm text-muted">
              Sends one short message through this server to Maple and shows the reply as text.
              Enable the coach and save before sending.
            </p>
            <label className="block space-y-1" htmlFor="ai-test-message">
              <FieldLabel className="text-sm font-medium">Test message</FieldLabel>
              <textarea
                id="ai-test-message"
                className="input min-h-20 w-full text-sm"
                maxLength={2000}
                value={testMessage}
                onChange={(event) => setTestMessage(event.target.value)}
              />
            </label>
            <button
              type="button"
              className="btn-ghost"
              disabled={!form.enabled || sending || !testMessage.trim()}
              onClick={() => void onSendTest()}
            >
              {sending ? "Sending…" : "Send test message"}
            </button>
            {reply ? (
              <pre className="whitespace-pre-wrap rounded-card border border-[var(--erv-outline-variant)] bg-[var(--erv-input-bg)] p-3 text-sm text-heading">
                {reply}
              </pre>
            ) : null}
          </div>
        </div>
      ) : null}
    </section>
  );
}

function ModelSelect({
  id,
  label,
  value,
  models,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  models: string[];
  onChange: (value: string) => void;
}) {
  const options = value && !models.includes(value) ? [value, ...models] : models;
  return (
    <label className="block space-y-1" htmlFor={id}>
      <FieldLabel className="text-sm font-medium">{label}</FieldLabel>
      <select
        id={id}
        className="input w-full"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{options.length === 0 ? "Test connection to load models" : "Choose a model"}</option>
        {options.map((model) => (
          <option key={model} value={model}>
            {model}
          </option>
        ))}
      </select>
    </label>
  );
}
