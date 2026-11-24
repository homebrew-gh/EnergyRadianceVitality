import type { ReactNode } from "react";

/**
 * The top of every page: its title, one line on what it is for, and its main buttons on the
 * right. Status lines (unpublished changes, errors) go in `children`, under the title row.
 */
export function PageHeader({
  title,
  description,
  actions,
  children,
}: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <header className="space-y-2">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1 basis-80 space-y-1">
          <h1 className="text-2xl font-bold text-heading">{title}</h1>
          {description ? <p className="max-w-3xl text-sm text-muted">{description}</p> : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
      {children}
    </header>
  );
}
