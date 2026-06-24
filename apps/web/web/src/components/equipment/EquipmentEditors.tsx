import { useMemo, useState, type FormEvent, type ReactNode } from "react";
import { FieldLabel, SectionHeader } from "../FieldLabel";
import {
  BAND_TIERS,
  BATTLE_ROPE_HEFT,
  BENCH_TYPES,
  benchTypeLabel,
  bandTierLabel,
  battleRopeHeftLabel,
  buildCatalogItem,
  CARDIO_MACHINES,
  cardioMachineLabel,
  CABLE_STATION_TYPES,
  cableStationLabel,
  defaultModalities,
  FITNESS_EQUIPMENT_PRESETS,
  formatWeightValue,
  JUMP_ROPE_STYLES,
  jumpRopeStyleLabel,
  parsePositiveWeightToKg,
  PLYO_BOX_KINDS,
  plyoBoxKindLabel,
  PULL_UP_OPTIONS,
  pullUpOptionLabel,
  STANDARD_BARBELL_TYPES,
  standardBarbellLabel,
  SQUAT_RACK_TYPES,
  squatRackTypeLabel,
  SUSPENSION_ANCHORS,
  suspensionAnchorLabel,
  WORKOUT_MODALITIES,
  workoutModalityLabel,
  type BandOwnership,
  type BarbellOwnership,
  type BattleRopeOwnership,
  type BenchOwnership,
  type BodyWeightUnit,
  type CableStationOwnership,
  type CardioMachinesOwnership,
  type DumbbellOwnership,
  type EquipmentCatalogKind,
  type JumpRopeOwnership,
  type KettlebellOwnership,
  type MedicineBallOwnership,
  type MobilityToolsOwnership,
  type OwnedEquipmentItem,
  type ParallettesRingsOwnership,
  type PlateOwnership,
  type PlatePairEntry,
  type PlyoBoxOwnership,
  type PullUpOwnership,
  type SquatRackOwnership,
  type StandardBarbellType,
  type SuspensionTrainerOwnership,
  type WorkoutModality,
} from "../../lib/fitnessEquipment";

export type EquipmentEditorState =
  | { kind: "manual"; existingId: string | null; nameDraft: string; modalities: WorkoutModality[] }
  | { kind: "barbell"; existingId: string | null; initial: BarbellOwnership | null; modalities: WorkoutModality[] }
  | { kind: "dumbbells"; existingId: string | null; initial: DumbbellOwnership | null; modalities: WorkoutModality[] }
  | { kind: "kettlebells"; existingId: string | null; initial: KettlebellOwnership | null; modalities: WorkoutModality[] }
  | { kind: "plates"; existingId: string | null; initial: PlateOwnership | null; modalities: WorkoutModality[] }
  | { kind: "bands"; existingId: string | null; initial: BandOwnership | null; modalities: WorkoutModality[] }
  | { kind: "bench"; existingId: string | null; initial: BenchOwnership | null; modalities: WorkoutModality[] }
  | { kind: "squatRack"; existingId: string | null; initial: SquatRackOwnership | null; modalities: WorkoutModality[] }
  | { kind: "pullUp"; existingId: string | null; initial: PullUpOwnership | null; modalities: WorkoutModality[] }
  | { kind: "cardioMachines"; existingId: string | null; initial: CardioMachinesOwnership | null; modalities: WorkoutModality[] }
  | { kind: "cableStation"; existingId: string | null; initial: CableStationOwnership | null; modalities: WorkoutModality[] }
  | { kind: "jumpRope"; existingId: string | null; initial: JumpRopeOwnership | null; modalities: WorkoutModality[] }
  | { kind: "medicineBall"; existingId: string | null; initial: MedicineBallOwnership | null; modalities: WorkoutModality[] }
  | { kind: "suspensionTrainer"; existingId: string | null; initial: SuspensionTrainerOwnership | null; modalities: WorkoutModality[] }
  | { kind: "plyoBox"; existingId: string | null; initial: PlyoBoxOwnership | null; modalities: WorkoutModality[] }
  | { kind: "battleRopes"; existingId: string | null; initial: BattleRopeOwnership | null; modalities: WorkoutModality[] }
  | { kind: "mobilityTools"; existingId: string | null; initial: MobilityToolsOwnership | null; modalities: WorkoutModality[] }
  | { kind: "parallettesRings"; existingId: string | null; initial: ParallettesRingsOwnership | null; modalities: WorkoutModality[] };

export function editorStateForItem(item: OwnedEquipmentItem): EquipmentEditorState {
  const kind = item.catalogKind ?? "MANUAL";
  const modalities = item.modalities ?? [];
  switch (kind) {
    case "MANUAL":
      return { kind: "manual", existingId: item.id, nameDraft: item.name, modalities };
    case "BARBELL":
      return { kind: "barbell", existingId: item.id, initial: item.barbell ?? null, modalities };
    case "DUMBBELLS":
      return { kind: "dumbbells", existingId: item.id, initial: item.dumbbells ?? null, modalities };
    case "KETTLEBELLS":
      return { kind: "kettlebells", existingId: item.id, initial: item.kettlebells ?? null, modalities };
    case "PLATES":
      return { kind: "plates", existingId: item.id, initial: item.plates ?? null, modalities };
    case "BANDS":
      return { kind: "bands", existingId: item.id, initial: item.bands ?? null, modalities };
    case "BENCH":
      return { kind: "bench", existingId: item.id, initial: item.bench ?? null, modalities };
    case "SQUAT_RACK":
      return { kind: "squatRack", existingId: item.id, initial: item.squatRack ?? null, modalities };
    case "PULL_UP_DIP":
      return { kind: "pullUp", existingId: item.id, initial: item.pullUp ?? null, modalities };
    case "CARDIO_MACHINES":
      return { kind: "cardioMachines", existingId: item.id, initial: item.cardioMachines ?? null, modalities };
    case "CABLE_STATION":
      return { kind: "cableStation", existingId: item.id, initial: item.cableStation ?? null, modalities };
    case "JUMP_ROPE":
      return { kind: "jumpRope", existingId: item.id, initial: item.jumpRope ?? null, modalities };
    case "MEDICINE_BALL":
      return { kind: "medicineBall", existingId: item.id, initial: item.medicineBalls ?? null, modalities };
    case "SUSPENSION_TRAINER":
      return { kind: "suspensionTrainer", existingId: item.id, initial: item.suspensionTrainer ?? null, modalities };
    case "PLYO_BOX":
      return { kind: "plyoBox", existingId: item.id, initial: item.plyoBox ?? null, modalities };
    case "BATTLE_ROPES":
      return { kind: "battleRopes", existingId: item.id, initial: item.battleRopes ?? null, modalities };
    case "MOBILITY_TOOLS":
      return { kind: "mobilityTools", existingId: item.id, initial: item.mobilityTools ?? null, modalities };
    case "PARALLETTE_RINGS":
      return { kind: "parallettesRings", existingId: item.id, initial: item.parallettesRings ?? null, modalities };
    default:
      return { kind: "manual", existingId: item.id, nameDraft: item.name, modalities };
  }
}

export function editorStateForQuickAdd(catalogKind: EquipmentCatalogKind): EquipmentEditorState {
  const modalities = defaultModalities(catalogKind);
  switch (catalogKind) {
    case "BARBELL":
      return { kind: "barbell", existingId: null, initial: null, modalities };
    case "DUMBBELLS":
      return { kind: "dumbbells", existingId: null, initial: null, modalities };
    case "KETTLEBELLS":
      return { kind: "kettlebells", existingId: null, initial: null, modalities };
    case "PLATES":
      return { kind: "plates", existingId: null, initial: null, modalities };
    case "BANDS":
      return { kind: "bands", existingId: null, initial: null, modalities };
    case "BENCH":
      return { kind: "bench", existingId: null, initial: null, modalities };
    case "SQUAT_RACK":
      return { kind: "squatRack", existingId: null, initial: null, modalities };
    case "PULL_UP_DIP":
      return { kind: "pullUp", existingId: null, initial: null, modalities };
    case "CARDIO_MACHINES":
      return { kind: "cardioMachines", existingId: null, initial: null, modalities };
    case "CABLE_STATION":
      return { kind: "cableStation", existingId: null, initial: null, modalities };
    case "JUMP_ROPE":
      return { kind: "jumpRope", existingId: null, initial: null, modalities };
    case "MEDICINE_BALL":
      return { kind: "medicineBall", existingId: null, initial: null, modalities };
    case "SUSPENSION_TRAINER":
      return { kind: "suspensionTrainer", existingId: null, initial: null, modalities };
    case "PLYO_BOX":
      return { kind: "plyoBox", existingId: null, initial: null, modalities };
    case "BATTLE_ROPES":
      return { kind: "battleRopes", existingId: null, initial: null, modalities };
    case "MOBILITY_TOOLS":
      return { kind: "mobilityTools", existingId: null, initial: null, modalities };
    case "PARALLETTE_RINGS":
      return { kind: "parallettesRings", existingId: null, initial: null, modalities };
    default:
      return { kind: "manual", existingId: null, nameDraft: "", modalities: ["WEIGHT_TRAINING"] };
  }
}

type EquipmentEditorModalProps = {
  editor: EquipmentEditorState;
  weightUnit: BodyWeightUnit;
  onClose: () => void;
  onSave: (item: OwnedEquipmentItem) => void;
};

export function EquipmentEditorModal({
  editor,
  weightUnit,
  onClose,
  onSave,
}: EquipmentEditorModalProps) {
  switch (editor.kind) {
    case "manual":
      return (
        <ManualEditor
          existingId={editor.existingId}
          nameDraft={editor.nameDraft}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(name, modalities) => {
            const trimmed = name.trim();
            if (!trimmed) return;
            onSave({
              id: editor.existingId ?? crypto.randomUUID(),
              name: trimmed,
              modalities,
              catalogKind: "MANUAL",
            });
          }}
        />
      );
    case "barbell":
      return (
        <BarbellEditor
          existingId={editor.existingId}
          initial={editor.initial}
          modalities={editor.modalities}
          weightUnit={weightUnit}
          onClose={onClose}
          onSave={onSave}
        />
      );
    case "dumbbells":
      return (
        <DumbbellEditor
          existingId={editor.existingId}
          initial={editor.initial}
          modalities={editor.modalities}
          weightUnit={weightUnit}
          onClose={onClose}
          onSave={onSave}
        />
      );
    case "kettlebells":
      return (
        <KettlebellEditor
          existingId={editor.existingId}
          initial={editor.initial}
          modalities={editor.modalities}
          weightUnit={weightUnit}
          onClose={onClose}
          onSave={onSave}
        />
      );
    case "plates":
      return (
        <PlatesEditor
          existingId={editor.existingId}
          initial={editor.initial}
          modalities={editor.modalities}
          weightUnit={weightUnit}
          onClose={onClose}
          onSave={onSave}
        />
      );
    case "bands":
      return (
        <BandsEditor
          existingId={editor.existingId}
          initial={editor.initial}
          modalities={editor.modalities}
          weightUnit={weightUnit}
          onClose={onClose}
          onSave={onSave}
        />
      );
    case "bench":
      return (
        <RadioCatalogEditor
          title={editor.existingId ? "Edit bench" : "Add bench"}
          options={BENCH_TYPES.map((value) => ({ value, label: benchTypeLabel(value) }))}
          initial={editor.initial?.benchType ?? "FLAT_ONLY"}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(benchType, modalities) =>
            onSave(
              buildCatalogItem(editor.existingId, "BENCH", weightUnit, modalities, (base) => ({
                ...base,
                bench: { benchType },
              })),
            )
          }
        />
      );
    case "squatRack":
      return (
        <RadioCatalogEditor
          title={editor.existingId ? "Edit rack / cage" : "Add rack / cage"}
          options={SQUAT_RACK_TYPES.map((value) => ({ value, label: squatRackTypeLabel(value) }))}
          initial={editor.initial?.rackType ?? "HALF_RACK"}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(rackType, modalities) =>
            onSave(
              buildCatalogItem(editor.existingId, "SQUAT_RACK", weightUnit, modalities, (base) => ({
                ...base,
                squatRack: { rackType },
              })),
            )
          }
        />
      );
    case "pullUp":
      return (
        <MultiSelectEditor
          title={editor.existingId ? "Edit pull-up / dip" : "Add pull-up / dip"}
          options={PULL_UP_OPTIONS.map((value) => ({ value, label: pullUpOptionLabel(value) }))}
          initial={editor.initial?.options ?? []}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(options, modalities) =>
            onSave(
              buildCatalogItem(editor.existingId, "PULL_UP_DIP", weightUnit, modalities, (base) => ({
                ...base,
                pullUp: { options },
              })),
            )
          }
        />
      );
    case "cardioMachines":
      return (
        <MultiSelectEditor
          title={editor.existingId ? "Edit cardio machines" : "Add cardio machines"}
          options={CARDIO_MACHINES.map((value) => ({ value, label: cardioMachineLabel(value) }))}
          initial={editor.initial?.machines ?? []}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(machines, modalities) =>
            onSave(
              buildCatalogItem(
                editor.existingId,
                "CARDIO_MACHINES",
                weightUnit,
                modalities,
                (base) => ({
                  ...base,
                  cardioMachines: { machines },
                }),
              ),
            )
          }
        />
      );
    case "cableStation":
      return (
        <RadioCatalogEditor
          title={editor.existingId ? "Edit cable station" : "Add cable station"}
          options={CABLE_STATION_TYPES.map((value) => ({ value, label: cableStationLabel(value) }))}
          initial={editor.initial?.stationType ?? "DUAL_ADJUSTABLE"}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(stationType, modalities) =>
            onSave(
              buildCatalogItem(
                editor.existingId,
                "CABLE_STATION",
                weightUnit,
                modalities,
                (base) => ({
                  ...base,
                  cableStation: { stationType },
                }),
              ),
            )
          }
        />
      );
    case "jumpRope":
      return (
        <MultiSelectEditor
          title={editor.existingId ? "Edit jump rope" : "Add jump rope"}
          options={JUMP_ROPE_STYLES.map((value) => ({ value, label: jumpRopeStyleLabel(value) }))}
          initial={editor.initial?.styles ?? []}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(styles, modalities) =>
            onSave(
              buildCatalogItem(editor.existingId, "JUMP_ROPE", weightUnit, modalities, (base) => ({
                ...base,
                jumpRope: { styles },
              })),
            )
          }
        />
      );
    case "medicineBall":
      return (
        <WeightChipEditor
          title={editor.existingId ? "Edit medicine / slam balls" : "Add medicine / slam balls"}
          initial={editor.initial?.ballWeightsKg ?? []}
          presets={
            weightUnit === "LB"
              ? FITNESS_EQUIPMENT_PRESETS.medicineBallsLb()
              : FITNESS_EQUIPMENT_PRESETS.medicineBallsKg()
          }
          modalities={editor.modalities}
          weightUnit={weightUnit}
          onClose={onClose}
          onSave={(ballWeightsKg, modalities) =>
            onSave(
              buildCatalogItem(
                editor.existingId,
                "MEDICINE_BALL",
                weightUnit,
                modalities,
                (base) => ({
                  ...base,
                  medicineBalls: { ballWeightsKg },
                }),
              ),
            )
          }
        />
      );
    case "suspensionTrainer":
      return (
        <MultiSelectEditor
          title={editor.existingId ? "Edit suspension trainer" : "Add suspension trainer"}
          options={SUSPENSION_ANCHORS.map((value) => ({ value, label: suspensionAnchorLabel(value) }))}
          initial={editor.initial?.anchors ?? []}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(anchors, modalities) =>
            onSave(
              buildCatalogItem(
                editor.existingId,
                "SUSPENSION_TRAINER",
                weightUnit,
                modalities,
                (base) => ({
                  ...base,
                  suspensionTrainer: { anchors },
                }),
              ),
            )
          }
        />
      );
    case "plyoBox":
      return (
        <MultiSelectEditor
          title={editor.existingId ? "Edit plyo box" : "Add plyo box"}
          options={PLYO_BOX_KINDS.map((value) => ({ value, label: plyoBoxKindLabel(value) }))}
          initial={editor.initial?.kinds ?? []}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(kinds, modalities) =>
            onSave(
              buildCatalogItem(editor.existingId, "PLYO_BOX", weightUnit, modalities, (base) => ({
                ...base,
                plyoBox: { kinds },
              })),
            )
          }
        />
      );
    case "battleRopes":
      return (
        <MultiSelectEditor
          title={editor.existingId ? "Edit battle ropes" : "Add battle ropes"}
          options={BATTLE_ROPE_HEFT.map((value) => ({ value, label: battleRopeHeftLabel(value) }))}
          initial={editor.initial?.heft ?? []}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(heft, modalities) =>
            onSave(
              buildCatalogItem(
                editor.existingId,
                "BATTLE_ROPES",
                weightUnit,
                modalities,
                (base) => ({
                  ...base,
                  battleRopes: { heft },
                }),
              ),
            )
          }
        />
      );
    case "mobilityTools":
      return (
        <CheckboxEditor
          title={editor.existingId ? "Edit mobility tools" : "Add mobility tools"}
          options={[
            { key: "foamRoller", label: "Foam roller" },
            { key: "lacrosseBall", label: "Lacrosse ball" },
            { key: "peanutBall", label: "Peanut / double ball" },
            { key: "massageGun", label: "Massage gun" },
          ]}
          initial={editor.initial ?? {}}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(values, modalities) =>
            onSave(
              buildCatalogItem(
                editor.existingId,
                "MOBILITY_TOOLS",
                weightUnit,
                modalities,
                (base) => ({
                  ...base,
                  mobilityTools: values,
                }),
              ),
            )
          }
        />
      );
    case "parallettesRings":
      return (
        <CheckboxEditor
          title={editor.existingId ? "Edit parallettes / rings" : "Add parallettes / rings"}
          options={[
            { key: "parallettes", label: "Parallettes" },
            { key: "gymnasticRings", label: "Gymnastic rings" },
          ]}
          initial={editor.initial ?? {}}
          modalities={editor.modalities}
          onClose={onClose}
          onSave={(values, modalities) =>
            onSave(
              buildCatalogItem(
                editor.existingId,
                "PARALLETTE_RINGS",
                weightUnit,
                modalities,
                (base) => ({
                  ...base,
                  parallettesRings: values,
                }),
              ),
            )
          }
        />
      );
  }
}

function ModalShell({
  title,
  onClose,
  onSubmit,
  canSave = true,
  children,
}: {
  title: string;
  onClose: () => void;
  onSubmit: (event: FormEvent) => void;
  canSave?: boolean;
  children: ReactNode;
}) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
      role="presentation"
    >
      <div
        className="card w-full max-w-lg max-h-[90vh] overflow-y-auto p-4 space-y-4"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-lg font-semibold text-heading">{title}</h3>
          <button type="button" className="btn-ghost text-sm py-1 px-2" onClick={onClose}>
            Close
          </button>
        </div>
        <form className="space-y-4" onSubmit={onSubmit}>
          {children}
          <div className="flex gap-2 justify-end">
            <button type="button" className="btn-ghost text-sm" onClick={onClose}>
              Cancel
            </button>
            <button type="submit" className="btn-primary text-sm" disabled={!canSave}>
              Save
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function ModalityPicker({
  modalities,
  onChange,
}: {
  modalities: WorkoutModality[];
  onChange: (next: WorkoutModality[]) => void;
}) {
  return (
    <div className="space-y-2">
      <SectionHeader>Useful for</SectionHeader>
      <div className="flex flex-wrap gap-2">
        {WORKOUT_MODALITIES.map((m) => {
          const selected = modalities.includes(m);
          return (
            <button
              key={m}
              type="button"
              className={selected ? "btn-primary text-xs py-1 px-2" : "btn-ghost text-xs py-1 px-2"}
              onClick={() =>
                onChange(selected ? modalities.filter((x) => x !== m) : [...modalities, m])
              }
            >
              {workoutModalityLabel(m)}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function ManualEditor({
  existingId,
  nameDraft,
  modalities: initialModalities,
  onClose,
  onSave,
}: {
  existingId: string | null;
  nameDraft: string;
  modalities: WorkoutModality[];
  onClose: () => void;
  onSave: (name: string, modalities: WorkoutModality[]) => void;
}) {
  const [name, setName] = useState(nameDraft);
  const [modalities, setModalities] = useState(initialModalities);
  return (
    <ModalShell
      title={existingId ? "Edit equipment" : "Add other equipment"}
      onClose={onClose}
      canSave={name.trim().length > 0}
      onSubmit={(e) => {
        e.preventDefault();
        onSave(name, modalities);
        onClose();
      }}
    >
      <label className="label block space-y-1">
        <FieldLabel>Name</FieldLabel>
        <input
          className="input w-full"
          value={name}
          maxLength={100}
          onChange={(e) => setName(e.target.value)}
          required
        />
      </label>
      <ModalityPicker modalities={modalities} onChange={setModalities} />
    </ModalShell>
  );
}

function BarbellEditor({
  existingId,
  initial,
  modalities: initialModalities,
  weightUnit,
  onClose,
  onSave,
}: {
  existingId: string | null;
  initial: BarbellOwnership | null;
  modalities: WorkoutModality[];
  weightUnit: BodyWeightUnit;
  onClose: () => void;
  onSave: (item: OwnedEquipmentItem) => void;
}) {
  const [barType, setBarType] = useState<StandardBarbellType>(initial?.barType ?? "OLYMPIC_MENS");
  const [customName, setCustomName] = useState(initial?.customName?.trim() ?? "");
  const [customWeight, setCustomWeight] = useState(
    initial?.customWeightKg != null
      ? String(
          weightUnit === "KG"
            ? initial.customWeightKg
            : Math.round(initial.customWeightKg * 2.2046226218 * 10) / 10,
        )
      : "",
  );
  const [modalities, setModalities] = useState(initialModalities);
  const customKg = parsePositiveWeightToKg(customWeight, weightUnit);
  const canSave = barType !== "CUSTOM" || (customKg != null && customName.trim().length > 0);

  return (
    <ModalShell
      title={existingId ? "Edit barbell" : "Add barbell"}
      onClose={onClose}
      canSave={canSave}
      onSubmit={(e) => {
        e.preventDefault();
        if (!canSave) return;
        const ownership: BarbellOwnership = {
          barType,
          customWeightKg: barType === "CUSTOM" ? customKg : null,
          customName: barType === "CUSTOM" ? customName.trim() : null,
        };
        onSave(
          buildCatalogItem(existingId, "BARBELL", weightUnit, modalities, (base) => ({
            ...base,
            barbell: ownership,
          })),
        );
        onClose();
      }}
    >
      <p className="text-sm text-muted">
        Pick the bar you use most often. Typical unloaded weights are shown for common bars.
      </p>
      <div className="space-y-2">
        {STANDARD_BARBELL_TYPES.map((type) => (
          <label key={type} className="flex items-center gap-2 text-sm">
            <input
              type="radio"
              name="barbell-type"
              checked={barType === type}
              onChange={() => setBarType(type)}
            />
            {standardBarbellLabel(type)}
          </label>
        ))}
      </div>
      {barType === "CUSTOM" ? (
        <>
          <label className="label block space-y-1">
            <FieldLabel>Bar name</FieldLabel>
            <input
              className="input w-full"
              value={customName}
              onChange={(e) => setCustomName(e.target.value)}
              placeholder="e.g. REP Open Trap, Eleiko, garage bar"
            />
          </label>
          <label className="label block space-y-1">
            <FieldLabel>{weightUnit === "KG" ? "Bar weight (kg)" : "Bar weight (lb)"}</FieldLabel>
            <input
              className="input w-full"
              inputMode="decimal"
              value={customWeight}
              onChange={(e) => setCustomWeight(e.target.value)}
            />
          </label>
        </>
      ) : null}
      <ModalityPicker modalities={modalities} onChange={setModalities} />
    </ModalShell>
  );
}

function kgMatchesPreset(kg: number, presetKg: number): boolean {
  return Math.abs(kg - presetKg) < 0.06;
}

function DumbbellEditor({
  existingId,
  initial,
  modalities: initialModalities,
  weightUnit,
  onClose,
  onSave,
}: {
  existingId: string | null;
  initial: DumbbellOwnership | null;
  modalities: WorkoutModality[];
  weightUnit: BodyWeightUnit;
  onClose: () => void;
  onSave: (item: OwnedEquipmentItem) => void;
}) {
  const standardPresets = useMemo(
    () =>
      weightUnit === "LB"
        ? FITNESS_EQUIPMENT_PRESETS.dumbbellPairsLb()
        : FITNESS_EQUIPMENT_PRESETS.dumbbellPairsKg(),
    [weightUnit],
  );
  const heavyPresets = useMemo(
    () =>
      weightUnit === "LB"
        ? FITNESS_EQUIPMENT_PRESETS.dumbbellPairsLbHeavy()
        : FITNESS_EQUIPMENT_PRESETS.dumbbellPairsKgHeavy(),
    [weightUnit],
  );
  const [mode, setMode] = useState(initial?.mode ?? "FIXED_PAIRS");
  const [selectedPairs, setSelectedPairs] = useState<number[]>(initial?.pairWeightsKg ?? []);
  const [showHeavy, setShowHeavy] = useState(
    () =>
      (initial?.pairWeightsKg ?? []).some((s) =>
        heavyPresets.some((h) => kgMatchesPreset(s, h)),
      ),
  );
  const [selMin, setSelMin] = useState(
    initial?.selectorizedMinKg != null
      ? String(
          weightUnit === "KG"
            ? initial.selectorizedMinKg
            : Math.round(initial.selectorizedMinKg * 2.2046226218),
        )
      : "5",
  );
  const [selMax, setSelMax] = useState(
    initial?.selectorizedMaxKg != null
      ? String(
          weightUnit === "KG"
            ? initial.selectorizedMaxKg
            : Math.round(initial.selectorizedMaxKg * 2.2046226218),
        )
      : weightUnit === "KG"
        ? "25"
        : "50",
  );
  const [selInc, setSelInc] = useState(
    initial?.selectorizedIncrementKg != null
      ? String(
          weightUnit === "KG"
            ? initial.selectorizedIncrementKg
            : Math.round(initial.selectorizedIncrementKg * 2.2046226218),
        )
      : weightUnit === "KG"
        ? "2.5"
        : "5",
  );
  const [modalities, setModalities] = useState(initialModalities);

  const minKg = parsePositiveWeightToKg(selMin, weightUnit);
  const maxKg = parsePositiveWeightToKg(selMax, weightUnit);
  const incKg = parsePositiveWeightToKg(selInc, weightUnit);
  const canSaveFixed = mode !== "FIXED_PAIRS" || selectedPairs.length > 0;
  const canSaveSelectorized =
    mode !== "SELECTORIZED" ||
    (minKg != null &&
      maxKg != null &&
      incKg != null &&
      maxKg > minKg &&
      incKg <= maxKg - minKg);
  const canSave = canSaveFixed && canSaveSelectorized;

  const togglePair = (kg: number) => {
    setSelectedPairs((prev) => {
      const exists = prev.some((s) => kgMatchesPreset(s, kg));
      if (exists) return prev.filter((s) => !kgMatchesPreset(s, kg));
      return [...prev, kg];
    });
  };

  return (
    <ModalShell
      title={existingId ? "Edit dumbbells" : "Add dumbbells"}
      onClose={onClose}
      canSave={canSave}
      onSubmit={(e) => {
        e.preventDefault();
        if (!canSave) return;
        const ownership: DumbbellOwnership =
          mode === "FIXED_PAIRS"
            ? { mode, pairWeightsKg: selectedPairs }
            : {
                mode,
                selectorizedMinKg: minKg,
                selectorizedMaxKg: maxKg,
                selectorizedIncrementKg: incKg,
              };
        onSave(
          buildCatalogItem(existingId, "DUMBBELLS", weightUnit, modalities, (base) => ({
            ...base,
            dumbbells: ownership,
          })),
        );
        onClose();
      }}
    >
      <div className="flex gap-2">
        <button
          type="button"
          className={mode === "FIXED_PAIRS" ? "btn-primary text-sm" : "btn-ghost text-sm"}
          onClick={() => setMode("FIXED_PAIRS")}
        >
          Fixed pairs
        </button>
        <button
          type="button"
          className={mode === "SELECTORIZED" ? "btn-primary text-sm" : "btn-ghost text-sm"}
          onClick={() => setMode("SELECTORIZED")}
        >
          Selectorized
        </button>
      </div>
      {mode === "FIXED_PAIRS" ? (
        <>
          <SectionHeader>Standard pairs</SectionHeader>
          <div className="flex flex-wrap gap-2">
            {standardPresets.map((kg) => {
              const selected = selectedPairs.some((s) => kgMatchesPreset(s, kg));
              return (
                <button
                  key={kg}
                  type="button"
                  className={selected ? "btn-primary text-xs py-1 px-2" : "btn-ghost text-xs py-1 px-2"}
                  onClick={() => togglePair(kg)}
                >
                  {formatWeightValue(kg, weightUnit)}
                </button>
              );
            })}
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={showHeavy}
              onChange={(e) => {
                setShowHeavy(e.target.checked);
                if (!e.target.checked) {
                  setSelectedPairs((prev) =>
                    prev.filter((s) => !heavyPresets.some((h) => kgMatchesPreset(s, h))),
                  );
                }
              }}
            />
            {weightUnit === "LB"
              ? "Strongman: heavy pairs (105–200 lb)"
              : "Strongman: heavy pairs (47.5–90 kg)"}
          </label>
          {showHeavy ? (
            <div className="flex flex-wrap gap-2">
              {heavyPresets.map((kg) => {
                const selected = selectedPairs.some((s) => kgMatchesPreset(s, kg));
                return (
                  <button
                    key={kg}
                    type="button"
                    className={
                      selected ? "btn-primary text-xs py-1 px-2" : "btn-ghost text-xs py-1 px-2"
                    }
                    onClick={() => togglePair(kg)}
                  >
                    {formatWeightValue(kg, weightUnit)}
                  </button>
                );
              })}
            </div>
          ) : null}
        </>
      ) : (
        <>
          <label className="label block space-y-1">
            <FieldLabel>{weightUnit === "KG" ? "Min (kg)" : "Min (lb)"}</FieldLabel>
            <input className="input w-full" value={selMin} onChange={(e) => setSelMin(e.target.value)} />
          </label>
          <label className="label block space-y-1">
            <FieldLabel>{weightUnit === "KG" ? "Max (kg)" : "Max (lb)"}</FieldLabel>
            <input className="input w-full" value={selMax} onChange={(e) => setSelMax(e.target.value)} />
          </label>
          <label className="label block space-y-1">
            <FieldLabel>{weightUnit === "KG" ? "Increment (kg)" : "Increment (lb)"}</FieldLabel>
            <input className="input w-full" value={selInc} onChange={(e) => setSelInc(e.target.value)} />
          </label>
        </>
      )}
      <ModalityPicker modalities={modalities} onChange={setModalities} />
    </ModalShell>
  );
}

function KettlebellEditor({
  existingId,
  initial,
  modalities: initialModalities,
  weightUnit,
  onClose,
  onSave,
}: {
  existingId: string | null;
  initial: KettlebellOwnership | null;
  modalities: WorkoutModality[];
  weightUnit: BodyWeightUnit;
  onClose: () => void;
  onSave: (item: OwnedEquipmentItem) => void;
}) {
  return (
    <WeightChipEditor
      title={existingId ? "Edit kettlebells" : "Add kettlebells"}
      initial={initial?.weightsKg ?? []}
      presets={FITNESS_EQUIPMENT_PRESETS.kettlebellsKg()}
      modalities={initialModalities}
      weightUnit="KG"
      onClose={onClose}
      onSave={(weightsKg, modalities) => {
        onSave(
          buildCatalogItem(existingId, "KETTLEBELLS", weightUnit, modalities, (base) => ({
            ...base,
            kettlebells: { weightsKg },
          })),
        );
        onClose();
      }}
    />
  );
}

function PlatesEditor({
  existingId,
  initial,
  modalities: initialModalities,
  weightUnit,
  onClose,
  onSave,
}: {
  existingId: string | null;
  initial: PlateOwnership | null;
  modalities: WorkoutModality[];
  weightUnit: BodyWeightUnit;
  onClose: () => void;
  onSave: (item: OwnedEquipmentItem) => void;
}) {
  const presets =
    weightUnit === "LB"
      ? FITNESS_EQUIPMENT_PRESETS.platesLb()
      : FITNESS_EQUIPMENT_PRESETS.platesKg();
  const [pairs, setPairs] = useState<PlatePairEntry[]>(() => {
    if (initial?.pairs?.length) return initial.pairs;
    return (initial?.plateWeightsKg ?? []).map((w) => ({ weightKg: w, pairCount: 1 }));
  });
  const [modalities, setModalities] = useState(initialModalities);

  const countFor = (kg: number) =>
    pairs.find((p) => kgMatchesPreset(p.weightKg, kg))?.pairCount ?? 0;

  const setCount = (kg: number, pairCount: number) => {
    setPairs((prev) => {
      const filtered = prev.filter((p) => !kgMatchesPreset(p.weightKg, kg));
      if (pairCount <= 0) return filtered;
      return [...filtered, { weightKg: kg, pairCount }].sort((a, b) => b.weightKg - a.weightKg);
    });
  };

  const canSave = pairs.some((p) => p.pairCount > 0);

  return (
    <ModalShell
      title={existingId ? "Edit weight plates" : "Add weight plates"}
      onClose={onClose}
      canSave={canSave}
      onSubmit={(e) => {
        e.preventDefault();
        if (!canSave) return;
        onSave(
          buildCatalogItem(existingId, "PLATES", weightUnit, modalities, (base) => ({
            ...base,
            plates: { pairs: pairs.filter((p) => p.pairCount > 0) },
          })),
        );
        onClose();
      }}
    >
      <p className="text-sm text-muted">Set how many pairs you own for each plate weight.</p>
      <div className="space-y-3">
        {presets.map((kg) => (
          <div key={kg} className="flex items-center justify-between gap-3 text-sm">
            <span>{formatWeightValue(kg, weightUnit)}</span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                className="btn-ghost text-xs py-1 px-2"
                onClick={() => setCount(kg, Math.max(0, countFor(kg) - 1))}
              >
                −
              </button>
              <span className="w-6 text-center">{countFor(kg)}</span>
              <button
                type="button"
                className="btn-ghost text-xs py-1 px-2"
                onClick={() => setCount(kg, countFor(kg) + 1)}
              >
                +
              </button>
            </div>
          </div>
        ))}
      </div>
      <ModalityPicker modalities={modalities} onChange={setModalities} />
    </ModalShell>
  );
}

function BandsEditor({
  existingId,
  initial,
  modalities: initialModalities,
  weightUnit,
  onClose,
  onSave,
}: {
  existingId: string | null;
  initial: BandOwnership | null;
  modalities: WorkoutModality[];
  weightUnit: BodyWeightUnit;
  onClose: () => void;
  onSave: (item: OwnedEquipmentItem) => void;
}) {
  const [tiers, setTiers] = useState(initial?.tiers ?? []);
  const [flags, setFlags] = useState({
    hasMiniLoopSet: initial?.hasMiniLoopSet ?? false,
    hasLongLoopBand: initial?.hasLongLoopBand ?? false,
    hasPullUpAssist: initial?.hasPullUpAssist ?? false,
    hasTubeHandles: initial?.hasTubeHandles ?? false,
  });
  const [modalities, setModalities] = useState(initialModalities);

  return (
    <ModalShell
      title={existingId ? "Edit resistance bands" : "Add resistance bands"}
      onClose={onClose}
      onSubmit={(e) => {
        e.preventDefault();
        onSave(
          buildCatalogItem(existingId, "BANDS", weightUnit, modalities, (base) => ({
            ...base,
            bands: { tiers, ...flags },
          })),
        );
        onClose();
      }}
    >
      <SectionHeader>Resistance tiers</SectionHeader>
      <div className="flex flex-wrap gap-2">
        {BAND_TIERS.map((tier) => {
          const selected = tiers.includes(tier);
          return (
            <button
              key={tier}
              type="button"
              className={selected ? "btn-primary text-xs py-1 px-2" : "btn-ghost text-xs py-1 px-2"}
              onClick={() =>
                setTiers(selected ? tiers.filter((t) => t !== tier) : [...tiers, tier])
              }
            >
              {bandTierLabel(tier)}
            </button>
          );
        })}
      </div>
      <div className="space-y-2 text-sm">
        {(
          [
            ["hasMiniLoopSet", "Mini loop set"],
            ["hasLongLoopBand", "Long loop band"],
            ["hasPullUpAssist", "Pull-up assist"],
            ["hasTubeHandles", "Tube bands with handles"],
          ] as const
        ).map(([key, label]) => (
          <label key={key} className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={flags[key]}
              onChange={(e) => setFlags({ ...flags, [key]: e.target.checked })}
            />
            {label}
          </label>
        ))}
      </div>
      <ModalityPicker modalities={modalities} onChange={setModalities} />
    </ModalShell>
  );
}

function RadioCatalogEditor<T extends string>({
  title,
  options,
  initial,
  modalities: initialModalities,
  onClose,
  onSave,
}: {
  title: string;
  options: { value: T; label: string }[];
  initial: T;
  modalities: WorkoutModality[];
  onClose: () => void;
  onSave: (value: T, modalities: WorkoutModality[]) => void;
}) {
  const [value, setValue] = useState(initial);
  const [modalities, setModalities] = useState(initialModalities);
  return (
    <ModalShell
      title={title}
      onClose={onClose}
      onSubmit={(e) => {
        e.preventDefault();
        onSave(value, modalities);
        onClose();
      }}
    >
      <div className="space-y-2">
        {options.map((option) => (
          <label key={option.value} className="flex items-center gap-2 text-sm">
            <input
              type="radio"
              name={title}
              checked={value === option.value}
              onChange={() => setValue(option.value)}
            />
            {option.label}
          </label>
        ))}
      </div>
      <ModalityPicker modalities={modalities} onChange={setModalities} />
    </ModalShell>
  );
}

function MultiSelectEditor<T extends string>({
  title,
  options,
  initial,
  modalities: initialModalities,
  onClose,
  onSave,
}: {
  title: string;
  options: { value: T; label: string }[];
  initial: T[];
  modalities: WorkoutModality[];
  onClose: () => void;
  onSave: (values: T[], modalities: WorkoutModality[]) => void;
}) {
  const [selected, setSelected] = useState(initial);
  const [modalities, setModalities] = useState(initialModalities);
  return (
    <ModalShell
      title={title}
      onClose={onClose}
      onSubmit={(e) => {
        e.preventDefault();
        onSave(selected, modalities);
        onClose();
      }}
    >
      <div className="flex flex-wrap gap-2">
        {options.map((option) => {
          const active = selected.includes(option.value);
          return (
            <button
              key={option.value}
              type="button"
              className={active ? "btn-primary text-xs py-1 px-2" : "btn-ghost text-xs py-1 px-2"}
              onClick={() =>
                setSelected(
                  active
                    ? selected.filter((v) => v !== option.value)
                    : [...selected, option.value],
                )
              }
            >
              {option.label}
            </button>
          );
        })}
      </div>
      <ModalityPicker modalities={modalities} onChange={setModalities} />
    </ModalShell>
  );
}

function CheckboxEditor({
  title,
  options,
  initial,
  modalities: initialModalities,
  onClose,
  onSave,
}: {
  title: string;
  options: { key: string; label: string }[];
  initial: Record<string, boolean | undefined>;
  modalities: WorkoutModality[];
  onClose: () => void;
  onSave: (values: Record<string, boolean>, modalities: WorkoutModality[]) => void;
}) {
  const [values, setValues] = useState<Record<string, boolean>>(() =>
    Object.fromEntries(options.map((o) => [o.key, initial[o.key] === true])),
  );
  const [modalities, setModalities] = useState(initialModalities);
  return (
    <ModalShell
      title={title}
      onClose={onClose}
      onSubmit={(e) => {
        e.preventDefault();
        onSave(values, modalities);
        onClose();
      }}
    >
      <div className="space-y-2 text-sm">
        {options.map((option) => (
          <label key={option.key} className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={values[option.key] ?? false}
              onChange={(e) => setValues({ ...values, [option.key]: e.target.checked })}
            />
            {option.label}
          </label>
        ))}
      </div>
      <ModalityPicker modalities={modalities} onChange={setModalities} />
    </ModalShell>
  );
}

function WeightChipEditor({
  title,
  initial,
  presets,
  modalities: initialModalities,
  weightUnit,
  onClose,
  onSave,
  onModalitiesChange,
}: {
  title: string;
  initial: number[];
  presets: number[];
  modalities: WorkoutModality[];
  weightUnit: BodyWeightUnit;
  onClose: () => void;
  onSave: (weights: number[], modalities: WorkoutModality[]) => void;
  onModalitiesChange?: (modalities: WorkoutModality[]) => void;
}) {
  const [selected, setSelected] = useState(initial);
  const [modalities, setModalities] = useState(initialModalities);

  const toggle = (kg: number) => {
    setSelected((prev) => {
      const exists = prev.some((s) => kgMatchesPreset(s, kg));
      if (exists) return prev.filter((s) => !kgMatchesPreset(s, kg));
      return [...prev, kg];
    });
  };

  return (
    <ModalShell
      title={title}
      onClose={onClose}
      canSave={selected.length > 0}
      onSubmit={(e) => {
        e.preventDefault();
        onSave(selected, modalities);
        onClose();
      }}
    >
      <div className="flex flex-wrap gap-2">
        {presets.map((kg) => {
          const active = selected.some((s) => kgMatchesPreset(s, kg));
          return (
            <button
              key={kg}
              type="button"
              className={active ? "btn-primary text-xs py-1 px-2" : "btn-ghost text-xs py-1 px-2"}
              onClick={() => toggle(kg)}
            >
              {formatWeightValue(kg, weightUnit)}
            </button>
          );
        })}
      </div>
      <ModalityPicker
        modalities={modalities}
        onChange={(next) => {
          setModalities(next);
          onModalitiesChange?.(next);
        }}
      />
    </ModalShell>
  );
}
