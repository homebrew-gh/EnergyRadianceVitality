import { useCallback, useEffect, useState } from "react";
import { kgToLb, lbToKg, type BodyWeightUnit } from "./fitnessEquipment";

const STORAGE_KEY = "erv-weight-training-load-unit";

export function weightLoadUnitSuffix(unit: BodyWeightUnit): "kg" | "lb" {
  return unit === "KG" ? "kg" : "lb";
}

export function formatWeightLoadNumber(kg: number, unit: BodyWeightUnit): string {
  const v = unit === "KG" ? kg : kgToLb(kg);
  return v % 1 === 0 ? String(Math.round(v)) : v.toFixed(1);
}

/** Parse user input in the chosen unit to stored kg, or `null` if blank/invalid. */
export function parseWeightInputToKg(raw: string, unit: BodyWeightUnit): number | null {
  const t = raw.trim().replace(",", ".");
  if (!t) return null;
  const v = Number.parseFloat(t);
  if (!Number.isFinite(v) || v < 0) return null;
  return unit === "KG" ? v : lbToKg(v);
}

export function readStoredWeightLoadUnit(): BodyWeightUnit {
  if (typeof window === "undefined") return "LB";
  const raw = window.localStorage.getItem(STORAGE_KEY)?.trim().toUpperCase();
  return raw === "KG" ? "KG" : "LB";
}

export function writeStoredWeightLoadUnit(unit: BodyWeightUnit): void {
  window.localStorage.setItem(STORAGE_KEY, unit);
}

export function useWeightLoadUnit(): [BodyWeightUnit, (unit: BodyWeightUnit) => void] {
  const [unit, setUnitState] = useState<BodyWeightUnit>(() => readStoredWeightLoadUnit());

  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY) {
        setUnitState(readStoredWeightLoadUnit());
      }
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const setUnit = useCallback((next: BodyWeightUnit) => {
    writeStoredWeightLoadUnit(next);
    setUnitState(next);
  }, []);

  return [unit, setUnit];
}
