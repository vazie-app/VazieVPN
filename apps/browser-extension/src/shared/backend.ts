// What the Vazie backend publishes to every client without a session: the About links and the questions. Every
// field is untrusted and read defensively, as in `VazieLinksSource.kt` and `VazieFaqSource.kt`: an item the
// extension cannot show is dropped on its own and the rest stay.

export type LinkSection = "AUTHOR" | "PROJECT" | "SECURITY" | "HELP" | "SUPPORT" | "FEEDBACK";
export const LINK_SECTIONS: LinkSection[] = ["AUTHOR", "PROJECT", "SECURITY", "HELP", "SUPPORT", "FEEDBACK"];

export interface Localized {
  ru: string;
  en: string;
}

export interface AppLink {
  key: string;
  section: LinkSection;
  url: string;
  title: Localized;
  subtitle: Localized | null;
  action: Localized | null;
}

export interface AppLinks {
  links: AppLink[];
}

const URL_MAX_LENGTH = 512;
const HTTPS = /^https:\/\/[^/?#@\s]+(?:[/?#][^\s]*)?$/;
const MAILTO = /^mailto:[^\s@?/]+@[^\s@?/]+\.[^\s@?/]+$/;
// eslint-disable-next-line no-control-regex
const HAS_CONTROL = /[\u0000-\u001f\u007f-\u009f]/;

/** Whether `url` is something the extension will open: `https` with a host and no user info, or one `mailto:`. */
export function isOpenableLink(url: string): boolean {
  return url.length <= URL_MAX_LENGTH && !HAS_CONTROL.test(url) && (HTTPS.test(url) || MAILTO.test(url));
}

export function localize(text: Localized, language: string): string {
  return language === "ru" ? text.ru : text.en;
}

type Raw = Record<string, unknown> | null | undefined;
const obj = (value: unknown): Raw => (value && typeof value === "object" ? (value as Record<string, unknown>) : null);
const str = (value: unknown): string | null => (typeof value === "string" ? value : null);

function linkText(value: unknown): Localized | null {
  const raw = obj(value);
  if (!raw) return null;
  const usable = (t: string | null): t is string =>
    t !== null && t.trim() !== "" && t.length <= 400 && !HAS_CONTROL.test(t);
  const ru = str(raw.ru);
  const en = str(raw.en);
  return usable(ru) && usable(en) ? { ru, en } : null;
}

export function readLinks(json: unknown): AppLinks | null {
  const list = obj(json)?.links;
  if (!Array.isArray(list)) return null;
  const links: AppLink[] = [];
  for (const item of list) {
    const raw = obj(item);
    if (!raw) continue;
    const section = LINK_SECTIONS.find((s) => s === raw.section);
    const url = str(raw.url);
    const key = str(raw.key);
    const title = linkText(raw.title);
    if (!section || !url || !isOpenableLink(url) || !key || key.trim() === "" || !title) continue;
    links.push({
      key,
      section,
      url,
      title,
      subtitle: raw.subtitle == null ? null : linkText(raw.subtitle),
      action: raw.action == null ? null : linkText(raw.action),
    });
  }
  return links.length > 0 ? { links } : null;
}

export const linksIn = (links: AppLinks, section: LinkSection): AppLink[] =>
  links.links.filter((l) => l.section === section);

// -- FAQ -------------------------------------------------------------------------------------------

export interface FaqText {
  ru: string;
  en: string | null;
}

export interface FaqLink {
  url: string;
  label: FaqText;
}

export interface FaqItem {
  key: string;
  question: FaqText;
  answer: FaqText;
  link: FaqLink | null;
}

export interface Faq {
  items: FaqItem[];
}

const SITE_PATH = /^\/[A-Za-z0-9._~/-]*(#[A-Za-z0-9_-]+)?$/;

/** What the screen will open: `https`, or a path on the site that is not `//host`. */
export function isOpenableFaqUrl(url: string): boolean {
  if (url.length > URL_MAX_LENGTH) return false;
  if (url.startsWith("//")) return false;
  if (url.startsWith("/")) return SITE_PATH.test(url);
  return url.startsWith("https://") && isOpenableLink(url);
}

export function resolveFaq(text: FaqText, language: string): string {
  return language === "ru" ? text.ru : (text.en ?? text.ru);
}

function faqText(value: unknown, max: number, multiline: boolean): FaqText | null {
  const raw = obj(value);
  if (!raw) return null;
  const usable = (t: string | null): t is string => {
    if (t === null || t.trim() === "" || t.length > max) return false;
    const stripped = multiline ? t.replace(/\n/g, "") : t;
    return !HAS_CONTROL.test(stripped);
  };
  const ru = str(raw.ru);
  const en = str(raw.en);
  if (!usable(ru)) return null;
  return { ru, en: usable(en) ? en : null };
}

export function readFaq(json: unknown): Faq | null {
  const list = obj(json)?.items;
  if (!Array.isArray(list)) return null;
  const items: FaqItem[] = [];
  for (const item of list) {
    const raw = obj(item);
    if (!raw) continue;
    const key = str(raw.key);
    const question = faqText(raw.question, 200, false);
    const answer = faqText(raw.answer, 2000, true);
    if (!key || key.trim() === "" || !question || !answer) continue;
    let link: FaqLink | null = null;
    const rawLink = obj(raw.link);
    if (rawLink) {
      const url = str(rawLink.url);
      const label = faqText(rawLink.label, 80, false);
      if (url && isOpenableFaqUrl(url) && label) link = { url, label };
    }
    items.push({ key, question, answer, link });
  }
  // An empty list is the operator's own answer (every question switched off) and is believed; questions none of
  // which can be shown are not.
  return list.length > 0 && items.length === 0 ? null : { items };
}
