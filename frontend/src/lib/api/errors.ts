import type { ErrorBody, FieldError } from "./types";

/** 서버에 닿지 못한 경우(오프라인, CORS 차단, DNS 실패 등)의 에러 코드 */
export const NETWORK_ERROR = "NETWORK_ERROR";
/** 에러 응답이 공통 형식(ErrorResponse)이 아닐 때의 에러 코드 */
export const UNKNOWN_ERROR = "UNKNOWN_ERROR";

/**
 * API 에러. 화면에서는 code로 분기하고, message는 그대로 보여 줄 수 있다.
 * 백엔드 에러 형식은 docs/06-api-spec.md 1. 공통 규칙을 따른다.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: FieldError[] = [],
    options?: ErrorOptions,
  ) {
    super(message, options);
    this.name = "ApiError";
  }

  static async from(response: Response): Promise<ApiError> {
    const body: unknown = await response.json().catch(() => null);
    if (isErrorBody(body)) {
      return new ApiError(response.status, body.code, body.message, body.errors ?? []);
    }
    return new ApiError(response.status, UNKNOWN_ERROR, `요청을 처리하지 못했습니다. (HTTP ${response.status})`);
  }
}

function isErrorBody(body: unknown): body is Omit<ErrorBody, "errors"> & { errors?: FieldError[] } {
  if (typeof body !== "object" || body === null) return false;
  const { code, message, errors } = body as Record<string, unknown>;
  return typeof code === "string" && typeof message === "string"
    && (errors === undefined || (Array.isArray(errors) && errors.every(isFieldError)));
}

function isFieldError(value: unknown): value is FieldError {
  if (typeof value !== "object" || value === null) return false;
  const { field, reason } = value as Record<string, unknown>;
  return typeof field === "string" && typeof reason === "string";
}
