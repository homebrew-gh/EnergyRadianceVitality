import { titleCaseWords } from "../lib/titleCase";

type FieldLabelProps = {
  children: string;
  className?: string;
};

/** Form field caption — multi-word labels render in title case. */
export function FieldLabel({ children, className }: FieldLabelProps) {
  return <span className={className}>{titleCaseWords(children)}</span>;
}

/** Section group heading (Flow, Lifting, Templates, …). */
export function SectionHeader({
  children,
  className = "text-xs font-semibold text-muted",
}: {
  children: string;
  className?: string;
}) {
  return <p className={className}>{titleCaseWords(children)}</p>;
}
