import { Link } from "react-router-dom";
import { useAuth } from "../api/auth.jsx";
import { get } from "../api/client.js";
import { Avatar, ErrorState, PageHeader, Progress, Skeleton, useDocumentTitle } from "../components/ui.jsx";
import useAsync from "../lib/useAsync.js";

export default function Profile() {
  useDocumentTitle("Profile");
  const { user } = useAuth();
  const { data, error, loading, reload } = useAsync(() => get("/api/dashboard"), []);
  return (
    <>
      <PageHeader title="Profile" actions={<Link className="btn" to="/settings/account">Edit profile</Link>} />
      <div className="dash-grid">
        <section className="card span-4 stack" style={{ alignItems: "center", textAlign: "center" }}>
          <Avatar user={user} size="lg" />
          <div><h2 style={{ fontSize: "var(--text-lg)" }}>{user.displayName}</h2><p className="small muted">{user.email}</p></div>
          <div className="row-wrap" style={{ justifyContent: "center" }}>
            <span className="badge">{user.role}</span>
            {user.emailVerified ? <span className="badge badge-primary">Email verified</span> : <span className="badge badge-warning">Email not verified</span>}
            {user.googleLinked && <span className="badge badge-info">Google connected</span>}
          </div>
          <p className="xs muted">Member since {new Date(user.createdAt).toLocaleDateString(undefined, { month: "long", year: "numeric" })}</p>
        </section>
        <section className="card span-8 stack">
          <h2 style={{ fontSize: "var(--text-md)" }}>Placement progress</h2>
          {error ? <ErrorState error={error} onRetry={reload} /> : loading ? <Skeleton h={160} /> : (
            <>
              <div className="row-wrap" style={{ gap: 24 }}>
                <div className="stat"><span className="stat-label">Solved</span><span className="stat-value">{data.totals.solved}<small>/ {data.totals.problems}</small></span></div>
                <div className="stat"><span className="stat-label">Current streak</span><span className="stat-value">{data.streak.current}<small>days</small></span></div>
                <div className="stat"><span className="stat-label">Longest streak</span><span className="stat-value">{data.streak.longest}<small>days</small></span></div>
              </div>
              <div className="topic-bars">
                {data.topics.filter((t) => t.solved + t.attempted > 0).map((t) => (
                  <div className="topic-bar" key={t.slug}><span className="truncate">{t.label}</span><Progress value={t.solved} max={t.total} small label={`${t.label} progress`} /><span className="xs muted">{t.solved}/{t.total}</span></div>
                ))}
                {data.topics.every((t) => t.solved + t.attempted === 0) && <p className="small muted">No topic progress yet. <Link to="/problems">Start practicing</Link>.</p>}
              </div>
            </>
          )}
        </section>
      </div>
    </>
  );
}
