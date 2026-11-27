import { describe, expect, it } from "vitest";
import { isActivePath, tabNav } from "./nav";

const tabHrefs = tabNav.map(({ href }) => href);

describe("isActivePath", () => {
  it("홈은 정확히 / 일 때만 활성화한다", () => {
    expect(isActivePath("/", "/")).toBe(true);
    expect(isActivePath("/reviews", "/")).toBe(false);
  });

  it("하위 경로도 같은 메뉴로 본다", () => {
    expect(isActivePath("/listings", "/listings")).toBe(true);
    expect(isActivePath("/listings/12", "/listings")).toBe(true);
  });

  it("이름이 겹치는 다른 경로는 활성화하지 않는다", () => {
    expect(isActivePath("/listings-old", "/listings")).toBe(false);
  });

  it("더 구체적인 메뉴가 있으면 그 메뉴만 활성화한다", () => {
    const siblings = ["/listings", ...tabHrefs];
    expect(isActivePath("/listings/new", "/listings/new", siblings)).toBe(true);
    expect(isActivePath("/listings/new", "/listings", siblings)).toBe(false);
    expect(isActivePath("/listings/7", "/listings", siblings)).toBe(true);
  });
});
