/**
 * Cardio for the Progress tab: weekly distance and time, and personal records. The records match
 * the phone's (CardioRecords.kt) group for group; the web never gets GPS routes, so a fastest
 * effort here is the whole session at its average pace, which the phone also falls back to.
 */
import type { CardioDayLog, CardioSession } from "./trainingHistory";
import { addDays, formatIsoDate, parseIsoDate, weekStartMonday } from "./trainingHistory";

const MILE = 1609.344;

export type CardioRecordGroupId = "RUN" | "RIDE" | "INDOOR_RIDE" | "RUCK" | "WALK" | "HIKE" | "ROW";

type GroupDef = {
  id: CardioRecordGroupId;
  label: string;
  builtins: string[];
  efforts: { name: string; meters: number }[];
  tracksClimbing: boolean;
};

export const CARDIO_RECORD_GROUPS: GroupDef[] = [
  {
    id: "RUN",
    label: "Run",
    builtins: ["RUN", "SPRINT"],
    efforts: [
      { name: "Mile", meters: MILE },
      { name: "5K", meters: 5000 },
      { name: "10K", meters: 10000 },
      { name: "Half Marathon", meters: 21097.5 },
    ],
    tracksClimbing: true,
  },
  {
    id: "RIDE",
    label: "Ride",
    builtins: ["BIKE"],
    efforts: [
      { name: "10 Miles", meters: 10 * MILE },
      { name: "25 Miles", meters: 25 * MILE },
      { name: "50 Miles", meters: 50 * MILE },
    ],
    tracksClimbing: true,
  },
  {
    id: "INDOOR_RIDE",
    label: "Indoor Ride",
    builtins: ["STATIONARY_BIKE"],
    efforts: [
      { name: "10 Miles", meters: 10 * MILE },
      { name: "20 Miles", meters: 20 * MILE },
    ],
    tracksClimbing: false,
  },
  {
    id: "RUCK",
    label: "Ruck",
    builtins: ["RUCK"],
    efforts: [
      { name: "Mile", meters: MILE },
      { name: "5K", meters: 5000 },
      { name: "12 Miles", meters: 12 * MILE },
    ],
    tracksClimbing: true,
  },
  {
    id: "WALK",
    label: "Walk",
    builtins: ["WALK"],
    efforts: [
      { name: "Mile", meters: MILE },
      { name: "5K", meters: 5000 },
    ],
    tracksClimbing: false,
  },
  { id: "HIKE", label: "Hike", builtins: ["HIKE"], efforts: [], tracksClimbing: true },
  {
    id: "ROW",
    label: "Row",
    builtins: ["ROWING"],
    efforts: [
      { name: "500 m", meters: 500 },
      { name: "2K", meters: 2000 },
      { name: "5K", meters: 5000 },
    ],
    tracksClimbing: false,
  },
];

export type CardioRecordKind =
  | { type: "FASTEST"; name: string; meters: number }
  | { type: "LONGEST_DISTANCE" }
  | { type: "LONGEST_TIME" }
  | { type: "MOST_CLIMBING" }
  | { type: "HEAVIEST_PACK" };

export type CardioRecord = {
  group: GroupDef;
  kind: CardioRecordKind;
  /** Seconds for FASTEST and LONGEST_TIME, meters for distance and climbing, kg for pack. */
  value: number;
  sessionId: string;
  date: string;
};

export function cardioRecordTitle(kind: CardioRecordKind): string {
  switch (kind.type) {
    case "FASTEST":
      return `Fastest ${kind.name}`;
    case "LONGEST_DISTANCE":
      return "Longest Distance";
    case "LONGEST_TIME":
      return "Longest Time";
    case "MOST_CLIMBING":
      return "Most Climbing";
    case "HEAVIEST_PACK":
      return "Heaviest Pack";
  }
}

function kindKey(kind: CardioRecordKind): string {
  return kind.type === "FASTEST" ? `FASTEST:${kind.meters}` : kind.type;
}

function kindOrder(group: GroupDef, kind: CardioRecordKind): number {
  switch (kind.type) {
    case "FASTEST":
      return group.efforts.findIndex((e) => e.meters === kind.meters);
    case "LONGEST_DISTANCE":
      return 100;
    case "LONGEST_TIME":
      return 101;
    case "MOST_CLIMBING":
      return 102;
    case "HEAVIEST_PACK":
      return 103;
  }
}

export function cardioRecordGroupOf(session: CardioSession): GroupDef | null {
  const builtin = session.activity.builtin;
  if (!builtin) return null;
  return CARDIO_RECORD_GROUPS.find((g) => g.builtins.includes(builtin)) ?? null;
}

/** Seconds from start to end when both are known, else the logged minutes. */
export function cardioSessionSeconds(session: CardioSession): number {
  const start = session.startEpochSeconds;
  const end = session.endEpochSeconds;
  if (start != null && end != null && end > start) return end - start;
  return session.durationMinutes * 60;
}

function recordsOf(session: CardioSession, date: string): CardioRecord[] {
  const group = cardioRecordGroupOf(session);
  if (!group || (session.segmentCount ?? 0) > 0) return [];
  const out: CardioRecord[] = [];
  const add = (kind: CardioRecordKind, value: number | null | undefined) => {
    if (value != null && Number.isFinite(value) && value > 0) {
      out.push({ group, kind, value, sessionId: session.id, date });
    }
  };
  const distance = session.distanceMeters != null && session.distanceMeters > 1 ? session.distanceMeters : null;
  const seconds = cardioSessionSeconds(session);
  for (const effort of group.efforts) {
    if (distance != null && seconds > 0 && distance + 0.5 >= effort.meters) {
      add({ type: "FASTEST", name: effort.name, meters: effort.meters }, (seconds * effort.meters) / distance);
    }
  }
  add({ type: "LONGEST_DISTANCE" }, distance);
  add({ type: "LONGEST_TIME" }, seconds);
  if (group.tracksClimbing && (session.elevationGainMeters ?? 0) >= 1) {
    add({ type: "MOST_CLIMBING" }, session.elevationGainMeters);
  }
  if (group.id === "RUCK") add({ type: "HEAVIEST_PACK" }, session.ruckLoadKg);
  return out;
}

function beats(record: CardioRecord, other: CardioRecord | undefined): boolean {
  if (!other) return true;
  return record.kind.type === "FASTEST" ? record.value < other.value - 0.5 : record.value > other.value + 0.5;
}

/** The best of each record across [logs], in group then record order. */
export function bestCardioRecords(logs: CardioDayLog[]): CardioRecord[] {
  const best = new Map<string, CardioRecord>();
  for (const log of logs) {
    for (const session of log.sessions) {
      for (const record of recordsOf(session, log.date)) {
        const key = `${record.group.id}|${kindKey(record.kind)}`;
        if (beats(record, best.get(key))) best.set(key, record);
      }
    }
  }
  const groupIndex = (g: GroupDef) => CARDIO_RECORD_GROUPS.findIndex((x) => x.id === g.id);
  return [...best.values()].sort(
    (a, b) => groupIndex(a.group) - groupIndex(b.group) || kindOrder(a.group, a.kind) - kindOrder(b.group, b.kind),
  );
}

export function formatClock(totalSeconds: number): string {
  const s = Math.max(0, Math.round(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return h > 0
    ? `${h}:${String(m).padStart(2, "0")}:${String(sec).padStart(2, "0")}`
    : `${m}:${String(sec).padStart(2, "0")}`;
}

/** "24:31", "13.10 mi", "820 ft", "45 lb", in the athlete's units. */
export function formatCardioRecordValue(
  record: CardioRecord,
  unit: "MI" | "KM",
  weightUnit: "LB" | "KG" = "LB",
): string {
  switch (record.kind.type) {
    case "FASTEST":
    case "LONGEST_TIME":
      return formatClock(record.value);
    case "LONGEST_DISTANCE":
      return unit === "KM" ? `${(record.value / 1000).toFixed(2)} km` : `${(record.value / MILE).toFixed(2)} mi`;
    case "MOST_CLIMBING":
      return unit === "KM" ? `${Math.round(record.value)} m` : `${Math.round(record.value * 3.28084)} ft`;
    case "HEAVIEST_PACK":
      return weightUnit === "KG" ? `${Math.round(record.value)} kg` : `${Math.round(record.value * 2.20462)} lb`;
  }
}

export type WeeklyCardioTotals = {
  weekStart: string;
  label: string;
  distanceMeters: number;
  seconds: number;
  sessions: number;
};

/**
 * Distance, time and count per Monday-start week, oldest first, with empty weeks filled in between
 * the first and last logged week (or for the last [weeks] weeks when given).
 */
export function weeklyCardioTotals(logs: CardioDayLog[], weeks: number | null, today = new Date()): WeeklyCardioTotals[] {
  const map = new Map<string, WeeklyCardioTotals>();
  const ensure = (weekStart: string) => {
    let bucket = map.get(weekStart);
    if (!bucket) {
      bucket = {
        weekStart,
        label: parseIsoDate(weekStart).toLocaleDateString(undefined, { month: "short", day: "numeric" }),
        distanceMeters: 0,
        seconds: 0,
        sessions: 0,
      };
      map.set(weekStart, bucket);
    }
    return bucket;
  };
  for (const log of logs) {
    const bucket = ensure(weekStartMonday(log.date));
    for (const session of log.sessions) {
      bucket.sessions += 1;
      bucket.seconds += cardioSessionSeconds(session);
      if (session.distanceMeters != null && session.distanceMeters > 1) bucket.distanceMeters += session.distanceMeters;
    }
  }
  const lastWeek = weekStartMonday(formatIsoDate(today));
  const starts = [...map.keys()].sort();
  const firstWeek =
    weeks != null ? addDays(lastWeek, -(weeks - 1) * 7) : (starts[0] ?? lastWeek);
  for (let week = firstWeek; week <= lastWeek; week = addDays(week, 7)) ensure(week);
  return [...map.values()]
    .filter((b) => b.weekStart >= firstWeek && b.weekStart <= lastWeek)
    .sort((a, b) => a.weekStart.localeCompare(b.weekStart));
}
