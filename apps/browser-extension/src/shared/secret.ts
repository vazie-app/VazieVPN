/** A value that must never reach a log, a message or a string by accident. `JSON.stringify` and `String` both
 * print a placeholder; the value comes out only through {@link Secret.expose}. */
export class Secret<T = string> {
  readonly #value: T;

  private constructor(value: T) {
    this.#value = value;
  }

  static of<T>(value: T): Secret<T> {
    return new Secret(value);
  }

  expose(): T {
    return this.#value;
  }

  toString(): string {
    return "Secret(…)";
  }

  toJSON(): string {
    return "Secret(…)";
  }
}
