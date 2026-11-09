import type { ContentfulStatusCode } from "hono/utils/http-status";

/** An error with a stable machine code and a Dutch message that clients can show as-is. */
export class HttpError extends Error {
  constructor(
    readonly status: ContentfulStatusCode,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "HttpError";
  }
}

export const notFound = (what = "Niet gevonden.") => new HttpError(404, "not_found", what);
