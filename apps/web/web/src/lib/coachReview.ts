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
