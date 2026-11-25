import type { components } from "./schema";

/**
 * 백엔드 OpenAPI 스펙에서 생성한 타입(schema.d.ts)에 이름을 붙여 둔다.
 * 스키마 이름으로 꺼내므로 springdoc이 응답 content-type을 바꿔도 영향이 없다.
 */
type Schemas = components["schemas"];

export type ErrorBody = Schemas["ErrorResponse"];
export type FieldError = Schemas["FieldErrorDetail"];

export type EmailVerificationRequest = Schemas["EmailVerificationRequest"];
export type EmailVerificationResponse = Schemas["EmailVerificationResponse"];
export type EmailVerificationConfirmRequest = Schemas["EmailVerificationConfirmRequest"];
export type SignupTokenResponse = Schemas["SignupTokenResponse"];
export type SignupRequest = Schemas["SignupRequest"];
export type SignupResponse = Schemas["SignupResponse"];
export type LoginRequest = Schemas["LoginRequest"];
export type TokenResponse = Schemas["TokenResponse"];

/** 프로필 이미지는 등록 전까지 null이다. 스펙에 nullable 표시가 없어 여기서 바로잡는다. */
export type CurrentUser = Omit<Schemas["MeResponse"], "profileImageUrl"> & {
  profileImageUrl: string | null;
};
