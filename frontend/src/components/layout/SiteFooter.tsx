import { Container } from "@/components/ui";

export function SiteFooter() {
  return (
    <footer className="bg-brand-deep text-cream">
      <Container className="flex flex-col gap-3 py-10 md:flex-row md:items-baseline md:justify-between">
        <p className="flex items-baseline gap-3">
          <span className="text-xl font-bold">DooDok.</span>
          <span className="text-sm text-cream/70">읽고, 나누고, 다시 읽다</span>
        </p>
        <p className="text-sm text-cream/70">© 2026 DooDok · 이용약관 · 개인정보처리방침</p>
      </Container>
    </footer>
  );
}
