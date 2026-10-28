import { describe, expect, it } from "vitest";
import { passwordProblems, passwordStrength, safeNext } from "./password.js";

describe("passwordProblems", () => {
  it("accepts a reasonable password", () => {
    expect(passwordProblems("Str0ng-pass!", "student@example.com")).toEqual([]);
  });
  it("flags short, letter-only, common and email-derived passwords", () => {
    expect(passwordProblems("abc", "")).toContain("At least 8 characters");
    expect(passwordProblems("onlyletters", "")).toContain("Letters plus a number or symbol");
    expect(passwordProblems("password1", "")).toContain("Not a common password");
    expect(passwordProblems("student2024!", "student@example.com")).toContain("Must not contain your email name");
  });
});

describe("passwordStrength", () => {
  it("scores longer, more varied passwords higher", () => {
    expect(passwordStrength("")).toBe(0);
    expect(passwordStrength("abcdefgh")).toBeLessThan(passwordStrength("Abcdefgh12!x"));
    expect(passwordStrength("password1")).toBe(1);
  });
});

describe("safeNext", () => {
  it("allows same-site paths only", () => {
    expect(safeNext("/problems?q=1")).toBe("/problems?q=1");
    expect(safeNext("//evil.com")).toBe("/dashboard");
    expect(safeNext("https://evil.com")).toBe("/dashboard");
    expect(safeNext("/\\evil.com")).toBe("/dashboard");
    expect(safeNext(undefined, "/x")).toBe("/x");
  });
});
