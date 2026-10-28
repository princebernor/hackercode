import { Link } from "react-router-dom";
import { get } from "../../api/client.js";
import { Alert, ErrorState, Skeleton, timeAgo } from "../../components/ui.jsx";
import useAsync from "../../lib/useAsync.js";

export function HealthList({ health }) {
  const rows = [
    ["Database", health.database === "up", health.database],
    ["Code execution", health.execution.configured && health.execution.enabled,
      health.execution.configured ? `${health.execution.provider}${health.execution.enabled ? "" : " (disabled)"}` : "not configured"],
    ["AI review", health.aiReview.configured && health.aiReview.enabled,
      health.aiReview.configured ? `${health.aiReview.provider} · ${health.aiReview.model}${health.aiReview.enabled ? "" : " (disabled)"}` : "not configured"],
    ["Email delivery", health.email === "smtp", health.email === "dev_console" ? "development console only" : health.email],
    ["Google sign-in", health.googleOAuth === "configured", health.googleOAuth],
    ["Maintenance mode", !health.maintenance, health.maintenance ? "ON" : "off"],
  ];
  return (
    <div className="list">
      {rows.map(([name, ok, detail]) => (
        <div className="list-item" key={name}>
          <span className={`status-dot ${ok ? "up" : "down"}`} aria-hidden="true" />
          <span className="grow">{name}</span>
          <span className="small muted">{detail}</span>
        </div>
      ))}
    </div>
  );
}

function Metric({ label, value }) {
  return <div className="card card-tight stat"><span className="stat-label">{label}</span><span className="stat-value">{value}</span></div>;
}

export default function AdminOverview() {
  const { data, error, loading, reload } = useAsync(() => get("/api/admin/overview"), []);
  if (error) return <ErrorState error={error} onRetry={reload} />;
  if (loading) return <Skeleton h={420} r={20} />;
  const { users, coding, reviews, library, health } = data;
  return (
    <div className="dash-grid">
      <div className="span-3"><Metric label="Users" value={users.total} /></div>
      <div className="span-3"><Metric label="Active (24 h / 7 d)" value={`${users.activeLast24h} / ${users.activeLast7Days}`} /></div>
      <div className="span-3"><Metric label="Code runs (24 h)" value={coding.runsLast24h} /></div>
      <div className="span-3"><Metric label="Reviews (24 h, AI)" value={`${reviews.reviewsLast24h} (${reviews.aiReviewsLast24h})`} /></div>

      <section className="card span-6">
        <div className="card-header"><h2>Problem library</h2><Link to="/admin/problems" className="small">Manage</Link></div>
        {library.valid
          ? <Alert kind="success">All {library.expected} source entries present and matching the PDF extract.</Alert>
          : <Alert kind="warning">{library.sourceEntriesPresent} of {library.expected} source entries present, {library.issues} issue(s). Run validation for details.</Alert>}
        <dl className="kv" style={{ marginTop: 12 }}>
          <dt>Total records</dt><dd>{library.stats.databaseTotal}</dd>
          <dt>Topics / patterns</dt><dd>{library.stats.topics} / {library.stats.subsections}</dd>
          <dt>With practice link</dt><dd>{library.stats.withPracticeUrl}</dd>
          <dt>With statement / tests</dt><dd>{library.stats.withStatement} / {library.stats.withTests}</dd>
          <dt>Added by staff / archived</dt><dd>{library.stats.adminAdded} / {library.stats.archived}</dd>
        </dl>
      </section>

      <section className="card span-6">
        <div className="card-header"><h2>Service health</h2></div>
        <HealthList health={health} />
      </section>

      <section className="card span-6">
        <div className="card-header"><h2>Learning activity</h2></div>
        <dl className="kv">
          <dt>Problems solved (all users)</dt><dd>{coding.problemsSolvedTotal}</dd>
          <dt>Solved in last 7 days</dt><dd>{coding.problemsSolvedLast7Days}</dd>
          <dt>Learners with progress</dt><dd>{coding.learnersWithProgress}</dd>
          <dt>Execution provider errors (24 h)</dt><dd>{coding.providerErrorsLast24h}</dd>
          <dt>Signed in last 7 days</dt><dd>{users.signedInLast7Days}</dd>
          <dt>Admins / moderators</dt><dd>{users.admins} / {users.moderators}</dd>
        </dl>
      </section>

      <section className="card span-6">
        <div className="card-header"><h2>Recent administrative events</h2></div>
        {data.recentEvents.length === 0 ? <p className="small muted">No events yet.</p> : (
          <div className="list">
            {data.recentEvents.map((e) => (
              <div className="list-item" key={e.id}>
                <span className="badge mono">{e.action}</span>
                <span className="grow small truncate">{e.actorEmail || "anonymous"}{e.detail ? ` · ${e.detail}` : ""}</span>
                <span className="xs subtle nowrap">{timeAgo(e.createdAt)}</span>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
