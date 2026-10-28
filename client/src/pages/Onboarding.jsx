import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { prefersReducedMotion, useAuth } from "../api/auth.jsx";
import { get, patch, post } from "../api/client.js";
import { Logo } from "../components/AppShell.jsx";
import Icon from "../components/Icon.jsx";
import Orbs from "../components/Orbs.jsx";
import { useToast } from "../components/Toasts.jsx";
import { Button, Field, LangBadge, useDocumentTitle } from "../components/ui.jsx";

const LANGS = [
  ["c", "C"], ["cpp", "C++"], ["java", "Java"], ["python", "Python"], ["javascript", "JavaScript"], ["go", "Go"],
];
const LEVELS = [
  { value: "beginner", title: "Just starting", text: "New to DSA. Start from Basics.", topic: "basics" },
  { value: "some", title: "Some practice", text: "Comfortable with arrays and strings.", topic: null },
  { value: "interview", title: "Interview prep", text: "Revising patterns for placements.", topic: null },
];

export default function Onboarding() {
  useDocumentTitle("Welcome");
  const { user, prefs, setUser, setPrefs } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [topics, setTopics] = useState([]);
  const [busy, setBusy] = useState(false);
  const [choice, setChoice] = useState({
    displayName: user?.displayName || "",
    language: prefs?.editor?.defaultLanguage || "java",
    level: "beginner",
    focusTopic: "",
    dailyGoal: prefs?.learning?.dailyGoal || 2,
    theme: prefs?.appearance?.theme || "system",
  });
  useEffect(() => { get("/api/topics").then(setTopics).catch(() => {}); }, []);

  const finish = async () => {
    if (!prefs) return;
    setBusy(true);
    try {
      const level = LEVELS.find((l) => l.value === choice.level);
      const next = {
        ...prefs,
        appearance: { ...prefs.appearance, theme: choice.theme },
        editor: { ...prefs.editor, defaultLanguage: choice.language },
        learning: { ...prefs.learning, dailyGoal: Number(choice.dailyGoal), focusTopic: choice.focusTopic || level.topic || null },
        review: { ...prefs.review, explainForBeginners: choice.level === "beginner" },
      };
      if (choice.displayName.trim() && choice.displayName.trim() !== user.displayName) await patch("/api/account/profile", { displayName: choice.displayName.trim(), avatarUrl: user.avatarUrl || "" });
      const updated = await post("/api/account/onboarding", next);
      setUser(updated);
      setPrefs(next);
      toast.success("You're all set. Happy coding!");
      navigate("/dashboard", { replace: true });
    } catch (e) {
      toast.error(e);
    } finally {
      setBusy(false);
    }
  };

  const steps = [
    {
      title: "Welcome to HackerCode",
      body: (
        <div className="stack">
          <p className="muted">Let's tailor your workspace. This takes about 30 seconds and you can change everything later in Settings.</p>
          <Field label="What should we call you?">
            {(p) => <input className="input" value={choice.displayName} maxLength={80} onChange={(e) => setChoice({ ...choice, displayName: e.target.value })} {...p} />}
          </Field>
          <div className="stack-sm">
            <span className="label">Where are you on your DSA journey?</span>
            <div className="choice-grid">
              {LEVELS.map((l) => (
                <button type="button" key={l.value} className="choice" aria-pressed={choice.level === l.value} onClick={() => setChoice({ ...choice, level: l.value })}>
                  <strong>{l.title}</strong><span className="small muted">{l.text}</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      ),
    },
    {
      title: "Pick your main language",
      body: (
        <div className="stack">
          <p className="muted">This becomes the default in the editor. Every problem supports all six.</p>
          <div className="choice-grid">
            {LANGS.map(([id, label]) => (
              <button type="button" key={id} className="choice" aria-pressed={choice.language === id} onClick={() => setChoice({ ...choice, language: id })}>
                <LangBadge id={id} /><strong>{label}</strong>
              </button>
            ))}
          </div>
        </div>
      ),
    },
    {
      title: "Set a goal",
      body: (
        <div className="stack">
          <Field label="Problems per day" hint="Small, consistent goals beat cramming.">
            {(p) => (
              <select className="select" value={choice.dailyGoal} onChange={(e) => setChoice({ ...choice, dailyGoal: e.target.value })} {...p}>
                {[1, 2, 3, 5, 8].map((n) => <option key={n} value={n}>{n} per day</option>)}
              </select>
            )}
          </Field>
          <Field label="Focus topic (optional)" hint="Recommendations will prioritise this topic.">
            {(p) => (
              <select className="select" value={choice.focusTopic} onChange={(e) => setChoice({ ...choice, focusTopic: e.target.value })} {...p}>
                <option value="">Follow the sheet in order</option>
                {topics.map((t) => <option key={t.slug} value={t.slug}>{t.label} ({t.total})</option>)}
              </select>
            )}
          </Field>
          <div className="stack-sm">
            <span className="label">Theme</span>
            <div className="choice-grid">
              {["light", "dark", "system"].map((t) => (
                <button type="button" key={t} className="choice" aria-pressed={choice.theme === t} onClick={() => setChoice({ ...choice, theme: t })}>
                  <strong style={{ textTransform: "capitalize" }}>{t}</strong>
                </button>
              ))}
            </div>
          </div>
        </div>
      ),
    },
  ];

  return (
    <div className="auth-page">
      <div className="card onboard stack-lg" style={{ width: "100%", position: "relative", overflow: "hidden" }}>
        <div style={{ position: "absolute", right: -70, bottom: -90, width: 260, height: 260, opacity: 0.35, pointerEvents: "none", zIndex: 0 }}>
          <Orbs parallax={false} reduced={prefersReducedMotion(prefs)} orbs={[
            { c: "teal", s: 140, x: 20, y: 20, z: 0, dur: 14, dx: 8, dy: -10 },
            { c: "orange", s: 70, x: 10, y: 60, z: 0, dur: 12, dx: -6, dy: 8 },
          ]} />
        </div>
        <div className="row between" style={{ background: "var(--navy-900)", margin: "-24px -24px 0", padding: "10px 20px" }}>
          <Logo to="/onboarding" />
          <span className="xs" style={{ color: "#9bb0b8" }}>Step {step + 1} of {steps.length}</span>
        </div>
        <div className="steps" aria-hidden="true">{steps.map((_, i) => <span key={i} className={i <= step ? "on" : ""} />)}</div>
        <h1>{steps[step].title}</h1>
        {steps[step].body}
        <div className="row between">
          <Button variant="ghost" disabled={step === 0} onClick={() => setStep(step - 1)}><Icon name="arrowLeft" size={16} /> Back</Button>
          {step < steps.length - 1
            ? <Button variant="primary" onClick={() => setStep(step + 1)}>Continue <Icon name="arrowRight" size={16} /></Button>
            : <Button variant="primary" loading={busy} onClick={finish}>Start practicing <Icon name="arrowRight" size={16} /></Button>}
        </div>
      </div>
    </div>
  );
}
