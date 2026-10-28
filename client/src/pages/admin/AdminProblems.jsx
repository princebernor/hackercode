import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../api/auth.jsx";
import { get, post, put, qs } from "../../api/client.js";
import { useToast } from "../../components/Toasts.jsx";
import { Alert, Button, ConfirmDialog, Dialog, ErrorState, Field, SkeletonRows } from "../../components/ui.jsx";
import useAsync from "../../lib/useAsync.js";

const EMPTY = { title: "", topicSlug: "", subsection: "", difficulty: "", statement: "", constraints: "", examples: "", testsJson: "", practiceUrls: "" };

function ProblemEditor({ id, topics, onClose, onSaved }) {
  const toast = useToast();
  const isNew = id === "new";
  const record = useAsync(() => (isNew ? Promise.resolve(null) : get(`/api/admin/problems/${id}`)), [id]);
  const [form, setForm] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const r = record.data;
  const values = form || (r ? Object.fromEntries(Object.keys(EMPTY).map((k) => [k, r[k] ?? ""])) : { ...EMPTY, topicSlug: topics[0]?.slug || "" });
  const set = (k) => (e) => setForm({ ...values, [k]: e.target.value });

  const save = async () => {
    setBusy(true);
    setError("");
    try {
      const saved = isNew ? await post("/api/admin/problems", values) : await put(`/api/admin/problems/${id}`, values);
      toast.success(isNew ? "Problem created." : "Problem updated.");
      onSaved(saved);
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  };

  return (
    <Dialog wide title={isNew ? "Add problem" : `Edit ${r ? (r.number ? `#${r.number}` : r.id) : ""}`} onClose={onClose}
      actions={<><Button variant="ghost" onClick={onClose}>Cancel</Button><Button variant="primary" loading={busy} onClick={save} disabled={record.loading}>Save</Button></>}>
      {record.loading ? <SkeletonRows rows={5} /> : (
        <div className="stack">
          {error && <Alert kind="danger">{error}</Alert>}
          {r?.provenance === "SOURCE_PDF" && (
            <div className="source-block xs">
              <strong>Source record (read-only):</strong> #{r.number} “{r.sourceText}”. Source numbering, text and links are preserved; edits below are tracked as operator content.
            </div>
          )}
          <Field label="Display title" required>{(p) => <input className="input" maxLength={300} value={values.title} onChange={set("title")} {...p} />}</Field>
          <div className="row-wrap" style={{ alignItems: "flex-start" }}>
            <div className="grow"><Field label="Topic">{(p) => <select className="select" value={values.topicSlug} onChange={set("topicSlug")} {...p}>{topics.map((t) => <option key={t.slug} value={t.slug}>{t.name}</option>)}</select>}</Field></div>
            <div className="grow"><Field label="Difficulty" hint="Leave blank unless you have a reliable source.">{(p) => <select className="select" value={values.difficulty} onChange={set("difficulty")} {...p}><option value="">Not set</option><option>Easy</option><option>Medium</option><option>Hard</option></select>}</Field></div>
          </div>
          <Field label="Pattern / subsection">{(p) => <input className="input" value={values.subsection} onChange={set("subsection")} {...p} />}</Field>
          <Field label="Practice links" hint="One https:// URL per line. Links from the source PDF cannot be removed.">{(p) => <textarea className="textarea mono" style={{ minHeight: 70 }} value={values.practiceUrls} onChange={set("practiceUrls")} {...p} />}</Field>
          <Field label="Problem statement">{(p) => <textarea className="textarea" value={values.statement} onChange={set("statement")} {...p} />}</Field>
          <Field label="Constraints">{(p) => <textarea className="textarea mono" style={{ minHeight: 70 }} value={values.constraints} onChange={set("constraints")} {...p} />}</Field>
          <Field label="Examples">{(p) => <textarea className="textarea mono" style={{ minHeight: 90 }} value={values.examples} onChange={set("examples")} {...p} />}</Field>
          <Field label="Test cases (JSON)" hint='Array of {"input": "...", "output": "...", "hidden": false}. Used by Submit. Max 20.'>
            {(p) => <textarea className="textarea mono" style={{ minHeight: 90 }} placeholder='[{"input": "5\n", "output": "odd"}]' value={values.testsJson} onChange={set("testsJson")} {...p} />}
          </Field>
        </div>
      )}
    </Dialog>
  );
}

export default function AdminProblems() {
  const { isAdmin } = useAuth();
  const toast = useToast();
  const [filters, setFilters] = useState({ q: "", topic: "", page: 0 });
  const [query, setQuery] = useState("");
  const topics = useAsync(() => get("/api/admin/topics"), []);
  const list = useAsync(() => get(`/api/admin/problems${qs({ ...filters, size: 50 })}`), [filters.q, filters.topic, filters.page]);
  const [validation, setValidation] = useState(null);
  const [editing, setEditing] = useState(null);
  const [archiving, setArchiving] = useState(null);
  const [busy, setBusy] = useState(null);

  const validate = async () => {
    setBusy("validate");
    try { setValidation(await get("/api/admin/problems/validation")); } catch (e) { toast.error(e); } finally { setBusy(null); }
  };
  const reimport = async () => {
    setBusy("import");
    try {
      const r = await post("/api/admin/problems/import");
      setValidation(r.validation);
      toast.success(`Import complete: ${r.result.problemsCreated} created, ${r.result.problemsAlreadyPresent} already present.`);
      list.reload();
    } catch (e) { toast.error(e); } finally { setBusy(null); }
  };
  const toggleArchive = async () => {
    try {
      await post(`/api/admin/problems/${archiving.id}/archive`, { archived: !archiving.archived });
      toast.success(archiving.archived ? "Problem restored." : "Problem archived.");
      setArchiving(null);
      list.reload();
    } catch (e) { toast.error(e); }
  };

  return (
    <div className="stack">
      <div className="row-wrap between">
        <form className="row-wrap grow" onSubmit={(e) => { e.preventDefault(); setFilters({ ...filters, q: query, page: 0 }); }}>
          <label htmlFor="apq" className="sr-only">Search problems</label>
          <input id="apq" className="input" style={{ maxWidth: 320 }} placeholder="Search title, #number, keyword" value={query} onChange={(e) => setQuery(e.target.value)} />
          <select className="select" aria-label="Topic" style={{ maxWidth: 260 }} value={filters.topic} onChange={(e) => setFilters({ ...filters, topic: e.target.value, page: 0 })}>
            <option value="">All topics</option>{topics.data?.map((t) => <option key={t.slug} value={t.slug}>{t.name}</option>)}
          </select>
          <Button type="submit">Search</Button>
        </form>
        <div className="row-wrap">
          <Button loading={busy === "validate"} onClick={validate}>Validate library</Button>
          {isAdmin && <Button loading={busy === "import"} onClick={reimport}>Re-run import</Button>}
          <Button variant="primary" onClick={() => setEditing("new")}>Add problem</Button>
        </div>
      </div>

      {validation && (
        <div className="card stack-sm">
          {validation.ok
            ? <Alert kind="success">Validation passed: all {validation.expected} source entries are present, numbered 1–{validation.expected} in order, and match the PDF extract.</Alert>
            : <Alert kind="warning">{validation.issues.length} issue(s) found. {validation.found} of {validation.expected} source entries present.</Alert>}
          {validation.issues.length > 0 && <ul className="small" style={{ margin: 0 }}>{validation.issues.slice(0, 20).map((i) => <li key={i}>{i}</li>)}</ul>}
          <p className="xs muted">
            {validation.stats.topics} topics · {validation.stats.subsections} patterns · {validation.stats.withPracticeUrl} with practice links · {validation.stats.withoutPracticeUrl} without (as in the PDF)
            {validation.stats.possibleDuplicateTitles?.length > 0 && ` · possible duplicate titles: ${validation.stats.possibleDuplicateTitles.join(" ")}`}
          </p>
        </div>
      )}

      {list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : list.loading && !list.data ? <SkeletonRows rows={8} /> : (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th scope="col">#</th><th scope="col">Title</th><th scope="col">Topic · pattern</th><th scope="col">Content</th><th scope="col"><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>
              {list.data.items.map((p) => (
                <tr key={p.id} style={p.archived ? { opacity: 0.6 } : undefined}>
                  <td className="mono small">{p.number ?? <span className="badge badge-accent">staff</span>}</td>
                  <td><Link to={`/problems/${p.slug}`}>{p.title}</Link>{p.archived && <span className="badge badge-danger" style={{ marginLeft: 6 }}>Archived</span>}</td>
                  <td className="small muted">{p.topic.label} · {p.subsectionLabel}</td>
                  <td className="xs">{[p.hasPracticeUrl && "link", p.hasStatement && "statement", p.difficulty].filter(Boolean).join(" · ") || <span className="subtle">title only</span>}</td>
                  <td className="nowrap" style={{ textAlign: "right" }}>
                    <Button size="sm" variant="ghost" onClick={() => setEditing(p.id)}>Edit</Button>
                    <Button size="sm" variant="ghost" onClick={() => setArchiving(p)}>{p.archived ? "Restore" : "Archive"}</Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {list.data && list.data.totalPages > 1 && (
        <nav className="pagination" aria-label="Pagination">
          <span className="small muted">{list.data.total} records · page {filters.page + 1} of {list.data.totalPages}</span>
          <div className="row"><Button size="sm" disabled={filters.page === 0} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>Previous</Button>
            <Button size="sm" disabled={filters.page + 1 >= list.data.totalPages} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>Next</Button></div>
        </nav>
      )}
      {editing && topics.data && <ProblemEditor id={editing} topics={topics.data} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); list.reload(); }} />}
      {archiving && (
        <ConfirmDialog danger={!archiving.archived} title={archiving.archived ? "Restore this problem?" : "Archive this problem?"}
          message={archiving.archived ? "It will appear in the library again." : "Archived problems are hidden from learners. Their progress is kept and the record can be restored."}
          confirmLabel={archiving.archived ? "Restore" : "Archive"} onConfirm={toggleArchive} onClose={() => setArchiving(null)} />
      )}
    </div>
  );
}
