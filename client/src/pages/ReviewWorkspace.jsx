import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { useAuth } from "../api/auth.jsx";
import { get, post } from "../api/client.js";
import Icon from "../components/Icon.jsx";
import LanguagePicker from "../components/LanguagePicker.jsx";
import { useToast } from "../components/Toasts.jsx";
import { Alert, Button, EmptyState, LangBadge, PageHeader, Skeleton, timeAgo, useDocumentTitle } from "../components/ui.jsx";
import useAsync from "../lib/useAsync.js";
import useCodeWorkspace, { SAVE_LABEL } from "../lib/useCodeWorkspace.js";

const CodeEditor = lazy(() => import("../components/CodeEditor.jsx"));

const SEVERITIES = ["critical", "high", "medium", "low", "info"];
const CATEGORY_LABEL = {
  correctness: "Correctness", security: "Security", performance: "Performance", complexity: "Complexity",
  readability: "Readability", best_practices: "Best practices",
};

function Finding({ f, source, onJump }) {
  const [open, setOpen] = useState(f.severity === "critical" || f.severity === "high");
  return (
    <div className="finding">
      <button onClick={() => setOpen(!open)} aria-expanded={open}>
        <span className={`badge sev-${f.severity}`}>{f.severity}</span>
        <span className="grow">{f.title}</span>
        <span className="badge">{CATEGORY_LABEL[f.category] || f.category}</span>
        {f.line > 0 && <span className="xs subtle nowrap">line {f.line}</span>}
        <Icon name={open ? "chevronDown" : "chevronRight"} size={16} />
      </button>
      {open && (
        <div className="finding-body">
          <div><h5>What</h5><p>{f.explanation}</p></div>
          {(f.impact) && <div><h5>Why it matters</h5><p>{f.impact}</p></div>}
          {f.suggestion && <div><h5>Suggested fix</h5><p style={{ whiteSpace: "pre-wrap" }}>{f.suggestion}</p></div>}
          <div className="row between">
            <span className="xs subtle">{source}</span>
            {f.line > 0 && <Button size="sm" variant="ghost" onClick={() => onJump(f.line)}>Go to line {f.line}</Button>}
          </div>
        </div>
      )}
    </div>
  );
}

function ReviewResult({ result, onJump, onApply }) {
  const [sev, setSev] = useState("all");
  const [cat, setCat] = useState("all");
  const report = result.ai.report;
  const all = useMemo(() => {
    const ai = (report?.findings || []).map((f) => ({ ...f, source: "AI-generated" }));
    const st = result.staticChecks.findings.map((f) => ({ ...f, impact: null, source: `Static rule: ${f.rule}` }));
    return [...ai, ...st].sort((a, b) => SEVERITIES.indexOf(a.severity) - SEVERITIES.indexOf(b.severity));
  }, [report, result]);
  const shown = all.filter((f) => (sev === "all" || f.severity === sev) && (cat === "all" || f.category === cat));
  const counts = SEVERITIES.map((s) => [s, all.filter((f) => f.severity === s).length]).filter(([, n]) => n);

  return (
    <div className="stack">
      <Alert kind="info">{result.disclaimer}</Alert>
      {result.ai.generated ? (
        <section className="card stack" aria-labelledby="ai-summary">
          <div className="row between" style={{ alignItems: "flex-start" }}>
            <div className="stack-sm">
              <span className="ai-label"><Icon name="sparkles" size={14} /> AI-generated · {result.ai.model}</span>
              <h2 id="ai-summary" style={{ fontSize: "var(--text-md)" }}>Summary</h2>
            </div>
            <div className="score" aria-label={`Quality score ${report.score} out of 100`}><strong>{report.score}</strong><span className="muted">/100</span></div>
          </div>
          <p>{report.summary}</p>
          <p className="xs muted">{result.ai.notice}</p>
          <dl className="kv">
            <dt>Time complexity</dt><dd className="mono">{report.complexity.time}{report.complexity.improvedTime && <span className="muted"> → achievable {report.complexity.improvedTime}</span>}</dd>
            <dt>Space complexity</dt><dd className="mono">{report.complexity.space}</dd>
          </dl>
          <p className="small muted">{report.complexity.explanation}</p>
        </section>
      ) : (
        <Alert kind={result.ai.errorCode === "ai_not_configured" ? "warning" : "danger"} title={result.ai.errorCode === "ai_not_configured" ? "No AI review." : "AI review failed."}>
          {result.ai.notice}
        </Alert>
      )}

      <section className="card stack" aria-labelledby="findings-title">
        <div className="row between" style={{ flexWrap: "wrap" }}>
          <h2 id="findings-title" style={{ fontSize: "var(--text-md)" }}>Findings ({all.length})</h2>
          <div className="row-wrap">
            <label className="sr-only" htmlFor="sev">Severity</label>
            <select id="sev" className="select input-sm" style={{ width: "auto", minHeight: 34 }} value={sev} onChange={(e) => setSev(e.target.value)}>
              <option value="all">All severities</option>
              {counts.map(([s, n]) => <option key={s} value={s}>{s} ({n})</option>)}
            </select>
            <label className="sr-only" htmlFor="cat">Category</label>
            <select id="cat" className="select input-sm" style={{ width: "auto", minHeight: 34 }} value={cat} onChange={(e) => setCat(e.target.value)}>
              <option value="all">All categories</option>
              {Object.entries(CATEGORY_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select>
          </div>
        </div>
        {shown.length === 0 ? <p className="small muted">{all.length ? "No findings match these filters." : "No issues found."}</p>
          : shown.map((f, i) => <Finding key={i} f={f} source={f.source} onJump={onJump} />)}
      </section>

      <section className="card stack-sm" aria-labelledby="static-title">
        <h2 id="static-title" style={{ fontSize: "var(--text-md)" }}>Static checks <span className="badge">rule-based, not AI</span></h2>
        <p className="small muted">Pattern-matching rules that run on every review. They can miss issues and can flag code that is actually fine.</p>
        <dl className="kv">
          <dt>Estimated time</dt><dd className="mono">{result.staticChecks.estimatedTime}</dd>
          <dt>Max loop nesting</dt><dd>{result.staticChecks.maxLoopDepth}</dd>
          <dt>Lines</dt><dd>{result.staticChecks.lineCount}</dd>
        </dl>
      </section>

      {report && (report.positives?.length > 0 || report.nextSteps?.length > 0) && (
        <section className="card stack-sm">
          {report.positives?.length > 0 && (<><h3 className="small">What's good</h3><ul className="small" style={{ margin: 0 }}>{report.positives.map((p, i) => <li key={i}>{p}</li>)}</ul></>)}
          {report.nextSteps?.length > 0 && (<><h3 className="small" style={{ marginTop: 8 }}>Next steps</h3><ol className="small" style={{ margin: 0 }}>{report.nextSteps.map((p, i) => <li key={i}>{p}</li>)}</ol></>)}
        </section>
      )}

      {report?.refactoredCode && (
        <section className="card stack-sm">
          <div className="row between"><h3 className="small">Suggested refactor (AI-generated)</h3><Button size="sm" onClick={() => onApply(report.refactoredCode)}>Replace editor code</Button></div>
          <pre className="mono xs" style={{ margin: 0, maxHeight: 320, overflow: "auto", background: "var(--surface-2)", padding: 12, borderRadius: 10 }}>{report.refactoredCode}</pre>
        </section>
      )}
    </div>
  );
}

export default function ReviewWorkspace() {
  useDocumentTitle("AI Code Review");
  const { prefs, user } = useAuth();
  const toast = useToast();
  const location = useLocation();
  const fromProblem = location.state?.problemId ? location.state : null;
  const ws = useCodeWorkspace("workspace", prefs, fromProblem ? { code: fromProblem.code, language: fromProblem.language } : null);
  const status = useAsync(() => get("/api/reviews/status"), []);
  const history = useAsync(() => get("/api/reviews"), []);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);
  const [editor, setEditor] = useState(null);
  const [pendingLoad, setPendingLoad] = useState(null);
  useEffect(() => {
    if (pendingLoad && ws.language === pendingLoad.language) {
      ws.setCode(pendingLoad.code);
      setPendingLoad(null);
    }
  }, [pendingLoad, ws]);

  const review = async () => {
    if (!ws.code.trim()) { toast.error("Write or paste some code first."); return; }
    setBusy(true);
    setError(null);
    try {
      const r = await post("/api/reviews", { language: ws.language, code: ws.code, problemId: fromProblem?.problemId });
      setResult(r);
      history.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  const openPast = async (id) => {
    try {
      const r = await get(`/api/reviews/${id}`);
      if (r.language !== ws.language) ws.setLanguage(r.language);
      setPendingLoad({ language: r.language, code: r.code });
      setResult(r);
    } catch (e) { toast.error(e); }
  };

  const jump = (line) => {
    if (!editor) return;
    editor.revealLineInCenter(line);
    editor.setPosition({ lineNumber: line, column: 1 });
    editor.focus();
  };

  const s = status.data;
  return (
    <>
      <PageHeader
        title="AI Code Review"
        subtitle="Get feedback on correctness, security, complexity and readability. Reviews read your code; they never run it."
        crumbs={fromProblem ? [{ label: "Practice", to: "/problems" }, { label: fromProblem.problemTitle, to: `/problems/${fromProblem.problemId}` }, { label: "Review" }] : undefined}
      />
      {s && !s.configured && <div style={{ marginBottom: 16 }}><Alert kind="warning" title="AI provider not configured.">Reviews will include rule-based static checks only. {s.setupHint}</Alert></div>}
      {s && s.configured && !s.enabled && <div style={{ marginBottom: 16 }}><Alert kind="warning">AI reviews are temporarily disabled by an administrator. Static checks still run.</Alert></div>}
      {s?.configured && !user.emailVerified && <div style={{ marginBottom: 16 }}><Alert kind="warning">Verify your email address to request AI reviews.</Alert></div>}

      <div className="review-layout">
        <section className="card editor-card" aria-label="Code to review">
          <div className="editor-toolbar">
            {ws.ready && <LanguagePicker languages={ws.languages} value={ws.language} onChange={ws.setLanguage} />}
            {ws.current && <span className="file-name">{ws.current.fileName}</span>}
            <span className="xs subtle">{SAVE_LABEL[ws.saveState]}</span>
            <Button variant="primary" size="sm" style={{ marginLeft: "auto" }} loading={busy} onClick={review}><Icon name="sparkles" size={14} /> Review code</Button>
          </div>
          <div className="editor-host" style={{ height: 560 }}>
            {ws.ready ? (
              <Suspense fallback={<div className="editor-fallback"><span className="spinner" /> Loading editor…</div>}>
                <CodeEditor value={ws.code} onChange={ws.setCode} language={ws.current?.monacoId} prefs={prefs} onRun={review} onSave={ws.saveNow}
                  onMount={(ed) => setEditor(ed)} ariaLabel="Code to review" />
              </Suspense>
            ) : <div className="editor-fallback"><span className="spinner" /> Loading…</div>}
          </div>
        </section>

        <div className="stack" aria-live="polite">
          {busy ? (
            <div className="card stack"><p className="row small"><span className="spinner" /> Reviewing your code… AI reviews can take up to a minute.</p><Skeleton h={80} /><Skeleton h={160} /></div>
          ) : error ? (
            <Alert kind="danger" title="Review failed.">{error.message}</Alert>
          ) : result ? (
            <ReviewResult result={result} onJump={jump} onApply={(c) => { ws.setCode(c); toast.success("Editor code replaced. Your previous version is in the review history."); }} />
          ) : (
            <div className="card"><EmptyState icon="sparkles" title="Paste code and press Review">
              You'll get a summary, severity-ranked findings with line references, complexity analysis and next steps. Ctrl+Enter also starts a review.
            </EmptyState></div>
          )}

          <section className="card" aria-labelledby="history-title">
            <div className="card-header"><h2 id="history-title">Review history</h2></div>
            {history.loading ? <Skeleton h={80} /> : !history.data?.length ? <p className="small muted">Your past reviews appear here{prefs?.privacy?.keepReviewHistory === false ? " (history is turned off in Settings → Privacy)" : ""}.</p> : (
              <div className="list">
                {history.data.map((h) => (
                  <div className="list-item" key={h.id}>
                    <LangBadge id={h.language} />
                    <div className="grow"><div className="small truncate">{h.summary}</div>
                      <div className="xs subtle">{h.aiGenerated ? `AI · ${h.model}` : "Static checks only"} · {h.findingCount} findings · {timeAgo(h.createdAt)}</div></div>
                    <Button size="sm" variant="ghost" onClick={() => openPast(h.id)}>Open</Button>
                  </div>
                ))}
              </div>
            )}
          </section>
          {fromProblem && <Link className="small" to={`/problems/${fromProblem.problemId}`}>← Back to {fromProblem.problemTitle}</Link>}
        </div>
      </div>
    </>
  );
}
