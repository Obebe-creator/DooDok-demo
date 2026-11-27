export type NavItem = { href: string; label: string };

/** 데스크톱 헤더 메뉴. 아직 없는 화면은 기능을 만들 때 페이지를 추가한다. */
export const mainNav: NavItem[] = [
  { href: "/listings", label: "중고거래" },
  { href: "/reviews", label: "독후감" },
  { href: "/recommendations", label: "AI 추천" },
  { href: "/library", label: "내 서재" },
];

/** 모바일 하단 탭. `sell`은 가운데 강조 버튼으로 그린다. */
export const tabNav = [
  { key: "home", href: "/", label: "홈" },
  { key: "reviews", href: "/reviews", label: "독후감" },
  { key: "sell", href: "/listings/new", label: "판매" },
  { key: "chats", href: "/chats", label: "채팅" },
  { key: "library", href: "/library", label: "내 서재" },
] as const;

/**
 * 현재 경로가 메뉴 경로에 속하는지 판단한다.
 * "/"는 홈에서만, 나머지는 하위 경로까지 포함한다. (/listings/12 → 중고거래)
 * 단, 더 구체적인 메뉴가 따로 있으면 그쪽을 우선한다. (/listings/new → 판매)
 */
export function isActivePath(pathname: string, href: string, siblings: readonly string[] = []) {
  if (href === "/") return pathname === "/";
  const matches = (target: string) => pathname === target || pathname.startsWith(`${target}/`);
  if (!matches(href)) return false;
  return !siblings.some((other) => other !== href && other.startsWith(`${href}/`) && matches(other));
}
