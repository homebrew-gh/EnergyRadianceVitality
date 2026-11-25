export const COACH_REVIEW_SECTIONS = [
  "Summary",
  "What went well",
  "Watch-outs",
  "Suggested focus next week",
  "Data gaps",
] as const;

export type CoachReviewSection = {
  title: string;
  body: string;
};

const KNOWN = new Map(COACH_REVIEW_SECTIONS.map((title) => [normalizeHeading(title), title]));

export function parseCoachReview(markdown: string): {
  sections: CoachReviewSection[];
  droppedAfterUnknownHeading: boolean;
} {
  const lines = markdown.replace(/\r\n/g, "\n").split("\n");
  const sections: CoachReviewSection[] = [];
  let current: CoachReviewSection | null = null;
  let dropped = false;
  const preamble: string[] = [];

  for (const line of lines) {
    const heading = headingText(line);
    if (heading) {
      const known = KNOWN.get(normalizeHeading(heading));
      if (!known) {
        dropped = true;
        break;
      }
      if (current) sections.push(current);
      current = { title: known, body: "" };
      continue;
    }
    if (current) current.body += `${line}\n`;
    else preamble.push(line);
  }
  if (current) sections.push(current);

  const intro = preamble.join("\n").trim();
  if (sections.length === 0 && intro) {
    return {
      sections: [{ title: "Review", body: stripOpaqueIds(intro) }],
      droppedAfterUnknownHeading: dropped,
    };
  }
  return {
    sections: sections.map((section) => ({
      ...section,
      body: stripOpaqueIds(section.body.trim()),
    })),
    droppedAfterUnknownHeading: dropped,
  };
}

const OPAQUE_ID =
  /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;

/** Workout and custom-exercise ids are for later edits, not for the review text. */
export function stripOpaqueIds(text: string): string {
  return text
    .replace(OPAQUE_ID, "")
    .replace(/[ \t]{2,}/g, " ")
    .replace(/^[ \t]+/gm, "")
    .replace(/[ \t]+([,.;:])/g, "$1");
}

function headingText(line: string): string | null {
  const match = /^(#{1,3})\s+(.+?)\s*$/.exec(line.trim());
  return match?.[2]?.replace(/[#*_]+$/g, "").trim() || null;
}

function normalizeHeading(title: string): string {
  return title.trim().toLowerCase();
}

/* ---------- Review v3: structured JSON ---------- */

export type CoachConsistency = "on_track" | "behind" | "ahead" | "unknown";
export type CoachProgress = "improving" | "flat" | "slipping" | "unknown";
export type CoachRecovery = "ok" | "watch" | "unknown";
export type CoachConfidence = "high" | "medium" | "low";
export type CoachLiftTrend = "up" | "flat" | "down" | "new";

export type CoachReviewJson = {
  headline: string;
  status: { consistency: CoachConsistency; progress: CoachProgress; recovery: CoachRecovery };
  lifts: { name: string; trend: CoachLiftTrend; next: string; why: string }[];
  nextWeek: { action: string; why: string }[];
  wins: string[];
  watchOuts: { text: string; evidence: string }[];
  bodyWeight: string | null;
  confidence: CoachConfidence;
  missingData: string | null;
};

export type CoachReviewParse =
  | { ok: true; review: CoachReviewJson }
  | { ok: false; errors: string[] };

const MAX_LIFTS = 6;
const MAX_ACTIONS = 3;

/**
 * Parse and validate the model's JSON review. Tolerates Markdown fences and text around the
 * object, fills safe defaults for optional parts, and reports what is missing so one retry
 * can ask the model to fix it.
 */
export function parseCoachReviewJson(text: string): CoachReviewParse {
  if (stripThinking(text).trim() === "") return { ok: false, errors: ["The reply was empty."] };
  const raw = extractJsonObject(text);
  if (raw == null) return { ok: false, errors: ["The reply did not contain a JSON object."] };
  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch (err) {
    return {
      ok: false,
      errors: [`The JSON did not parse: ${err instanceof Error ? err.message : "syntax error"}.`],
    };
  }
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    return { ok: false, errors: ["The reply must be one JSON object."] };
  }
  const source = data as Record<string, unknown>;
  const errors: string[] = [];

  const headline = cleanText(source.headline);
  if (!headline) errors.push("headline must be a non-empty string.");

  const statusSource = isRecord(source.status) ? source.status : {};
  const status = {
    consistency: oneOf(statusSource.consistency, ["on_track", "behind", "ahead", "unknown"], "unknown"),
    progress: oneOf(statusSource.progress, ["improving", "flat", "slipping", "unknown"], "unknown"),
    recovery: oneOf(statusSource.recovery, ["ok", "watch", "unknown"], "unknown"),
  } as CoachReviewJson["status"];

  const lifts = arrayOf(source.lifts)
    .map((row) => {
      if (!isRecord(row)) return null;
      const name = cleanText(row.name);
      const next = cleanText(row.next);
      if (!name || !next) return null;
      return {
        name,
        trend: oneOf(row.trend, ["up", "flat", "down", "new"], "flat") as CoachLiftTrend,
        next,
        why: cleanText(row.why),
      };
    })
    .filter((row): row is CoachReviewJson["lifts"][number] => row != null)
    .slice(0, MAX_LIFTS);

  const nextWeek = arrayOf(source.nextWeek)
    .map((row) => {
      if (typeof row === "string") return cleanText(row) ? { action: cleanText(row), why: "" } : null;
      if (!isRecord(row)) return null;
      const action = cleanText(row.action);
      return action ? { action, why: cleanText(row.why) } : null;
    })
    .filter((row): row is CoachReviewJson["nextWeek"][number] => row != null)
    .slice(0, MAX_ACTIONS);
  if (nextWeek.length === 0) errors.push("nextWeek must list at least one action.");

  const wins = arrayOf(source.wins).map(cleanText).filter(Boolean).slice(0, 3);
  const watchOuts = arrayOf(source.watchOuts)
    .map((row) => {
      if (typeof row === "string") return cleanText(row) ? { text: cleanText(row), evidence: "" } : null;
      if (!isRecord(row)) return null;
      const text = cleanText(row.text);
      return text ? { text, evidence: cleanText(row.evidence) } : null;
    })
    .filter((row): row is CoachReviewJson["watchOuts"][number] => row != null)
    .slice(0, 3);

  if (errors.length > 0) return { ok: false, errors };
  return {
    ok: true,
    review: {
      headline,
      status,
      lifts,
      nextWeek,
      wins,
      watchOuts,
      bodyWeight: cleanText(source.bodyWeight) || null,
      confidence: oneOf(source.confidence, ["high", "medium", "low"], "medium") as CoachConfidence,
      missingData: cleanText(source.missingData) || null,
    },
  };
}

/** Plain Markdown copy of a v3 review, for the Copy button. */
export function coachReviewToMarkdown(review: CoachReviewJson): string {
  const lines = [`**${review.headline}**`, ""];
  lines.push(
    `Consistency: ${statusLabel(review.status.consistency)} · Progress: ${statusLabel(review.status.progress)} · Recovery: ${statusLabel(review.status.recovery)} · Confidence: ${review.confidence}`,
    "",
  );
  if (review.lifts.length > 0) {
    lines.push("## Next Session", "");
    for (const lift of review.lifts) {
      lines.push(`- **${lift.name}** (${liftTrendLabel(lift.trend)}): ${lift.next}${lift.why ? `. ${lift.why}` : ""}`);
    }
    lines.push("");
  }
  lines.push("## Next Week", "");
  review.nextWeek.forEach((item, index) => {
    lines.push(`${index + 1}. ${item.action}${item.why ? ` ${item.why}` : ""}`);
  });
  if (review.wins.length > 0) {
    lines.push("", "## What Went Well", "", ...review.wins.map((win) => `- ${win}`));
  }
  if (review.watchOuts.length > 0) {
    lines.push(
      "",
      "## Watch-Outs",
      "",
      ...review.watchOuts.map((item) => `- ${item.text}${item.evidence ? ` (${item.evidence})` : ""}`),
    );
  }
  if (review.bodyWeight) lines.push("", `Body weight: ${review.bodyWeight}`);
  if (review.missingData) lines.push("", `Missing data: ${review.missingData}`);
  return lines.join("\n");
}

export function statusLabel(value: string): string {
  switch (value) {
    case "on_track":
      return "on track";
    case "ok":
      return "OK";
    default:
      return value;
  }
}

export function liftTrendLabel(trend: CoachLiftTrend): string {
  switch (trend) {
    case "up":
      return "Up";
    case "down":
      return "Down";
    case "new":
      return "New";
    default:
      return "Flat";
  }
}

/** Some models put their reasoning in the reply as <think>…</think>; an unclosed block runs to the end. */
export function stripThinking(text: string): string {
  return text.replace(/<think>[\s\S]*?(<\/think>|$)/gi, "");
}

function extractJsonObject(text: string): string | null {
  const unfenced = stripThinking(text).replace(/```(?:json)?/gi, "").trim();
  const start = unfenced.indexOf("{");
  const end = unfenced.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  return unfenced.slice(start, end + 1);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function arrayOf(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function cleanText(value: unknown): string {
  return typeof value === "string" ? stripOpaqueIds(value).trim() : "";
}

function oneOf(value: unknown, allowed: string[], fallback: string): string {
  const text = typeof value === "string" ? value.trim().toLowerCase().replace(/[\s-]+/g, "_") : "";
  return allowed.includes(text) ? text : fallback;
}
