import { describe, expect, it } from "vitest";
import { coachReviewToMarkdown, parseCoachReviewJson } from "./coachReview";

const valid = {
  headline: "Training four days a week, but bench has stalled. Hold 185 and add a row set.",
  status: { consistency: "on_track", progress: "flat", recovery: "watch" },
  lifts: [
    { name: "Bench Press", trend: "flat", next: "185 × 8, 8, 8", why: "Reps fell to 6 at RPE 9.5." },
    { name: "Back Squat", trend: "up", next: "235 × 5, 5, 5", why: "Hit 6 on every set at 225." },
  ],
  nextWeek: [{ action: "Add a fourth set of rows.", why: "Pulling is 6 sets a week against 12 for pressing." }],
  wins: ["Squat is up 10 lb in two weeks."],
  watchOuts: [{ text: "Pressing outweighs pulling.", evidence: "12 vs 6 sets per week" }],
  bodyWeight: null,
  confidence: "medium",
  missingData: null,
};

describe("parseCoachReviewJson", () => {
  it("accepts a valid review", () => {
    const parsed = parseCoachReviewJson(JSON.stringify(valid));
    expect(parsed.ok).toBe(true);
    if (parsed.ok) {
      expect(parsed.review.lifts).toHaveLength(2);
      expect(parsed.review.status.recovery).toBe("watch");
    }
  });

  it("strips Markdown fences and surrounding text", () => {
    const parsed = parseCoachReviewJson(`Here you go:\n\`\`\`json\n${JSON.stringify(valid)}\n\`\`\``);
    expect(parsed.ok).toBe(true);
  });

  it("normalises loose enum values and fills defaults", () => {
    const parsed = parseCoachReviewJson(
      JSON.stringify({
        ...valid,
        status: { consistency: "On Track", progress: "great", recovery: "OK" },
        confidence: "very high",
        wins: undefined,
        nextWeek: ["Sleep eight hours."],
      }),
    );
    expect(parsed.ok).toBe(true);
    if (parsed.ok) {
      expect(parsed.review.status).toEqual({ consistency: "on_track", progress: "unknown", recovery: "ok" });
      expect(parsed.review.confidence).toBe("medium");
      expect(parsed.review.wins).toEqual([]);
      expect(parsed.review.nextWeek).toEqual([{ action: "Sleep eight hours.", why: "" }]);
    }
  });

  it("reports what is missing so the retry can fix it", () => {
    const parsed = parseCoachReviewJson(JSON.stringify({ ...valid, headline: "", nextWeek: [] }));
    expect(parsed).toEqual({
      ok: false,
      errors: ["headline must be a non-empty string.", "nextWeek must list at least one action."],
    });
    expect(parseCoachReviewJson("## Summary\nNo JSON here").ok).toBe(false);
    expect(parseCoachReviewJson("{not json}").ok).toBe(false);
  });

  it("drops lifts without a next load and removes ids", () => {
    const parsed = parseCoachReviewJson(
      JSON.stringify({
        ...valid,
        lifts: [
          { name: "Bench Press", trend: "flat", next: "", why: "" },
          { name: "Row 3f2b1c4d-1234-4abc-9def-0123456789ab", trend: "up", next: "155 × 10", why: "" },
        ],
      }),
    );
    expect(parsed.ok && parsed.review.lifts).toEqual([{ name: "Row", trend: "up", next: "155 × 10", why: "" }]);
  });
});

describe("coachReviewToMarkdown", () => {
  it("renders the headline, lifts and actions", () => {
    const parsed = parseCoachReviewJson(JSON.stringify(valid));
    if (!parsed.ok) throw new Error("expected a valid review");
    const markdown = coachReviewToMarkdown(parsed.review);
    expect(markdown).toContain("**Training four days a week");
    expect(markdown).toContain("- **Bench Press** (Flat): 185 × 8, 8, 8");
    expect(markdown).toContain("1. Add a fourth set of rows.");
  });
});
