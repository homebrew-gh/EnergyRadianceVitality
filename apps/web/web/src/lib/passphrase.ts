import type { AuthStatus } from "./api";

export const MIN_PASSPHRASE_LENGTH = 8;

/** Old companions omit the flag; a present sealed blob is the historical default. */
export function passphraseIsSet(status: AuthStatus | null | undefined): boolean {
  if (!status?.has_state) return false;
  return status.passphrase_set ?? true;
}
