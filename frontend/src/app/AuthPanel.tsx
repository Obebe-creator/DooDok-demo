"use client";

import { useEffect, useState, type FormEvent, type InputHTMLAttributes } from "react";
import { ApiError, authApi } from "@/lib/api";
import { useAuth } from "@/lib/auth/AuthProvider";

const primary = "rounded-lg bg-brand px-4 py-2.5 font-semibold text-background hover:opacity-90 disabled:opacity-50";
const link = "w-fit text-sm font-semibold text-brand underline underline-offset-4 disabled:text-muted disabled:no-underline";
const input = "w-full rounded-lg border border-muted/35 bg-background px-3 py-2.5 focus-visible:outline-2 focus-visible:outline-brand";

function Field({ label, hint, ...props }: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string }) {
  return (
    <label className="grid gap-1.5 text-sm font-medium">
      {label}
      <input className={input} required {...props} />
      {hint && <span className="text-xs text-muted">{hint}</span>}
    </label>
  );
}

function ErrorNotice({ error }: { error: unknown }) {
  if (error == null) return null;
  const message = error instanceof ApiError
    ? error.fieldErrors.map(({ reason }) => reason).join(" ") || error.message
    : "요청을 처리하지 못했습니다. 다시 시도해 주세요.";
  return <p role="alert" className="rounded-lg border border-current px-3 py-2 text-sm text-red-700 dark:text-red-300">{message}</p>;
}

function LoginForm({ email, signupComplete, openSignup }: { email: string; signupComplete: boolean; openSignup: () => void }) {
  const { login } = useAuth();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const data = new FormData(event.currentTarget);
    setPending(true);
    setError(null);
    try {
      await login(String(data.get("email")).trim(), String(data.get("password")));
    } catch (cause) {
      setError(cause);
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <h2 className="text-2xl font-bold">로그인</h2>
      {signupComplete && <p role="status" className="text-sm text-brand">가입이 완료됐습니다. 새 계정으로 로그인해 주세요.</p>}
      <form onSubmit={submit}>
        <fieldset className="grid gap-4" disabled={pending}>
          <Field label="이메일" name="email" type="email" autoComplete="username" defaultValue={email} maxLength={255} />
          <Field label="비밀번호" name="password" type="password" autoComplete="current-password" />
          <ErrorNotice error={error} />
          <button className={primary}>{pending ? "로그인 중…" : "로그인"}</button>
        </fieldset>
      </form>
      <button type="button" className={link} onClick={openSignup} disabled={pending}>회원가입</button>
    </>
  );
}

type SignupStep =
  | { kind: "email" }
  | { kind: "code"; expiresAt: number; resendAt: number }
  | { kind: "details"; signupToken: string; expiresAt: number };

function SignupForm({ initialEmail, openLogin }: { initialEmail: string; openLogin: (email: string, complete?: boolean) => void }) {
  const [email, setEmail] = useState(initialEmail);
  const [step, setStep] = useState<SignupStep>({ kind: "email" });
  const [now, setNow] = useState(0);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    if (step.kind === "email") return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [step]);

  const remaining = step.kind === "email" ? 0 : Math.max(0, Math.ceil((step.expiresAt - now) / 1000));
  const resendIn = step.kind === "code" ? Math.max(0, Math.ceil((step.resendAt - now) / 1000)) : 0;
  const expired = step.kind !== "email" && remaining === 0;

  async function run(action: () => Promise<void>) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      await action();
    } catch (cause) {
      setError(cause);
      if (cause instanceof ApiError) {
        if (cause.code === "INVALID_SIGNUP_TOKEN") setStep({ kind: "email" });
        if (cause.code === "EMAIL_VERIFICATION_CODE_EXPIRED" || cause.code === "EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED") {
          setStep((current) => current.kind === "code" ? { ...current, expiresAt: 0 } : current);
        }
      }
    } finally {
      setPending(false);
    }
  }

  async function sendCode() {
    const address = email.trim();
    const result = await authApi.sendVerificationCode(address);
    const sentAt = Date.now();
    setEmail(address);
    setNow(sentAt);
    setStep({ kind: "code", expiresAt: sentAt + result.expiresIn * 1000, resendAt: sentAt + result.resendAvailableIn * 1000 });
  }

  function restart() {
    setStep({ kind: "email" });
    setError(null);
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (expired) return;
    const data = new FormData(event.currentTarget);
    void run(async () => {
      if (step.kind === "email") {
        await sendCode();
      } else if (step.kind === "code") {
        const result = await authApi.confirmVerificationCode(email, String(data.get("code")));
        const verifiedAt = Date.now();
        setNow(verifiedAt);
        setStep({ kind: "details", signupToken: result.signupToken, expiresAt: verifiedAt + result.expiresIn * 1000 });
      } else {
        const user = await authApi.signup({
          signupToken: step.signupToken,
          nickname: String(data.get("nickname")),
          password: String(data.get("password")),
        });
        openLogin(user.email, true);
      }
    });
  }

  return (
    <>
      <h2 className="text-2xl font-bold">회원가입</h2>
      <form onSubmit={submit} key={step.kind}>
        <fieldset className="grid gap-4" disabled={pending}>
          {step.kind === "email" && (
            <Field label="이메일" name="email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} maxLength={255} />
          )}
          {step.kind === "code" && (
            <>
              <p className="break-all text-sm text-muted">{email}로 인증 코드를 보냈습니다.</p>
              <Field key={step.resendAt} label="인증 코드" name="code" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} disabled={expired} />
              <p className="text-sm text-muted">{expired ? "새 인증 코드를 요청해 주세요." : `코드 유효 시간 ${remaining}초`}</p>
              <div className="flex flex-wrap gap-x-4 gap-y-2">
                <button type="button" className={link} onClick={() => void run(sendCode)} disabled={resendIn > 0}>
                  {resendIn > 0 ? `${resendIn}초 후 재전송` : "코드 재전송"}
                </button>
                <button type="button" className={link} onClick={restart}>이메일 변경</button>
              </div>
            </>
          )}
          {step.kind === "details" && (
            <>
              <p className="break-all text-sm text-brand">인증된 이메일 · {email}</p>
              <Field label="닉네임" name="nickname" autoComplete="nickname" minLength={2} maxLength={12} disabled={expired} hint="한글, 영문, 숫자, 밑줄로 2~12자" />
              <Field label="비밀번호" name="password" type="password" autoComplete="new-password" minLength={8} maxLength={20} disabled={expired} hint="영문, 숫자, 특수문자를 모두 포함해 8~20자" />
              {expired && <button type="button" className={link} onClick={restart}>이메일 다시 인증</button>}
            </>
          )}
          <ErrorNotice error={error} />
          <button className={primary} disabled={expired}>
            {pending ? "처리 중…" : { email: "인증 코드 보내기", code: "인증 코드 확인", details: "가입 완료" }[step.kind]}
          </button>
        </fieldset>
      </form>
      <button type="button" className={link} onClick={() => openLogin(email)} disabled={pending}>로그인으로 돌아가기</button>
    </>
  );
}

export default function AuthPanel() {
  const { state, logout, reloadUser, retryRestore } = useAuth();
  const [signup, setSignup] = useState(false);
  const [email, setEmail] = useState("");
  const [signupComplete, setSignupComplete] = useState(false);
  const [logoutPending, setLogoutPending] = useState(false);
  const [logoutError, setLogoutError] = useState<unknown>(null);

  function openLogin(address: string, complete = false) {
    setEmail(address);
    setSignupComplete(complete);
    setSignup(false);
  }

  async function signOut() {
    setLogoutPending(true);
    setLogoutError(null);
    try {
      await logout();
      openLogin("");
    } catch (cause) {
      setLogoutError(cause);
    } finally {
      setLogoutPending(false);
    }
  }

  return (
    <section aria-label="계정" className="flex min-w-0 flex-col gap-5 rounded-2xl border border-muted/25 bg-background p-6 shadow-sm sm:p-8">
      {state.status === "loading" && <p role="status" className="text-sm text-muted">로그인 상태를 확인하는 중입니다.</p>}
      {state.status === "restoreError" && (
        <>
          <h2 className="text-2xl font-bold">세션을 확인하지 못했습니다</h2>
          <ErrorNotice error={state.error} />
          <button type="button" className={primary} onClick={() => void retryRestore()}>다시 확인</button>
        </>
      )}
      {state.status === "authenticated" && (
        <>
          <h2 className="break-words text-2xl font-bold">{state.user.nickname}님, 반갑습니다</h2>
          {state.profile.status === "loading" && <p role="status" className="text-sm text-muted">프로필을 불러오는 중입니다.</p>}
          {state.profile.status === "ready" && <p className="break-all text-sm text-muted">{state.profile.user.email}</p>}
          {state.profile.status === "error" && (
            <p className="text-sm text-muted">
              프로필을 불러오지 못했습니다.{" "}
              <button type="button" className={link} onClick={() => void reloadUser().catch(() => {})}>다시 불러오기</button>
            </p>
          )}
          <ErrorNotice error={logoutError} />
          <button type="button" className={primary} disabled={logoutPending} onClick={() => void signOut()}>
            {logoutPending ? "로그아웃 중…" : "로그아웃"}
          </button>
        </>
      )}
      {state.status === "unauthenticated" && (signup
        ? <SignupForm initialEmail={email} openLogin={openLogin} />
        : <LoginForm email={email} signupComplete={signupComplete} openSignup={() => setSignup(true)} />)}
    </section>
  );
}
