"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { SearchIcon } from "@/components/icons";
import { Container } from "@/components/ui";
import { useAuth } from "@/lib/auth/AuthProvider";
import { cn } from "@/lib/cn";
import { isActivePath, mainNav } from "./nav";

const mainHrefs = mainNav.map(({ href }) => href);

function AccountLink() {
  const { state } = useAuth();

  if (state.status === "loading") {
    return <span className="size-9 rounded-full bg-cream/15" aria-hidden="true" />;
  }
  if (state.status === "authenticated") {
    return (
      <Link
        href="/library"
        aria-label={`${state.user.nickname}님의 서재`}
        className="grid size-9 place-items-center rounded-full bg-cream text-sm font-bold text-brand hover:bg-cream-soft"
      >
        {state.user.nickname.slice(0, 1)}
      </Link>
    );
  }
  return (
    <Link href="/#account" className="text-[13px] font-medium tracking-[0.06em] text-cream/90 hover:text-cream">
      로그인
    </Link>
  );
}

export function SiteHeader() {
  const pathname = usePathname();

  return (
    <header className="bg-brand text-cream">
      <Container className="flex h-16 items-center justify-between gap-6 md:h-20">
        <Link href="/" aria-label="두독 홈" className="text-2xl font-bold">
          DooDok.
        </Link>

        <nav aria-label="주요 메뉴" className="hidden items-center gap-10 md:flex">
          {mainNav.map(({ href, label }) => {
            const active = isActivePath(pathname, href, mainHrefs);
            return (
              <Link
                key={href}
                href={href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "text-[13px] font-medium tracking-[0.1em] underline-offset-8 transition-opacity",
                  active ? "underline decoration-accent decoration-2" : "opacity-80 hover:opacity-100",
                )}
              >
                {label}
              </Link>
            );
          })}
        </nav>

        <div className="flex items-center gap-2 md:gap-4">
          <Link href="/search" aria-label="책 검색" className="grid size-9 place-items-center rounded-full hover:bg-cream/10">
            <SearchIcon className="size-5" />
          </Link>
          <Link
            href="/listings/new"
            className="hidden h-9 items-center rounded-full border border-cream/70 px-5 text-[13px] font-medium tracking-[0.04em] hover:bg-cream/10 md:inline-flex"
          >
            판매하기
          </Link>
          <AccountLink />
        </div>
      </Container>
    </header>
  );
}
