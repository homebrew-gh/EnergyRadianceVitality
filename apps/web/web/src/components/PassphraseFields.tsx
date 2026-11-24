import { FieldLabel } from "./FieldLabel";
import { SecretInput } from "./SecretInput";

type PassphraseFieldsProps = {
  passphrase: string;
  confirm: string;
  onPassphrase: (value: string) => void;
  onConfirm: (value: string) => void;
  idPrefix: string;
  autoComplete?: string;
};

export function PassphraseFields({
  passphrase,
  confirm,
  onPassphrase,
  onConfirm,
  idPrefix,
  autoComplete = "new-password",
}: PassphraseFieldsProps) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div>
        <label className="label" htmlFor={`${idPrefix}-pass`}>
          <FieldLabel>Passphrase</FieldLabel>
        </label>
        <SecretInput
          id={`${idPrefix}-pass`}
          autoComplete={autoComplete}
          value={passphrase}
          onChange={(e) => onPassphrase(e.target.value)}
        />
      </div>
      <div>
        <label className="label" htmlFor={`${idPrefix}-confirm`}>
          <FieldLabel>Confirm passphrase</FieldLabel>
        </label>
        <SecretInput
          id={`${idPrefix}-confirm`}
          autoComplete={autoComplete}
          value={confirm}
          onChange={(e) => onConfirm(e.target.value)}
        />
      </div>
    </div>
  );
}
