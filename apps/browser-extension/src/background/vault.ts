// The stored configurations, encrypted at rest: one AES-GCM document in IndexedDB whose key is a non-extractable
// `CryptoKey` kept beside it. Android keeps the same arrangement with a Keystore key and a file. The key cannot be
// exported by any code, so a copy of the browser profile's files does not carry the configurations away; code
// running inside this extension can still use the key, as any app's own process can use its Keystore key.

import type { StoredProfile, VlessOutbound } from "../shared/model.ts";

const DB_NAME = "vazie-vpn";
const STORE = "kv";
const KEY_ID = "vault-key";
const DOC_ID = "profiles";
const VERSION = 1;

interface Sealed {
  iv: Uint8Array<ArrayBuffer>;
  data: ArrayBuffer;
}

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1);
    request.onupgradeneeded = () => request.result.createObjectStore(STORE);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function get<T>(db: IDBDatabase, id: string): Promise<T | undefined> {
  return new Promise((resolve, reject) => {
    const request = db.transaction(STORE).objectStore(STORE).get(id);
    request.onsuccess = () => resolve(request.result as T | undefined);
    request.onerror = () => reject(request.error);
  });
}

async function put(db: IDBDatabase, id: string, value: unknown): Promise<void> {
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE, "readwrite");
    tx.objectStore(STORE).put(value, id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

async function vaultKey(db: IDBDatabase): Promise<CryptoKey> {
  const existing = await get<CryptoKey>(db, KEY_ID);
  if (existing) return existing;
  const key = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
  await put(db, KEY_ID, key);
  return key;
}

interface Document {
  version: number;
  profiles: StoredProfile[];
}

/** The profiles, an empty list when nothing is stored yet, or `null` when what is stored cannot be read. */
export async function readProfiles(): Promise<StoredProfile[] | null> {
  try {
    const db = await open();
    const sealed = await get<Sealed>(db, DOC_ID);
    if (!sealed) return [];
    const key = await vaultKey(db);
    const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: sealed.iv }, key, sealed.data);
    const document = JSON.parse(new TextDecoder().decode(plain)) as Document;
    if (document.version !== VERSION || !Array.isArray(document.profiles)) return null;
    return document.profiles;
  } catch {
    // Not rethrown and not logged: every error this can produce may carry the bytes that failed, and those
    // bytes are the person's configuration.
    return null;
  }
}

export async function writeProfiles(profiles: StoredProfile[]): Promise<void> {
  const db = await open();
  const key = await vaultKey(db);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const document: Document = { version: VERSION, profiles };
  const data = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(JSON.stringify(document)));
  await put(db, DOC_ID, { iv, data } satisfies Sealed);
}

export type { VlessOutbound };
