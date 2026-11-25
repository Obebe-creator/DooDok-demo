import { request } from "./http";
import { refreshAccessToken } from "./refresh";
import { tokenStore } from "./token-store";
import type {
  CurrentUser,
  EmailVerificationConfirmRequest,
  EmailVerificationRequest,
  EmailVerificationResponse,
  LoginRequest,
  SignupRequest,
  SignupResponse,
  SignupTokenResponse,
  TokenResponse,
} from "./types";

/** 인증 API. 엔드포인트는 docs/06-api-spec.md 2. 인증 */
export const authApi = {
  sendVerificationCode(email: string) {
    const body: EmailVerificationRequest = { email };
    return request<EmailVerificationResponse>("/api/auth/email-verifications", { method: "POST", body });
  },

  confirmVerificationCode(email: string, code: string) {
    const body: EmailVerificationConfirmRequest = { email, code };
    return request<SignupTokenResponse>("/api/auth/email-verifications/confirm", { method: "POST", body });
  },

  signup(body: SignupRequest) {
    return request<SignupResponse>("/api/auth/signup", { method: "POST", body });
  },

  /** 성공하면 Access Token을 저장한다. Refresh Token은 서버가 HttpOnly 쿠키로 심는다. */
  async login(email: string, password: string) {
    const body: LoginRequest = { email, password };
    const response = await request<TokenResponse>("/api/auth/login", { method: "POST", body });
    tokenStore.set(response.accessToken);
    return response;
  },

  /** 서버 요청이 실패해도 이 브라우저의 토큰은 지운다. */
  async logout() {
    try {
      await request<void>("/api/auth/logout", { method: "POST" });
    } finally {
      tokenStore.set(null);
    }
  },

  /** Refresh 쿠키로 세션을 되살린다. 세션이 없으면 null */
  restoreSession() {
    return refreshAccessToken();
  },

  me() {
    return request<CurrentUser>("/api/users/me", { auth: true });
  },
};
