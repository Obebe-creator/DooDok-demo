import type { ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

export type ButtonVariant = "primary" | "accent" | "outline" | "ghost" | "link";
export type ButtonSize = "sm" | "md" | "lg";

const base =
  "inline-flex items-center justify-center gap-2 font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-strong disabled:pointer-events-none disabled:opacity-50";

const variants: Record<ButtonVariant, string> = {
  primary: "rounded-full bg-brand text-on-brand hover:bg-brand-deep",
  accent: "rounded-full bg-accent-strong text-on-accent hover:bg-accent-strong/90",
  outline: "rounded-full border border-brand/40 text-foreground hover:bg-cream-soft",
  ghost: "rounded-full text-foreground hover:bg-cream-soft",
  link: "w-fit text-foreground underline underline-offset-4 hover:text-accent-strong",
};

const sizes: Record<ButtonSize, string> = {
  sm: "h-9 px-4 text-sm",
  md: "h-11 px-6 text-[15px]",
  lg: "h-13 px-7 text-base",
};

/** 버튼 모양의 className. `<Link>`에 버튼 스타일을 입힐 때도 쓴다. */
export function buttonClass({
  variant = "primary",
  size = "md",
  className,
}: { variant?: ButtonVariant; size?: ButtonSize; className?: string } = {}) {
  return cn(base, variants[variant], variant !== "link" && sizes[size], className);
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
};

/** 기본 type은 "button"이다. 폼 제출 버튼은 type="submit"을 지정한다. */
export function Button({ variant, size, className, type = "button", ...props }: ButtonProps) {
  return <button type={type} className={buttonClass({ variant, size, className })} {...props} />;
}
