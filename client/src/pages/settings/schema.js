// Declarative description of every preference: drives rendering, search and per-section reset.
// DEFAULTS mirrors UserPreferences.defaults() on the server (the server validates every value).

export const DEFAULTS = {
  appearance: { theme: "system", reducedMotion: "system", dashboardDensity: "comfortable", showActivityHeatmap: true, showLanguageChart: true, showRecommendations: true },
  editor: { theme: "hackercode-dark", fontSize: 14, tabSize: 4, insertSpaces: true, wordWrap: false, minimap: false, defaultLanguage: "java", layout: "split", autosave: true, formatOnRun: false },
  learning: { dailyGoal: 2, weeklyGoal: 10, remindersEnabled: false, reminderTime: "19:00", focusTopic: null },
  review: { detailLevel: "standard", categories: ["best-practices", "complexity", "correctness", "performance", "readability", "security"], explainForBeginners: false, includeRefactorSuggestion: true },
  notifications: { inApp: true, emailReminders: false, emailProductUpdates: false, emailSecurityAlerts: true },
  privacy: { trackActivity: true, keepReviewHistory: true, keepRunHistory: true },
  accessibility: { fontScale: "100", highContrast: false, underlineLinks: false, alwaysShowFocus: false },
};

const LANGS = [["c", "C"], ["cpp", "C++"], ["java", "Java"], ["python", "Python"], ["javascript", "JavaScript"], ["go", "Go"]];

export const SECTIONS = [
  { id: "account", label: "Account" },
  { id: "security", label: "Security" },
  { id: "appearance", label: "Appearance" },
  { id: "editor", label: "Editor" },
  { id: "coding", label: "Coding preferences" },
  { id: "ai-review", label: "AI review" },
  { id: "learning", label: "Learning & placement" },
  { id: "notifications", label: "Notifications" },
  { id: "privacy", label: "Privacy & data" },
  { id: "accessibility", label: "Accessibility" },
  { id: "shortcuts", label: "Keyboard shortcuts" },
];

export const SETTINGS = [
  { section: "appearance", path: "appearance.theme", label: "Theme", hint: "System follows your operating system.", type: "segmented", options: [["light", "Light"], ["dark", "Dark"], ["system", "System"]] },
  { section: "appearance", path: "appearance.dashboardDensity", label: "Dashboard density", type: "segmented", options: [["comfortable", "Comfortable"], ["compact", "Compact"]] },
  { section: "appearance", path: "appearance.showActivityHeatmap", label: "Show activity heatmap", type: "switch" },
  { section: "appearance", path: "appearance.showLanguageChart", label: "Show language usage chart", type: "switch" },
  { section: "appearance", path: "appearance.showRecommendations", label: "Show recommended problems", type: "switch" },

  { section: "editor", path: "editor.theme", label: "Editor theme", type: "select", options: [["hackercode-dark", "HackerCode Dark"], ["hackercode-light", "HackerCode Light"], ["vs-dark", "VS Dark"], ["vs", "VS Light"], ["hc-black", "High contrast"]] },
  { section: "editor", path: "editor.fontSize", label: "Font size", hint: "10–28 px", type: "number", min: 10, max: 28 },
  { section: "editor", path: "editor.tabSize", label: "Tab size", type: "segmented", options: [[2, "2"], [4, "4"], [8, "8"]] },
  { section: "editor", path: "editor.insertSpaces", label: "Indent with spaces", hint: "Off inserts tab characters.", type: "switch" },
  { section: "editor", path: "editor.wordWrap", label: "Word wrap", type: "switch" },
  { section: "editor", path: "editor.minimap", label: "Show minimap", type: "switch" },

  { section: "coding", path: "editor.defaultLanguage", label: "Default language", hint: "Used for new problems and the review workspace.", type: "select", options: LANGS },
  { section: "coding", path: "editor.layout", label: "Default editor layout", type: "segmented", options: [["split", "Side by side"], ["stacked", "Stacked"]] },
  { section: "coding", path: "editor.autosave", label: "Autosave code", hint: "Saves drafts to your account a moment after you stop typing. When off, press Ctrl+S.", type: "switch" },

  { section: "ai-review", path: "review.detailLevel", label: "Review detail level", hint: "Brief is faster; thorough reports more findings.", type: "segmented", options: [["brief", "Brief"], ["standard", "Standard"], ["thorough", "Thorough"]] },
  { section: "ai-review", path: "review.categories", label: "Review focus areas", hint: "At least one.", type: "multi", options: [["correctness", "Correctness"], ["security", "Security"], ["performance", "Performance"], ["complexity", "Complexity"], ["readability", "Readability"], ["best-practices", "Best practices"]] },
  { section: "ai-review", path: "review.explainForBeginners", label: "Beginner-friendly explanations", type: "switch" },
  { section: "ai-review", path: "review.includeRefactorSuggestion", label: "Include a refactored version", type: "switch" },

  { section: "learning", path: "learning.dailyGoal", label: "Daily goal (problems)", type: "number", min: 1, max: 50 },
  { section: "learning", path: "learning.weeklyGoal", label: "Weekly goal (problems)", type: "number", min: 1, max: 200 },
  { section: "learning", path: "learning.focusTopic", label: "Focus topic", hint: "Recommendations prioritise this topic.", type: "topic" },
  { section: "learning", path: "learning.remindersEnabled", label: "Learning reminders", hint: "Reminder emails require SMTP to be configured on the server.", type: "switch" },
  { section: "learning", path: "learning.reminderTime", label: "Reminder time", type: "time" },

  { section: "notifications", path: "notifications.inApp", label: "In-app notifications", type: "switch" },
  { section: "notifications", path: "notifications.emailReminders", label: "Email: learning reminders", type: "switch" },
  { section: "notifications", path: "notifications.emailProductUpdates", label: "Email: product updates", type: "switch" },
  { section: "notifications", path: "notifications.emailSecurityAlerts", label: "Email: security alerts", hint: "Password changes and new sign-ins.", type: "switch" },

  { section: "privacy", path: "privacy.trackActivity", label: "Track learning activity", hint: "Powers streaks, the heatmap and recent activity.", type: "switch" },
  { section: "privacy", path: "privacy.keepReviewHistory", label: "Keep code review history", hint: "When off, reviews (and the reviewed code) are not stored.", type: "switch" },
  { section: "privacy", path: "privacy.keepRunHistory", label: "Keep run output history", hint: "When off, only run status and timing are stored.", type: "switch" },

  { section: "accessibility", path: "appearance.reducedMotion", label: "Motion", hint: "Reduce turns off animations and the 3D scenes' movement.", type: "segmented", options: [["system", "System"], ["reduce", "Reduce"], ["full", "Full"]] },
  { section: "accessibility", path: "accessibility.fontScale", label: "Text size", type: "segmented", options: [["90", "90%"], ["100", "100%"], ["110", "110%"], ["125", "125%"]] },
  { section: "accessibility", path: "accessibility.highContrast", label: "Higher contrast", type: "switch" },
  { section: "accessibility", path: "accessibility.underlineLinks", label: "Underline links", type: "switch" },
  { section: "accessibility", path: "accessibility.alwaysShowFocus", label: "Always show focus outline", type: "switch" },
];

export const PREF_SECTIONS = new Set(SETTINGS.map((s) => s.section));

export function getPath(obj, path) {
  return path.split(".").reduce((o, k) => (o == null ? o : o[k]), obj);
}

export function setPath(obj, path, value) {
  const [head, ...rest] = path.split(".");
  if (!rest.length) return { ...obj, [head]: value };
  return { ...obj, [head]: setPath(obj[head] || {}, rest.join("."), value) };
}

export const SHORTCUTS = [
  ["Ctrl/Cmd + K", "Focus global search"],
  ["/", "Focus global search (outside the editor)"],
  ["Ctrl/Cmd + Enter", "Run code (or start a review)"],
  ["Ctrl/Cmd + Shift + Enter", "Submit solution"],
  ["Ctrl/Cmd + S", "Save the current draft"],
  ["Ctrl/Cmd + F", "Find in editor"],
  ["Ctrl/Cmd + H", "Find and replace in editor"],
  ["Ctrl/Cmd + /", "Toggle line comment"],
  ["Alt + Shift + F", "Format document (where supported)"],
  ["Ctrl + M", "Toggle Tab key moving focus out of the editor"],
  ["Esc", "Close dialogs and menus"],
];
