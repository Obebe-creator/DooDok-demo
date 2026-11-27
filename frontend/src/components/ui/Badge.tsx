import type { HTMLAttributes } from "react";
import { cn } from "@/lib/cn";
import { conditionGrades, type ConditionGrade } from "./condition";

type BadgeTone = "neutral" | "brand" | "accent";

const tones: Record<BadgeTone, string> = {
  neutral: "bg-cream-soft text-brand",
  brand: "bg-brand text-on-brand",
  accent: "bg-accent-strong text-on-accent",
};

export function Badge({ tone = "neutral", className, ...props }: HTMLAttributes<HTMLSpanElement> & { tone?: BadgeTone }) {
  return (
    <span
      className={cn("inline-flex items-center rounded-xs px-2 py-0.5 text-xs font-medium", tones[tone], className)}
      {...props}
    />
  );
}

const gradeTones: Record<ConditionGrade, string> = {
  S: "bg-grade-s text-grade-s-fg",
  A: "bg-grade-a text-grade-a-fg",
  B: "bg-grade-b text-grade-b-fg",
  C: "bg-grade-c text-grade-c-fg",
};

/** 책 상태 등급 뱃지. 화면에는 "최상·상·중·하"로, 설명은 title로 보여 준다. */
export function ConditionBadge({ grade, className }: { grade: ConditionGrade; className?: string }) {
  const { label, description } = conditionGrades[grade];
  return (
    <span
      title={description}
      className={cn("inline-flex items-center rounded-xs px-2 py-0.5 text-xs font-medium", gradeTones[grade], className)}
    >
      {label}
    </span>
  );
}
