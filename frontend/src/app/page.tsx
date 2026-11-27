import { Container, Eyebrow } from "@/components/ui";
import AuthPanel from "./AuthPanel";

export default function Home() {
  return (
    <main>
      <Container className="grid gap-10 py-14 md:grid-cols-[1fr_25rem] md:items-center md:gap-14 md:py-24">
        <div className="grid gap-4">
          <Eyebrow>A Quieter Shelf · 중고책과 독후감</Eyebrow>
          <h1 className="text-4xl font-light leading-tight sm:text-5xl">
            읽은 책은 다음 독자에게,
            <br />
            감상은 책과 함께.
          </h1>
          <p className="text-muted">중고책 거래 · 독후감 · 독후감 기반 도서 추천 · 월간 독서 리포트</p>
        </div>
        <div id="account" className="scroll-mt-6">
          <AuthPanel />
        </div>
      </Container>
    </main>
  );
}
