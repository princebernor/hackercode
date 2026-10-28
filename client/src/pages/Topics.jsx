import { Link } from "react-router-dom";
import { get } from "../api/client.js";
import { ErrorState, PageHeader, Progress, Skeleton, useDocumentTitle } from "../components/ui.jsx";
import useAsync from "../lib/useAsync.js";

export default function Topics() {
  useDocumentTitle("Topics & patterns");
  const { data, error, loading, reload } = useAsync(() => get("/api/topics"), []);
  return (
    <>
      <PageHeader title="Topics & patterns" subtitle="The sheet's 18 topics and their sub-patterns, with your progress in each." />
      {error ? <ErrorState error={error} onRetry={reload} /> : loading ? (
        <div className="topic-grid">{Array.from({ length: 9 }, (_, i) => <Skeleton key={i} h={220} r={20} />)}</div>
      ) : (
        <div className="topic-grid">
          {data.map((t) => (
            <article key={t.slug} className="card topic-card" aria-labelledby={`t-${t.slug}`}>
              <div className="row" style={{ alignItems: "flex-start" }}>
                <span className="topic-num" aria-hidden="true">{t.position}</span>
                <div className="grow">
                  <h2 id={`t-${t.slug}`} style={{ fontSize: "var(--text-md)" }}><Link to={`/problems?topic=${t.slug}`} style={{ color: "inherit" }}>{t.label}</Link></h2>
                  <div className="row-wrap" style={{ marginTop: 4 }}>
                    {t.kind && <span className="badge">{t.kind}</span>}
                    <span className="badge badge-primary">{t.total} problems</span>
                  </div>
                </div>
              </div>
              {t.note && <p className="xs muted">Sheet note: “{t.note}”</p>}
              <div className="stack-sm">
                <div className="row between xs muted"><span>{t.solved} solved · {t.attempted} attempted</span><span>{t.total ? Math.round((t.solved / t.total) * 100) : 0}%</span></div>
                <Progress value={t.solved} max={t.total} label={`${t.label} progress`} small />
              </div>
              <div className="sub-list">
                {t.subsections.map((s) => (
                  <Link key={s.name} to={`/problems?topic=${t.slug}&subsection=${encodeURIComponent(s.name)}`} title={s.note || undefined}>
                    <span className="truncate">{s.label}</span><span className="nowrap">{s.solved}/{s.total}</span>
                  </Link>
                ))}
              </div>
            </article>
          ))}
        </div>
      )}
    </>
  );
}
