import { lazy, Suspense, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../api/auth.jsx";
import { get, post, put } from "../api/client.js";
import Icon from "../components/Icon.jsx";
import LanguagePicker from "../components/LanguagePicker.jsx";
import { BookmarkButton, STATUS_LABEL, StatusDot } from "../components/ProblemBits.jsx";
import { useToast } from "../components/Toasts.jsx";
import {
  Alert, Breadcrumbs, Button, ConfirmDialog, EmptyState, ErrorState, LangBadge, Segmented, Skeleton, Tabs, timeAgo, useDocumentTitle,
} from "../components/ui.jsx";
import useAsync from "../lib/useAsync.js";
import useCodeWorkspace, { SAVE_LABEL } from "../lib/useCodeWorkspace.js";

const CodeEditor = lazy(() => import("../components/CodeEditor.jsx"));

const RESULT_TEXT = {
  OK: ["ok", "Ran successfully"], ACCEPTED: ["ok", "Accepted"], COMPILE_ERROR: ["bad", "Compilation error"],
  RUNTIME_ERROR: ["bad", "Runtime error"], TIME_LIMIT: ["bad", "Time limit exceeded"], MEMORY_LIMIT: ["bad", "Memory limit exceeded"],
  OUTPUT_LIMIT: ["bad", "Output limit exceeded"], WRONG_ANSWER: ["bad", "Wrong answer"], PROVIDER_ERROR: ["warn", "Execution service error"],
};

function RunResult({ result }) {
  const [tone, text] = RESULT_TEXT[result.status] || ["warn", result.status];
  return (
    <div className="stack-sm">
      <div className="row-wrap">
        <span className={`status-pill ${tone}`}><Icon name={tone === "ok" ? "check" : "alert"} size={16} />{text}</span>
        {result.kind === "SUBMIT" && result.testsTotal > 0 && <span className="badge">{result.testsPassed}/{result.testsTotal} tests</span>}
        {result.timeMs != null && <span className="badge">{result.timeMs} ms</span>}
        {result.memoryKb != null && <span className="badge">{Math.round(result.memoryKb / 1024)} MB</span>}
        <span className="badge badge-outline">via {result.provider}</span>
      </div>
      {result.message && result.status !== "OK" && <p className="small muted">{result.message}</p>}
      {result.compileOutput && (<><span className="label">Compiler output</span><pre className="err">{result.compileOutput}</pre></>)}
      {result.stdout != null && result.stdout !== "" && (<><span className="label">Output (stdout)</span><pre>{result.stdout}</pre></>)}
      {result.stdout === "" && !result.compileOutput && result.kind === "RUN" && <p className="small muted">The program produced no output.</p>}
      {result.stderr && (<><span className="label">Errors (stderr)</span><pre className="err">{result.stderr}</pre></>)}
      {result.cases?.length > 0 && (
        <div className="stack-sm">
          {result.cases.map((c) => (
            <details key={c.index} className="card card-tight" open={!c.passed && !c.hidden}>
              <summary className="row" style={{ cursor: "pointer" }}>
                <Icon name={c.passed ? "check" : "x"} size={16} /> Test {c.index} {c.hidden ? "(hidden)" : ""} · {c.passed ? "passed" : RESULT_TEXT[c.status]?.[1] || "failed"}
              </summary>
              {!c.hidden && (
                <div className="stack-sm" style={{ marginTop: 8 }}>
                  <span className="xs muted">Input</span><pre>{c.input}</pre>
                  <span className="xs muted">Expected</span><pre>{c.expected}</pre>
                  <span className="xs muted">Your output</span><pre>{c.actual}</pre>
                </div>
              )}
            </details>
          ))}
        </div>
      )}
    </div>
  );
}

function Description({ problem }) {
  const s = problem.source;
  const a = problem.added;
  return (
    <div className="stack">
      {s && (
        <div className="source-block stack-sm">
          <strong className="small">From the source sheet (#{s.number})</strong>
          <span>{s.text}</span>
          <span className="xs muted">{s.topic} › {s.subsection}</span>
          {problem.subsectionNote && <span className="xs muted">Pattern note: “{problem.subsectionNote}”</span>}
        </div>
      )}
      {s?.practiceUrls?.length > 0 ? (
        <div className="stack-sm">
          {s.practiceUrls.map((u) => (
            <a key={u} className="btn btn-outline-primary btn-sm" style={{ justifyContent: "flex-start" }} href={u} target="_blank" rel="noopener noreferrer">
              <Icon name="external" size={14} /> Open practice page <span className="xs subtle truncate">({new URL(u).hostname})</span>
            </a>
          ))}
        </div>
      ) : s && <p className="small muted">The source sheet provides no practice link for this entry.</p>}

      {a ? (
        <div className="added-block stack-sm">
          <strong className="small">Added after import{a.addedBy ? " by staff" : ""}</strong>
          {a.difficulty && <span><span className="badge badge-info">{a.difficulty}</span></span>}
          {a.statement && <div><h4 className="small">Problem statement</h4><p className="prose">{a.statement}</p></div>}
          {a.constraints && <div><h4 className="small">Constraints</h4><p className="prose mono">{a.constraints}</p></div>}
          {a.examples && <div><h4 className="small">Examples</h4><pre className="prose mono" style={{ margin: 0 }}>{a.examples}</pre></div>}
          {a.testCount > 0 && <span className="xs muted">{a.testCount} test case{a.testCount === 1 ? "" : "s"} are used when you submit.</span>}
        </div>
      ) : (
        <Alert kind="info">
          The source sheet lists this problem by title only, so no statement, constraints, examples or test cases are available here yet.
          Use the practice link for the full problem, then write and run your solution with your own input.
        </Alert>
      )}

      {problem.related.length > 0 && (
        <div className="stack-sm">
          <h3 className="small">Related in {problem.topic.label}</h3>
          {problem.related.map((r) => (
            <Link key={r.id} to={`/problems/${r.slug}`} className="row small" style={{ color: "var(--text)" }}>
              <StatusDot status={r.status} /> <span className="truncate">#{r.number} {r.title}</span>
              {r.subsection === problem.subsection && <span className="badge badge-outline">same pattern</span>}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

function Notes({ problem, onSaved }) {
  const toast = useToast();
  const [notes, setNotes] = useState(problem.progress.notes || "");
  const [busy, setBusy] = useState(false);
  const dirty = notes !== (problem.progress.notes || "");
  const save = async () => {
    setBusy(true);
    try {
      const p = await put(`/api/problems/${problem.id}/progress`, { notes });
      onSaved(p);
      toast.success("Notes saved.");
    } catch (e) { toast.error(e); } finally { setBusy(false); }
  };
  return (
    <div className="stack">
      <label htmlFor="notes" className="label">Personal notes</label>
      <textarea id="notes" className="textarea" style={{ minHeight: 220 }} maxLength={20000} value={notes} onChange={(e) => setNotes(e.target.value)}
        placeholder="Approach, edge cases, complexity, mistakes to avoid…" />
      <div className="row between">
        <span className="xs muted">{notes.length.toLocaleString()} / 20,000</span>
        <Button variant="primary" size="sm" disabled={!dirty} loading={busy} onClick={save}>Save notes</Button>
      </div>
    </div>
  );
}

function Submissions({ problemId, refreshKey, onLoad }) {
  const { data, loading, error } = useAsync(() => get(`/api/problems/${problemId}/submissions`), [problemId, refreshKey]);
  if (loading) return <Skeleton h={120} />;
  if (error) return <Alert kind="danger">{error.message}</Alert>;
  if (!data.length) return <EmptyState icon="send" title="No submissions yet">Submit to save a solution here.</EmptyState>;
  return (
    <div className="list">
      {data.map((s) => (
        <div className="list-item" key={s.id}>
          <LangBadge id={s.language} />
          <div className="grow">
            <div className="small" style={{ fontWeight: 500 }}>{RESULT_TEXT[s.status]?.[1] || s.status}{s.testsTotal > 0 && ` · ${s.testsPassed}/${s.testsTotal} tests`}</div>
            <div className="xs subtle">{timeAgo(s.createdAt)}</div>
          </div>
          <Button size="sm" variant="ghost" onClick={() => onLoad(s)}>Load code</Button>
        </div>
      ))}
    </div>
  );
}

function Workspace({ problem, setProblem }) {
  const { prefs, user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const ws = useCodeWorkspace(problem.id, prefs);
  const [tab, setTab] = useState("description");
  const [stdin, setStdin] = useState("");
  const [consoleTab, setConsoleTab] = useState("input");
  const [result, setResult] = useState(null);
  const [running, setRunning] = useState(null);
  const [expanded, setExpanded] = useState(false);
  const [layout, setLayout] = useState(prefs?.editor?.layout || "split");
  const [confirmReset, setConfirmReset] = useState(false);
  const [subKey, setSubKey] = useState(0);

  const exec = ws.execution;
  const canRun = exec?.configured && exec?.enabled && user.emailVerified;
  const runBlockReason = !exec ? "" : !exec.configured ? "Code execution is not configured on this server." : !exec.enabled
    ? "Code execution is temporarily disabled by an administrator." : !user.emailVerified ? "Verify your email to run code." : "";

  const execute = async (kind) => {
    if (!canRun || running) return;
    setRunning(kind);
    setConsoleTab("output");
    setResult(null);
    try {
      const body = { language: ws.language, code: ws.code, stdin, problemId: problem.id };
      const r = kind === "SUBMIT" ? await post(`/api/problems/${problem.id}/submit`, body) : await post("/api/code/run", body);
      setResult(r);
      if (r.progressStatus) setProblem((p) => ({ ...p, progress: { ...p.progress, status: r.progressStatus } }));
      if (kind === "SUBMIT") { setSubKey((k) => k + 1); toast.info(r.message); }
      if (ws.autosave) ws.saveNow();
    } catch (e) {
      setResult({ error: e });
    } finally {
      setRunning(null);
    }
  };

  // Loading a past submission may switch languages first; apply its code once that switch has rendered.
  const [pendingLoad, setPendingLoad] = useState(null);
  useEffect(() => {
    if (pendingLoad && ws.language === pendingLoad.language) {
      ws.setCode(pendingLoad.code);
      setPendingLoad(null);
    }
  }, [pendingLoad, ws]);

  const copy = async () => {
    try { await navigator.clipboard.writeText(ws.code); toast.success("Code copied."); } catch { toast.error("Copying is blocked by the browser."); }
  };

  if (ws.error) return <ErrorState error={ws.error} />;

  const info = (
    <section className="card ws-info stack" aria-label="Problem information">
      <Tabs label="Problem sections" value={tab} onChange={setTab} tabs={[
        { value: "description", label: "Description" }, { value: "notes", label: "Notes" }, { value: "submissions", label: "Submissions" },
      ]} />
      {tab === "description" && <Description problem={problem} />}
      {tab === "notes" && <Notes problem={problem} onSaved={(p) => setProblem((x) => ({ ...x, progress: p }))} />}
      {tab === "submissions" && <Submissions problemId={problem.id} refreshKey={subKey} onLoad={(s) => {
        if (s.language !== ws.language) ws.setLanguage(s.language);
        setPendingLoad({ language: s.language, code: s.code });
        toast.info("Loaded the submitted code into the editor.");
      }} />}
    </section>
  );

  return (
    <>
      <div className={`workspace${layout === "stacked" ? " stacked" : ""}${expanded ? " expanded" : ""}`}>
        {info}
        <section className="card editor-card" aria-label="Code editor">
          <div className="editor-toolbar">
            {ws.ready && <LanguagePicker languages={ws.languages} value={ws.language} onChange={ws.setLanguage} />}
            {ws.current && <span className="file-name">{ws.current.fileName}</span>}
            <span className="xs subtle" aria-live="polite">{SAVE_LABEL[ws.saveState]}{!ws.autosave && ws.saveState === "unsaved" && " · Ctrl+S to save"}</span>
            <div className="row" style={{ marginLeft: "auto", gap: 4 }}>
              <Button size="sm" variant="ghost" className="btn-icon" onClick={copy} aria-label="Copy code" title="Copy code"><Icon name="copy" size={16} /></Button>
              <Button size="sm" variant="ghost" className="btn-icon" onClick={() => setConfirmReset(true)} aria-label="Reset to starter template" title="Reset to starter template"><Icon name="reset" size={16} /></Button>
              <Button size="sm" variant="ghost" className="btn-icon hide-sm" onClick={() => setLayout(layout === "split" ? "stacked" : "split")}
                aria-label={`Switch to ${layout === "split" ? "stacked" : "side-by-side"} layout`} title="Toggle layout"><Icon name="layers" size={16} /></Button>
              <Button size="sm" variant="ghost" className="btn-icon" onClick={() => setExpanded(!expanded)} aria-pressed={expanded}
                aria-label={expanded ? "Exit focus mode" : "Expand editor"} title={expanded ? "Exit focus mode" : "Expand editor"}><Icon name={expanded ? "shrink" : "expand"} size={16} /></Button>
            </div>
          </div>
          <div className="editor-host">
            {ws.ready ? (
              <Suspense fallback={<div className="editor-fallback"><span className="spinner" /> Loading editor…</div>}>
                <CodeEditor value={ws.code} onChange={ws.setCode} language={ws.current?.monacoId} prefs={prefs}
                  onRun={() => execute("RUN")} onSubmit={() => execute("SUBMIT")} onSave={ws.saveNow}
                  ariaLabel={`${ws.current?.label} code editor for ${problem.title}`} />
              </Suspense>
            ) : <div className="editor-fallback"><span className="spinner" /> Loading your saved code…</div>}
          </div>
          <div className="console">
            <div className="row between" style={{ padding: "8px 12px", flexWrap: "wrap", gap: 8 }}>
              <Segmented label="Console" value={consoleTab} onChange={setConsoleTab} options={[{ value: "input", label: "Input" }, { value: "output", label: "Output" }]} />
              <div className="row" style={{ gap: 8 }}>
                <Button size="sm" onClick={() => navigate("/review", { state: { code: ws.code, language: ws.language, problemId: problem.id, problemTitle: problem.title } })}>
                  <Icon name="sparkles" size={14} /> Review
                </Button>
                <Button size="sm" variant="dark" disabled={!canRun} loading={running === "RUN"} onClick={() => execute("RUN")} title="Ctrl+Enter">
                  <Icon name="play" size={14} /> Run
                </Button>
                <Button size="sm" variant="primary" disabled={!canRun} loading={running === "SUBMIT"} onClick={() => execute("SUBMIT")} title="Ctrl+Shift+Enter">
                  <Icon name="send" size={14} /> Submit
                </Button>
              </div>
            </div>
            <div className="console-body">
              {runBlockReason && <Alert kind="warning">{runBlockReason}{!exec?.configured && " Your code is still saved, and AI Review works without execution."}</Alert>}
              {consoleTab === "input" ? (
                <div className="stack-sm">
                  <label htmlFor="stdin" className="label">Standard input (stdin)</label>
                  <textarea id="stdin" className="textarea mono" style={{ minHeight: 90 }} value={stdin} onChange={(e) => setStdin(e.target.value)} maxLength={64000}
                    placeholder="Sample input passed to your program, e.g. 5" />
                </div>
              ) : running ? (
                <p className="row small"><span className="spinner" /> {running === "SUBMIT" ? "Submitting…" : "Running in the sandbox…"}</p>
              ) : result?.error ? (
                <Alert kind="danger">{result.error.message}</Alert>
              ) : result ? <RunResult result={result} /> : (
                <p className="small muted">Run your code to see output here. Nothing is executed until you press Run or Submit.</p>
              )}
            </div>
          </div>
        </section>
      </div>
      {confirmReset && (
        <ConfirmDialog title="Reset code?" danger confirmLabel="Reset" onClose={() => setConfirmReset(false)}
          message={`This replaces your ${ws.current?.label} code with the starter template and deletes the saved draft for this language.`}
          onConfirm={async () => { await ws.reset(); setConfirmReset(false); toast.success("Code reset to the starter template."); }} />
      )}
    </>
  );
}

export default function ProblemDetail() {
  const { idOrSlug } = useParams();
  const { data: problem, error, loading, reload, setData: setProblem } = useAsync(() => get(`/api/problems/${idOrSlug}`), [idOrSlug]);
  useDocumentTitle(problem ? `#${problem.number ?? ""} ${problem.title}` : "Problem");
  useEffect(() => { window.scrollTo(0, 0); }, [idOrSlug]);

  if (error) return error.status === 404
    ? <EmptyState icon="search" title="Problem not found" action={<Link className="btn btn-primary" to="/problems">Browse problems</Link>}>It may have been archived or the link is wrong.</EmptyState>
    : <ErrorState error={error} onRetry={reload} />;
  if (loading && !problem) return <div className="stack"><Skeleton h={60} /><Skeleton h={480} r={20} /></div>;

  const status = problem.progress.status;
  return (
    <>
      <Breadcrumbs items={[{ label: "Practice", to: "/problems" }, { label: problem.topic.label, to: `/problems?topic=${problem.topic.slug}` }, { label: problem.subsectionLabel || "Problem" }]} />
      <header className="page-header" style={{ alignItems: "center" }}>
        <div className="stack-sm">
          <div className="row-wrap">
            {problem.number && <span className="badge badge-accent">#{problem.number}</span>}
            <span className="badge">{problem.topic.label}</span>
            {problem.subsectionLabel && <span className="badge badge-outline">{problem.subsectionLabel}</span>}
            <span className={`badge ${status === "SOLVED" ? "badge-primary" : status === "ATTEMPTED" ? "badge-warning" : ""}`}>{STATUS_LABEL[status]}</span>
          </div>
          <h1>{problem.title}</h1>
        </div>
        <div className="row-wrap">
          <BookmarkButton problem={{ ...problem, bookmarked: problem.progress.bookmarked }}
            onChange={(p) => setProblem((x) => ({ ...x, progress: { ...x.progress, bookmarked: p.bookmarked } }))} />
          <StatusMenu problem={problem} setProblem={setProblem} />
          {problem.previous && <Link className="btn btn-sm" to={`/problems/${problem.previous.slug}`} aria-label={`Previous: ${problem.previous.title}`}><Icon name="arrowLeft" size={14} /> Prev</Link>}
          {problem.next && <Link className="btn btn-sm" to={`/problems/${problem.next.slug}`} aria-label={`Next: ${problem.next.title}`}>Next <Icon name="arrowRight" size={14} /></Link>}
        </div>
      </header>
      <Workspace key={problem.id} problem={problem} setProblem={setProblem} />
    </>
  );
}

function StatusMenu({ problem, setProblem }) {
  const toast = useToast();
  const set = async (status) => {
    try {
      const p = await put(`/api/problems/${problem.id}/progress`, { status });
      setProblem((x) => ({ ...x, progress: p }));
      toast.success(status === "SOLVED" ? "Marked as solved. Nice work!" : `Marked as ${STATUS_LABEL[status].toLowerCase()}.`);
    } catch (e) { toast.error(e); }
  };
  const status = problem.progress.status;
  return status === "SOLVED"
    ? <Button size="sm" onClick={() => set("ATTEMPTED")}><Icon name="reset" size={14} /> Mark unsolved</Button>
    : <Button size="sm" variant="primary" onClick={() => set("SOLVED")}><Icon name="check" size={14} /> Mark solved</Button>;
}
