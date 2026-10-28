import { Link } from "react-router-dom";
import { get } from "../api/client.js";
import { ErrorState, PageHeader, Progress, Skeleton, useDocumentTitle } from "../components/ui.jsx";
import useAsync from "../lib/useAsync.js";
import { Heatmap } from "./Dashboard.jsx";

export default function ProgressPage() {
  useDocumentTitle("Progress");
  const { data, error, loading, reload } = useAsync(() => get("/api/dashboard"), []);
  if (error) return <ErrorState error={error} onRetry={reload} />;
  if (loading) return <Skeleton h={480} r={20} />;
  const t = data.totals;
  return (
    <>
      <PageHeader title="Progress" subtitle="Everything here is computed from your own activity." />
      <div className="dash-grid">
        <section className="card span-4 stat"><span className="stat-label">Completion</span><span className="stat-value">{t.completionPercent}%</span><Progress value={t.solved} max={t.problems} label="Overall completion" /></section>
        <section className="card span-4 stat"><span className="stat-label">Solved / attempted</span><span className="stat-value">{t.solved}<small>/ {t.attempted} attempted</small></span></section>
        <section className="card span-4 stat"><span className="stat-label">Streak</span><span className="stat-value">{data.streak.current}<small>days · longest {data.streak.longest}</small></span></section>
        <section className="card span-12">
          <div className="card-header"><h2>Activity</h2></div>
          <Heatmap days={data.heatmap} />
        </section>
        <section className="card span-12">
          <div className="card-header"><h2>By topic and pattern</h2></div>
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th scope="col">Topic</th><th scope="col">Pattern</th><th scope="col">Solved</th><th scope="col" style={{ width: "30%" }}>Progress</th></tr></thead>
              <tbody>
                {data.topics.flatMap((tp) => tp.subsections.map((s, i) => (
                  <tr key={tp.slug + s.name}>
                    <td>{i === 0 ? <Link to={`/problems?topic=${tp.slug}`}><strong>{tp.label}</strong></Link> : ""}</td>
                    <td><Link to={`/problems?topic=${tp.slug}&subsection=${encodeURIComponent(s.name)}`}>{s.label}</Link></td>
                    <td className="nowrap">{s.solved} / {s.total}</td>
                    <td><Progress value={s.solved} max={s.total} label={`${s.label} progress`} small /></td>
                  </tr>
                )))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </>
  );
}
