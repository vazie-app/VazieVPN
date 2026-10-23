import { decodeStrict } from "./percent.ts";

/** A share link's query string, read once and then consumed parameter by parameter (`LinkQuery.kt`). */
export class LinkQuery {
  readonly #entries: { name: string; value: string; consumed: boolean }[];

  private constructor(entries: { name: string; value: string; consumed: boolean }[]) {
    this.#entries = entries;
  }

  /** The value of the first unconsumed parameter matching any of `names`, in the order given. */
  take(...names: string[]): string | null {
    for (const name of names) {
      const entry = this.#entries.find((e) => !e.consumed && e.name.toLowerCase() === name.toLowerCase());
      if (entry) {
        entry.consumed = true;
        return entry.value;
      }
    }
    return null;
  }

  /** Everything `take` did not claim, in the order it appeared, spelled as it arrived. */
  remaining(): [string, string][] {
    return this.#entries.filter((e) => !e.consumed).map((e) => [e.name, e.value]);
  }

  /** `null` when any name or value is malformed percent-encoding. */
  static parse(query: string): LinkQuery | null {
    if (query === "") return new LinkQuery([]);
    const entries: { name: string; value: string; consumed: boolean }[] = [];
    for (const pair of query.split("&")) {
      if (pair === "") continue;
      const sep = pair.indexOf("=");
      const rawName = sep < 0 ? pair : pair.slice(0, sep);
      const rawValue = sep < 0 ? "" : pair.slice(sep + 1);
      const name = decodeStrict(rawName);
      const value = decodeStrict(rawValue);
      if (name === null || value === null) return null;
      if (name === "") continue;
      entries.push({ name, value, consumed: false });
    }
    return new LinkQuery(entries);
  }
}
