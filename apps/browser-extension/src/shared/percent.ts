/** Percent-decoding in the two strictnesses a share link needs (`PercentCoding.kt`). */

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder("utf-8", { fatal: false });

const HEX = "0123456789ABCDEF";

export const USER_INFO = "-._~";
/** Inside a query value: `/` and `:` are legal and common, `&` `=` `?` `#` would end the value. */
export const QUERY = "-._~/:,";
/** A display name is last in the link, so only `#` itself has to go. */
export const FRAGMENT = "-._~/:?&=+,!$'()*;@";

/** Decoded text, or `null` when a `%` is not followed by two hexadecimal digits. */
export function decodeStrict(text: string): string | null {
  return decode(text, false);
}

/** Decoded text, with any malformed `%` kept as a literal `%`. */
export function decodeLenient(text: string): string {
  return decode(text, true) as string;
}

export function encode(text: string, safe: string): string {
  let out = "";
  for (const byte of textEncoder.encode(text)) {
    const ch = String.fromCharCode(byte);
    if (isUnreserved(ch) || safe.includes(ch)) {
      out += ch;
    } else {
      out += "%" + HEX[(byte >> 4) & 0xf] + HEX[byte & 0xf];
    }
  }
  return out;
}

function isUnreserved(ch: string): boolean {
  return (ch >= "A" && ch <= "Z") || (ch >= "a" && ch <= "z") || (ch >= "0" && ch <= "9");
}

function hexValue(ch: string | undefined): number | null {
  if (ch === undefined) return null;
  const code = ch.charCodeAt(0);
  if (code >= 48 && code <= 57) return code - 48;
  if (code >= 97 && code <= 102) return code - 87;
  if (code >= 65 && code <= 70) return code - 55;
  return null;
}

function decode(text: string, lenient: boolean): string | null {
  if (!text.includes("%")) return text;
  const bytes: number[] = [];
  let index = 0;
  while (index < text.length) {
    const ch = text[index] as string;
    if (ch !== "%") {
      // A code point, written as UTF-8: the text may carry characters above ASCII beside the escapes.
      const point = text.codePointAt(index) as number;
      const unit = String.fromCodePoint(point);
      bytes.push(...textEncoder.encode(unit));
      index += unit.length;
      continue;
    }
    const high = hexValue(text[index + 1]);
    const low = hexValue(text[index + 2]);
    if (high === null || low === null) {
      if (!lenient) return null;
      bytes.push(0x25);
      index += 1;
      continue;
    }
    bytes.push((high << 4) | low);
    index += 3;
  }
  return textDecoder.decode(Uint8Array.from(bytes));
}
