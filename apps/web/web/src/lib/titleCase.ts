/** Capitalize the first letter of each word for field labels and section headers. */
export function titleCaseWords(text: string): string {
  return text.replace(/\b[a-z]/g, (char) => char.toUpperCase());
}
