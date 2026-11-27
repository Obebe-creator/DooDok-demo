/** 매물의 책 상태 등급. `listings.condition_grade`(S, A, B, C)와 같다. (docs/04-erd.md) */
export const CONDITION_GRADES = ["S", "A", "B", "C"] as const;

export type ConditionGrade = (typeof CONDITION_GRADES)[number];

export const conditionGrades: Record<ConditionGrade, { label: string; description: string }> = {
  S: { label: "최상", description: "새 책 수준" },
  A: { label: "상", description: "사용감 적음" },
  B: { label: "중", description: "밑줄·필기 일부" },
  C: { label: "하", description: "손상 있음" },
};

export function isConditionGrade(value: string): value is ConditionGrade {
  return (CONDITION_GRADES as readonly string[]).includes(value);
}
