import { ApiError } from "./api";

export type AiTask =
  | "analysis"
  | "workout_suggest_changes"
  | "workout_generate"
  | "plan_suggest";

export type AiCompletionRequest = {
  task: AiTask;
  model_id?: string;
  context: Record<string, unknown>;
  subject?: Record<string, unknown>;
  user_prompt?: string;
  response_format?: "markdown" | "json";
};

export type AiCompletion = {
  content: string;
  model: string;
  prompt_version: string;
  usage?: {
    prompt_tokens?: number;
    completion_tokens?: number;
  };
};

export type AiRelayStatus = {
  busy: boolean;
  queued: number;
  last_error: string | null;
};

export async function completeAi(body: AiCompletionRequest): Promise<AiCompletion> {
  return requestJson<AiCompletion>("/api/ai/complete", body);
}

export async function cancelAi(): Promise<{ cancelled: boolean }> {
  return requestJson("/api/ai/cancel", {});
}

export async function aiRelayStatus(): Promise<AiRelayStatus> {
  const res = await fetch("/api/ai/status", {
    credentials: "same-origin",
    headers: { Accept: "application/json" },
  });
  return readJson(res);
}

export async function streamAi(
  body: AiCompletionRequest,
  onEvent: (event: { event: string; data: string }) => void,
): Promise<void> {
  const res = await fetch("/api/ai/stream", {
    method: "POST",
    credentials: "same-origin",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    await readJson(res);
    return;
  }
  if (!res.body) {
    throw new ApiError(res.status, "The coach stream did not start.");
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const chunk = await reader.read();
    if (chunk.done) break;
    buffer += decoder.decode(chunk.value, { stream: true });
    buffer = buffer.replace(/\r\n/g, "\n");
    let split = buffer.indexOf("\n\n");
    while (split >= 0) {
      const raw = buffer.slice(0, split);
      buffer = buffer.slice(split + 2);
      const parsed = parseSseEvent(raw);
      if (parsed) onEvent(parsed);
      split = buffer.indexOf("\n\n");
    }
  }
}

async function requestJson<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(path, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  return readJson(res);
}

async function readJson<T>(res: Response): Promise<T> {
  const text = await res.text();
  let parsed: unknown = undefined;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = { error: text };
    }
  }
  if (!res.ok) {
    const message =
      (parsed as { error?: string } | undefined)?.error ?? `${res.status} ${res.statusText}`;
    throw new ApiError(res.status, message);
  }
  return parsed as T;
}

function parseSseEvent(raw: string): { event: string; data: string } | null {
  let event = "message";
  const data: string[] = [];
  for (const line of raw.split("\n")) {
    if (line.startsWith("event:")) event = sseValue(line, "event:");
    else if (line.startsWith("data:")) data.push(sseValue(line, "data:"));
  }
  if (data.length === 0) return null;
  return { event, data: data.join("\n") };
}

/** Drop only the single space SSE allows after the colon. Token text often starts with a space. */
function sseValue(line: string, field: string): string {
  const value = line.slice(field.length);
  return value.startsWith(" ") ? value.slice(1) : value;
}
