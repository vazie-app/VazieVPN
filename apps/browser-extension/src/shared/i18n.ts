import androidEn from "./i18n/android.en.json" with { type: "json" };
import androidRu from "./i18n/android.ru.json" with { type: "json" };
import extEn from "./i18n/ext.en.json" with { type: "json" };
import extRu from "./i18n/ext.ru.json" with { type: "json" };

export type Language = "en" | "ru";
type Entry = string | Record<string, string>;
type Table = Record<string, Entry>;

// The Android app's words first, the extension's own over them: where a sentence differs (a phone's "widgets" are
// a browser's "side panel"), the extension's key wins.
const TABLES: Record<Language, Table> = {
  en: { ...(androidEn as Table), ...(extEn as Table) },
  ru: { ...(androidRu as Table), ...(extRu as Table) },
};

/** Russian for a Russian-speaking browser, English for every other. */
export function languageOf(uiLanguage: string): Language {
  return uiLanguage.toLowerCase().startsWith("ru") ? "ru" : "en";
}

/** `%1$s`, `%2$d`, `%s`, `%d` and `%%`, in the way Android's `getString` reads them. */
export function format(template: string, args: readonly (string | number)[]): string {
  let next = 0;
  return template.replace(/%(?:(\d+)\$)?([sd%])/g, (_whole, position: string | undefined, type: string) => {
    if (type === "%") return "%";
    const index = position ? Number(position) - 1 : next++;
    const value = args[index];
    return value === undefined ? "" : String(value);
  });
}

export interface Translator {
  language: Language;
  /** A string by key. A missing key shows English, then the key itself: never a blank. */
  t(key: string, ...args: (string | number)[]): string;
  /** A plural by key and count, with the count as the first argument. */
  n(key: string, count: number, ...args: (string | number)[]): string;
  has(key: string): boolean;
}

export function translator(language: Language): Translator {
  const table = TABLES[language];
  const english = TABLES.en;
  const rules = new Intl.PluralRules(language);
  const lookup = (key: string): Entry | undefined => table[key] ?? english[key];
  return {
    language,
    has: (key) => key in english || key in table,
    t(key, ...args) {
      const entry = lookup(key);
      if (typeof entry === "string") return format(entry, args);
      if (entry) return format(entry["other"] ?? "", args);
      return key;
    },
    n(key, count, ...args) {
      const entry = lookup(key);
      if (typeof entry === "string") return format(entry, [count, ...args]);
      if (!entry) return key;
      const form = rules.select(count);
      return format(entry[form] ?? entry["other"] ?? "", [count, ...args]);
    },
  };
}

export function allKeys(language: Language): string[] {
  return Object.keys(TABLES[language]);
}
