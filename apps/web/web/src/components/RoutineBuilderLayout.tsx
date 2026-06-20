import type { ReactNode } from "react";
import { LibrarySidebar } from "../components/LibrarySidebar";
import type { LibraryItemKind } from "../components/LibrarySidebar";

type RoutineBuilderLayoutProps = {
  sidebarKinds: LibraryItemKind[];
  selectionKind?: LibraryItemKind;
  selectedIds?: ReadonlySet<string>;
  onPick: Parameters<typeof LibrarySidebar>[0]["onPick"];
  pickLabel?: string;
  pickDisabled?: boolean;
  weightCatalog: Parameters<typeof LibrarySidebar>[0]["weightCatalog"];
  stretchCatalog: Parameters<typeof LibrarySidebar>[0]["stretchCatalog"];
  cardioCatalog: Parameters<typeof LibrarySidebar>[0]["cardioCatalog"];
  children: ReactNode;
};

export function RoutineBuilderLayout({
  sidebarKinds,
  selectionKind,
  selectedIds,
  onPick,
  pickLabel,
  pickDisabled,
  weightCatalog,
  stretchCatalog,
  cardioCatalog,
  children,
}: RoutineBuilderLayoutProps) {
  return (
    <div className="grid gap-4 lg:grid-cols-[minmax(240px,300px)_1fr] items-start">
      <LibrarySidebar
        className="lg:sticky lg:top-4"
        kinds={sidebarKinds}
        selectionKind={selectionKind}
        selectedIds={selectedIds}
        onPick={onPick}
        pickLabel={pickLabel}
        pickDisabled={pickDisabled}
        weightCatalog={weightCatalog}
        stretchCatalog={stretchCatalog}
        cardioCatalog={cardioCatalog}
      />
      <div className="space-y-4 min-w-0">{children}</div>
    </div>
  );
}
