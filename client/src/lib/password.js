// Client-side password guidance. The server's PasswordPolicy is authoritative; these rules mirror it so users see
// problems before submitting.

const COMMON = new Set([
  "password", "password1", "password123", "12345678", "123456789", "1234567890", "qwerty123",
  "iloveyou1", "admin123", "welcome1", "letmein1", "abc12345", "passw0rd", "hackercode1",
]);

export function passwordProblems(password, email) {
  const problems = [];
  if (!password || password.length < 8) problems.push("At least 8 characters");
  if (password && password.length > 128) problems.push("At most 128 characters");
  if (password && !(/\p{L}/u.test(password) && /[^\p{L}]/u.test(password))) problems.push("Letters plus a number or symbol");
  if (password && COMMON.has(password.toLowerCase())) problems.push("Not a common password");
  const local = (email || "").toLowerCase().split("@")[0];
  if (password && local.length >= 4 && password.toLowerCase().includes(local)) problems.push("Must not contain your email name");
  return problems;
}

/** 0 (empty) to 4 (strong). */
export function passwordStrength(password) {
  if (!password) return 0;
  let score = 0;
  if (password.length >= 8) score++;
  if (password.length >= 12) score++;
  const classes = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((r) => r.test(password)).length;
  if (classes >= 2) score++;
  if (classes >= 3 && password.length >= 10) score++;
  if (COMMON.has(password.toLowerCase())) score = 1;
  return Math.max(1, Math.min(4, score));
}

export const STRENGTH_LABEL = ["", "Weak", "Fair", "Good", "Strong"];

/** Only same-site absolute paths are allowed as post-login destinations. */
export function safeNext(next, fallback = "/dashboard") {
  if (!next || typeof next !== "string") return fallback;
  if (!next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\") || next.includes("://")) return fallback;
  return next;
}

export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
