/**
 * The phone reads built-in standards from a JSON asset made from `standardsLibrary.ts`.
 * This test fails when the two drift. To refresh the asset after changing the library, run
 * `UPDATE_STANDARDS_ASSET=1 npx vitest run test/standardsLibraryAsset.test.ts`.
 */
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { BUILT_IN_STANDARDS } from "../src/lib/standardsLibrary";

const here = dirname(fileURLToPath(import.meta.url));
const appDir = resolve(here, "../../../../app/src/main");
const asset = resolve(appDir, "assets/standards/built_in_standards.json");

function builtInStandardsAssetJson(): string {
  return `${JSON.stringify(BUILT_IN_STANDARDS, null, 2)}\n`;
}

describe.skipIf(!existsSync(appDir))("built-in standards asset for Android", () => {
  it("matches standardsLibrary.ts", () => {
    const expected = builtInStandardsAssetJson();
    if (process.env.UPDATE_STANDARDS_ASSET) writeFileSync(asset, expected);
    expect(existsSync(asset)).toBe(true);
    expect(readFileSync(asset, "utf8")).toBe(expected);
  });
});
