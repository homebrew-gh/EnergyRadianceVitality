import { useEffect, useId, useMemo, useState } from "react";
import { FieldLabel } from "./FieldLabel";
import { formatCategoryLabel } from "../lib/catalog";

const CUSTOM_VALUE = "__custom__";

type Props = {
  label: string;
  value: string;
  options: string[];
  onChange: (next: string) => void;
  customHint?: string;
  required?: boolean;
};

export function CatalogGroupSelect({
  label,
  value,
  options,
  onChange,
  customHint = "This label will be available for future items in this catalog.",
  required = true,
}: Props) {
  const selectId = useId();
  const customId = useId();
  const normalizedValue = value.trim().toLowerCase();
  const optionSet = useMemo(
    () => new Set(options.map((option) => option.trim().toLowerCase())),
    [options],
  );
  const startsCustom =
    normalizedValue.length > 0 && !optionSet.has(normalizedValue);

  const [mode, setMode] = useState<"preset" | "custom">(
    startsCustom ? "custom" : "preset",
  );
  const [customDraft, setCustomDraft] = useState(
    startsCustom ? normalizedValue : "",
  );

  useEffect(() => {
    if (normalizedValue && !optionSet.has(normalizedValue)) {
      setMode("custom");
      setCustomDraft(normalizedValue);
    } else if (normalizedValue && optionSet.has(normalizedValue)) {
      setMode("preset");
    }
  }, [normalizedValue, optionSet]);

  const selectValue =
    mode === "custom" || !normalizedValue || !optionSet.has(normalizedValue)
      ? mode === "custom"
        ? CUSTOM_VALUE
        : normalizedValue || options[0] || ""
      : normalizedValue;

  return (
    <div className="space-y-2">
      <label className="label block" htmlFor={selectId}>
        <FieldLabel>{label}</FieldLabel>
      </label>
      <select
        id={selectId}
        className="input w-full"
        value={selectValue}
        required={required && mode !== "custom"}
        onChange={(e) => {
          const next = e.target.value;
          if (next === CUSTOM_VALUE) {
            setMode("custom");
            if (customDraft.trim()) {
              onChange(customDraft.trim().toLowerCase());
            }
            return;
          }
          setMode("preset");
          setCustomDraft("");
          onChange(next);
        }}
      >
        {options.map((option) => (
          <option key={option} value={option}>
            {formatCategoryLabel(option)}
          </option>
        ))}
        <option value={CUSTOM_VALUE}>Add new group…</option>
      </select>
      {mode === "custom" ? (
        <div className="space-y-1">
          <label className="label block" htmlFor={customId}>
            <FieldLabel>New group name</FieldLabel>
          </label>
          <input
            id={customId}
            className="input w-full"
            value={customDraft}
            onChange={(e) => {
              const next = e.target.value;
              setCustomDraft(next);
              onChange(next.trim().toLowerCase());
            }}
            placeholder="e.g. forearms"
            required={required}
          />
          <p className="text-xs text-muted">{customHint}</p>
        </div>
      ) : null}
    </div>
  );
}
