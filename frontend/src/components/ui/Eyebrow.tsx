import type { HTMLAttributes } from "react";
import { cn } from "@/lib/cn";

/** 섹션 제목 위의 작은 영문 라벨 (예: NEARBY SHELVES) */
export function Eyebrow({ tone = "accent", className, ...props }: HTMLAttributes<HTMLParagraphElement> & { tone?: "accent" | "cream" }) {
  return (
    <p
      className={cn(
        "text-xs font-medium uppercase tracking-eyebrow",
        tone === "accent" ? "text-accent-strong" : "text-cream/80",
        className,
      )}
      {...props}
    />
  );
}
