import type { ReactNode } from "react";
import { LibrarySidebar } from "../components/LibrarySidebar";
import type { LibraryItemKind } from "../components/LibrarySidebar";
import type { OwnedEquipmentItem } from "../lib/fitnessEquipment";
import type { WeightExercisePickerFilter } from "../lib/weightExerciseAvailability";

type RoutineBuilderLayoutProps = {
  sidebarKinds: LibraryItemKind[];
  selectionKind?: LibraryItemKind;
  selectedIds?: ReadonlySet<string>;
  onPick: Parameters<typeof LibrarySidebar>[0]["onPick"];
  pickLabel?: string;
  pickDisabled?: boolean;
  enableWeightEquipmentFilter?: boolean;
  gymMembership?: boolean;
  ownedEquipment?: OwnedEquipmentItem[];
  enabledWeightExercisePackIds?: string[];
  weightPickerFilter?: WeightExercisePickerFilter;
  onWeightPickerFilterChange?: (filter: WeightExercisePickerFilter) => void;
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
  enableWeightEquipmentFilter,
  gymMembership,
  ownedEquipment,
  enabledWeightExercisePackIds,
  weightPickerFilter,
  onWeightPickerFilterChange,
  weightCatalog,
  stretchCatalog,
  cardioCatalog,
  children,
}: RoutineBuilderLayoutProps) {
  return (
    <div className="grid gap-5 lg:grid-cols-[minmax(260px,330px)_1fr] items-start">
      <LibrarySidebar
        key={sidebarKinds.join(",")}
        className="lg:sticky lg:top-24"
        kinds={sidebarKinds}
        selectionKind={selectionKind}
        selectedIds={selectedIds}
        onPick={onPick}
        pickLabel={pickLabel}
        pickDisabled={pickDisabled}
        enableWeightEquipmentFilter={enableWeightEquipmentFilter}
        gymMembership={gymMembership}
        ownedEquipment={ownedEquipment}
        enabledWeightExercisePackIds={enabledWeightExercisePackIds}
        weightPickerFilter={weightPickerFilter}
        onWeightPickerFilterChange={onWeightPickerFilterChange}
        weightCatalog={weightCatalog}
        stretchCatalog={stretchCatalog}
        cardioCatalog={cardioCatalog}
      />
      <div className="space-y-4 min-w-0">{children}</div>
    </div>
  );
}
