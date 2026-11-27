import type { InputHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string };

/** 라벨과 안내 문구가 붙은 입력칸. 기본으로 required이며 props로 끌 수 있다. */
export function TextField({ label, hint, className, ...props }: TextFieldProps) {
  return (
    <label className="grid gap-1.5 text-sm font-medium text-foreground">
      {label}
      <input
        className={cn(
          "h-11 w-full rounded-xs border border-line bg-surface px-3 text-[15px] font-normal placeholder:text-muted",
          "focus-visible:border-brand focus-visible:outline-2 focus-visible:outline-offset-0 focus-visible:outline-brand/25",
          "disabled:bg-background disabled:text-muted",
          className,
        )}
        required
        {...props}
      />
      {hint && <span className="text-xs font-normal text-muted">{hint}</span>}
    </label>
  );
}
