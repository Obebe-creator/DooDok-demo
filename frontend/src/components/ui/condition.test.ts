import { describe, expect, it } from "vitest";
import { CONDITION_GRADES, conditionGrades, isConditionGrade } from "./condition";

describe("conditionGrades", () => {
  it("ERD의 모든 등급에 화면 이름과 설명이 있다", () => {
    for (const grade of CONDITION_GRADES) {
      expect(conditionGrades[grade].label).not.toBe("");
      expect(conditionGrades[grade].description).not.toBe("");
    }
  });

  it("서버에서 받은 값이 등급인지 확인한다", () => {
    expect(isConditionGrade("S")).toBe(true);
    expect(isConditionGrade("C")).toBe(true);
    expect(isConditionGrade("D")).toBe(false);
    expect(isConditionGrade("s")).toBe(false);
  });
});
