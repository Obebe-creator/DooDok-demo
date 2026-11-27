import type { HTMLAttributes } from "react";
import { cn } from "@/lib/cn";

/** 본문 최대 너비(1200px)와 좌우 여백 */
export function Container({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("mx-auto w-full max-w-[1200px] px-5 md:px-10", className)} {...props} />;
}
