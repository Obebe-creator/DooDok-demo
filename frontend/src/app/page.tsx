import AuthPanel from "./AuthPanel";

export default function Home() {
  return (
    <main className="mx-auto grid min-h-svh max-w-5xl content-center gap-8 px-6 py-12 md:grid-cols-[1fr_25rem] md:items-center md:gap-14">
      <div className="grid gap-3">
        <p className="text-sm font-semibold tracking-wide text-brand">DooDok</p>
        <h1 className="text-5xl font-bold tracking-tight sm:text-6xl">두독</h1>
        <p className="text-lg text-muted">읽은 책은 다음 독자에게, 감상은 책과 함께.</p>
        <p className="text-sm text-muted">중고책 거래 · 독후감 · 독후감 기반 도서 추천 · 월간 독서 리포트</p>
      </div>
      <AuthPanel />
    </main>
  );
}
