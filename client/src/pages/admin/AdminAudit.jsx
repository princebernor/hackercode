import { Fragment, useState } from "react";
import { get, post, qs } from "../../api/client.js";
import { useToast } from "../../components/Toasts.jsx";
import { Button, ConfirmDialog, ErrorState, Segmented, Skeleton, SkeletonRows, timeAgo } from "../../components/ui.jsx";
import useAsync from "../../lib/useAsync.js";
import { HealthList } from "./AdminOverview.jsx";

export function AuditLog() {
  const [category, setCategory] = useState("");
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useAsync(() => get(`/api/admin/audit${qs({ category, page, size: 50 })}`), [category, page]);
  return (
    <div className="stack">
      <Segmented label="Event category" value={category} onChange={(v) => { setCategory(v); setPage(0); }}
        options={[{ value: "", label: "All" }, { value: "auth", label: "Authentication" }, { value: "admin", label: "Accounts" }, { value: "problem", label: "Library" }, { value: "config", label: "Configuration" }, { value: "account", label: "Self-service" }]} />
      {error ? <ErrorState error={error} onRetry={reload} /> : loading && !data ? <SkeletonRows rows={8} /> : (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th scope="col">When</th><th scope="col">Action</th><th scope="col">Actor</th><th scope="col">Target</th><th scope="col">Detail</th><th scope="col">Network</th></tr></thead>
            <tbody>
              {data.items.map((e) => (
                <tr key={e.id}>
                  <td className="nowrap small" title={new Date(e.createdAt).toLocaleString()}>{timeAgo(e.createdAt)}</td>
                  <td><span className="badge mono">{e.action}</span></td>
                  <td className="small">{e.actorEmail || <span className="subtle">anonymous</span>}</td>
                  <td className="small muted">{e.targetType ? `${e.targetType}:${e.targetId ?? ""}` : ""}</td>
                  <td className="small">{e.detail}</td>
                  <td className="xs subtle mono">{e.ipHint}</td>
                </tr>
              ))}
              {data.items.length === 0 && <tr><td colSpan={6} className="small muted">No events.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
      {data && data.totalPages > 1 && (
        <nav className="pagination" aria-label="Pagination">
          <span className="small muted">{data.total} events</span>
          <div className="row"><Button size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</Button><Button size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Older</Button></div>
        </nav>
      )}
      <p className="xs muted">The audit log never stores passwords, tokens, secrets or source code. Records are kept for 365 days.</p>
    </div>
  );
}

export function Operations() {
  const toast = useToast();
  const { data, error, loading, reload } = useAsync(() => get("/api/admin/operations"), []);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  if (error) return <ErrorState error={error} onRetry={reload} />;
  if (loading) return <Skeleton h={360} r={20} />;
  const runRetention = async () => {
    setBusy(true);
    try { await post("/api/admin/operations/retention"); toast.success("Retention purge completed."); reload(); } catch (e) { toast.error(e); } finally { setBusy(false); setConfirm(false); }
  };
  return (
    <div className="dash-grid">
      <section className="card span-6">
        <div className="card-header"><h2>Authentication (24 h)</h2></div>
        <dl className="kv"><dt>Failed sign-ins</dt><dd>{data.failedLoginsLast24h}</dd><dt>Blocked (suspended) sign-ins</dt><dd>{data.blockedLoginsLast24h}</dd></dl>
      </section>
      <section className="card span-6">
        <div className="card-header"><h2>Services</h2></div>
        <HealthList health={data.health} />
      </section>
      <section className="card span-6">
        <div className="card-header"><h2>Data retention job</h2><Button size="sm" onClick={() => setConfirm(true)}>Run now</Button></div>
        <dl className="kv">
          {Object.entries(data.retention.policy).map(([k, v]) => <Fragment key={k}><dt>{k}</dt><dd>{v}</dd></Fragment>)}
        </dl>
        <p className="xs muted" style={{ marginTop: 10 }}>Last run: {data.retention.lastRun.status}{data.retention.lastRun.finishedAt ? ` · ${timeAgo(data.retention.lastRun.finishedAt)}` : ""}. Runs daily at 03:15 server time.</p>
      </section>
      <section className="card span-6">
        <div className="card-header"><h2>Failed execution jobs</h2></div>
        {data.executionProviderErrors.length === 0 ? <p className="small muted">No execution-provider errors recorded.</p> : (
          <div className="list">{data.executionProviderErrors.map((r) => (
            <div className="list-item" key={r.id}><span className="badge">{r.language}</span><span className="grow small">Run #{r.id} via {r.provider}</span><span className="xs subtle">{timeAgo(r.createdAt)}</span></div>
          ))}</div>
        )}
      </section>
      {confirm && <ConfirmDialog danger busy={busy} title="Run the retention purge now?" confirmLabel="Run purge"
        message="Records older than the retention periods are permanently deleted." onConfirm={runRetention} onClose={() => setConfirm(false)} />}
    </div>
  );
}
