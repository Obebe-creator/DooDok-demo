"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ComponentType, SVGProps } from "react";
import { ChatIcon, HomeIcon, PenIcon, PlusIcon, UserIcon } from "@/components/icons";
import { cn } from "@/lib/cn";
import { isActivePath, tabNav } from "./nav";

const icons: Record<(typeof tabNav)[number]["key"], ComponentType<SVGProps<SVGSVGElement>>> = {
  home: HomeIcon,
  reviews: PenIcon,
  sell: PlusIcon,
  chats: ChatIcon,
  library: UserIcon,
};

const tabHrefs = tabNav.map(({ href }) => href);

/** 모바일(md 미만) 하단 고정 탭바 */
export function MobileTabBar() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="하단 메뉴"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface pb-[env(safe-area-inset-bottom)] md:hidden"
    >
      <ul className="grid grid-cols-5">
        {tabNav.map(({ key, href, label }) => {
          const Icon = icons[key];
          const active = isActivePath(pathname, href, tabHrefs);
          return (
            <li key={key}>
              <Link
                href={href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "flex h-16 flex-col items-center justify-center gap-1 text-[11px]",
                  active ? "font-bold text-brand" : "font-medium text-muted",
                )}
              >
                {key === "sell" ? (
                  <span className="grid size-9 place-items-center rounded-full bg-accent-strong text-on-accent">
                    <Icon className="size-5" />
                  </span>
                ) : (
                  <Icon className="size-6" />
                )}
                {label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
