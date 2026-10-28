import { lazy, Suspense, useCallback, useState } from "react";
import { Link } from "react-router-dom";
import { prefersReducedMotion, useAuth } from "../api/auth.jsx";
import { get } from "../api/client.js";
import Icon from "../components/Icon.jsx";
import Orbs from "../components/Orbs.jsx";
import { BookmarkButton, StatusDot } from "../components/ProblemBits.jsx";
import { EmptyState, ErrorState, LangBadge, Progress, Skeleton, timeAgo, useDocumentTitle } from "../components/ui.jsx";
import useAsync from "../lib/useAsync.js";

const HeroScene = lazy(() => import("../three/HeroScene.jsx"));

const LANG_LABEL = { c: "C", cpp: "C++", java: "Java", python: "Python", javascript: "JavaScript", go: "Go" };
const ACTIVITY_LABEL = {
  PROBLEM_VIEWED: "Viewed", CODE_RUN: "Ran code for", CODE_SUBMITTED: "Submitted", PROBLEM_SOLVED: "Solved",
  REVIEW_REQUESTED: "Requested a review", NOTE_SAVED: "Updated notes on",
};

function supportsWebGL() {
  try {
    const c = document.createElement("canvas");
    return !!(c.getContext("webgl2") || c.getContext("webgl"));
  } catch {
    return false;
  }
}

function HeroVisual({ reduced }) {
  const [webgl, setWebgl] = useState(() => supportsWebGL());
  const fail = useCallback(() => setWebgl(false), []);
  if (!webgl) {
    return (
      <div className="hero-scene" style={{ position: "relative" }}>
        <Orbs parallax={false} reduced={reduced} orbs={[
          { c: "teal", s: 130, x: 45, y: 15, z: 0, dur: 14, dx: 8, dy: -10 },
          { c: "orange", s: 80, x: 15, y: 5, z: 0, dur: 12, dx: -6, dy: 8 },
          { c: "cyan", s: 60, x: 25, y: 60, z: 0, dur: 13, dx: 6, dy: -6 },
        ]} />
      </div>
    );
  }
  return (
    <Suspense fallback={<div className="hero-scene" />}>
      <HeroScene reduced={reduced} onFail={fail} />
    </Suspense>
  );
}

export function Heatmap({ days }) {
  const max = Math.max(1, ...days.map((d) => d.count));
  const level = (c) => (c === 0 ? 0 : Math.min(4, Math.ceil((c / max) * 4)));
  const active = days.filter((d) => d.count > 0).length;
  // Pad so the first column starts on Sunday.
  const pad = new Date(days[0].day + "T00:00:00Z").getUTCDay();
  return (
    <div className="stack-sm">
      <div className="heatmap" role="img" aria-label={`Activity heatmap: active on ${active} of the last ${days.length} days`}>
        {Array.from({ length: pad }, (_, i) => <span key={`p${i}`} style={{ visibility: "hidden" }} />)}
        {days.map((d) => <span key={d.day} data-l={level(d.count)} title={`${d.day}: ${d.count} event${d.count === 1 ? "" : "s"}`} />)}
      </div>
      <div className="legend">Less <span style={{ background: "var(--surface-3)" }} /><span style={{ background: "#b4f0dc" }} /><span style={{ background: "#5fdcb6" }} /><span style={{ background: "#1fb98d" }} /><span style={{ background: "#0b7c64" }} /> More</div>
    </div>
  );
}

function Stat({ icon, tone, label, value, sub }) {
  return (
    <div className="card card-tight stat">
      <span className="stat-label"><span className={`stat-icon ${tone || ""}`}><Icon name={icon} size={16} /></span>{label}</span>
      <span className="stat-value">{value}{sub && <small>{sub}</small>}</span>
    </div>
  );
}

export default function Dashboard() {
  useDocumentTitle("Dashboard");
  const { user, prefs } = useAuth();
  const { data, error, loading, reload, setData } = useAsync(() => get("/api/dashboard"), []);
  const reduced = prefersReducedMotion(prefs);
  const a = prefs?.appearance || {};

  if (error && !data) return <ErrorState error={error} onRetry={reload} />;

  const first = user.displayName.split(" ")[0];
  const updateBookmark = (p) => setData((d) => ({ ...d, bookmarks: d.bookmarks.map((x) => (x.id === p.id ? p : x)).filter((x) => x.bookmarked) }));

  return (
    <div className="dash-grid">
      <section className="hero span-12" aria-labelledby="welcome">
        <div>
          <p className="small" style={{ opacity: 0.8 }}>{new Date().toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" })}</p>
          <h1 id="welcome">{data?.isNew ? `Welcome, ${first}. Let's begin.` : `Welcome back, ${first}.`}</h1>
          <p>
            {loading && !data ? "Loading your progress…"
              : data.isNew ? "Your dashboard fills in as you practice. Start with the first recommended problem below, or paste code into AI Code Review."
                : `You've solved ${data.totals.solved} of ${data.totals.problems} problems. ${data.streak.current > 0 ? `${data.streak.current}-day streak — keep it going.` : "Practice today to start a streak."}`}
          </p>
          <div className="hero-actions">
            {data?.recommendations?.[0] && (
              <Link className="btn btn-primary" to={`/problems/${data.recommendations[0].problem.slug}`}>
                <Icon name="play" size={14} /> {data.isNew ? "Start problem #1" : "Continue practicing"}
              </Link>
            )}
            <Link className="btn btn-ghost" to="/review"><Icon name="sparkles" size={16} /> Review code</Link>
          </div>
        </div>
        <HeroVisual reduced={reduced} />
      </section>

      {loading && !data ? (
        <>
          {[0, 1, 2, 3].map((i) => <div key={i} className="span-3"><Skeleton h={104} r={20} /></div>)}
          <div className="span-8"><Skeleton h={300} r={20} /></div>
          <div className="span-4"><Skeleton h={300} r={20} /></div>
        </>
      ) : (
        <>
          <div className="span-3"><Stat icon="check" label="Solved" value={data.totals.solved} sub={`/ ${data.totals.problems}`} /></div>
          <div className="span-3"><Stat icon="clock" tone="accent" label="Attempted" value={data.totals.attempted} /></div>
          <div className="span-3"><Stat icon="flame" tone="accent" label="Current streak" value={data.streak.current} sub={`day${data.streak.current === 1 ? "" : "s"} · best ${data.streak.longest}`} /></div>
          <div className="span-3"><Stat icon="target" tone="info" label="Today's goal" value={`${data.goals.solvedToday}/${data.goals.dailyGoal}`} sub={`· week ${data.goals.solvedThisWeek}/${data.goals.weeklyGoal}`} /></div>

          {a.showRecommendations !== false && <section className="card span-8" aria-labelledby="rec-title">
            <div className="card-header">
              <h2 id="rec-title">Recommended next</h2>
              <Link to="/problems" className="small">Browse all</Link>
            </div>
            {data.recommendations.length === 0 ? (
              <EmptyState icon="check" title="Nothing left to recommend">You have touched every topic. Revisit bookmarks or attempted problems.</EmptyState>
            ) : (
              <div className="list">
                {data.recommendations.map((r) => (
                  <div className="list-item" key={r.problem.id}>
                    <StatusDot status={r.problem.status} />
                    <div className="grow">
                      <Link to={`/problems/${r.problem.slug}`}>#{r.problem.number} {r.problem.title}</Link>
                      <div className="xs muted">{r.reason}</div>
                    </div>
                    <span className="badge">{r.problem.topic.label}</span>
                  </div>
                ))}
              </div>
            )}
          </section>}

          <section className={`card ${a.showRecommendations !== false ? "span-4" : "span-12"} stack`} aria-labelledby="completion-title">
            <h2 id="completion-title" style={{ fontSize: "var(--text-md)" }}>Sheet completion</h2>
            <div className="row" style={{ gap: 20 }}>
              <div className="ring" style={{ "--p": data.totals.completionPercent }} role="img" aria-label={`${data.totals.completionPercent}% complete`}>
                <div><div><strong>{data.totals.completionPercent}%</strong><span className="xs muted">complete</span></div></div>
              </div>
              <dl className="kv">
                <dt>Solved</dt><dd>{data.totals.solved}</dd>
                <dt>Attempted</dt><dd>{data.totals.attempted}</dd>
                <dt>Bookmarked</dt><dd>{data.totals.bookmarked}</dd>
                <dt>Remaining</dt><dd>{data.totals.problems - data.totals.solved}</dd>
              </dl>
            </div>
          </section>

          {a.showActivityHeatmap !== false && (
            <section className="card span-8" aria-labelledby="activity-title">
              <div className="card-header"><h2 id="activity-title">Activity · last 26 weeks</h2><span className="xs muted">UTC days</span></div>
              <Heatmap days={data.heatmap} />
              {data.isNew && <p className="small muted" style={{ marginTop: 12 }}>No activity yet. Viewing, running and solving problems will appear here.</p>}
            </section>
          )}

          {a.showLanguageChart !== false && (
            <section className="card span-4" aria-labelledby="lang-title">
              <div className="card-header"><h2 id="lang-title">Language usage</h2></div>
              {Object.keys(data.languages).length === 0 ? (
                <p className="small muted">Run or review code to see which languages you use most.</p>
              ) : (
                <div className="bars">
                  {Object.entries(data.languages).sort((x, y) => y[1] - x[1]).map(([lang, n]) => {
                    const max = Math.max(...Object.values(data.languages));
                    return (
                      <div className="bar-row" key={lang}>
                        <span className="row" style={{ gap: 8 }}><LangBadge id={lang} />{LANG_LABEL[lang] || lang}</span>
                        <Progress value={n} max={max} label={`${LANG_LABEL[lang]} usage`} small />
                        <span className="xs muted">{n}</span>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>
          )}

          <section className="card span-7" aria-labelledby="topics-title">
            <div className="card-header"><h2 id="topics-title">Topic progress</h2><Link to="/topics" className="small">All topics</Link></div>
            <div className="topic-bars">
              {data.topics.map((t) => (
                <div className="topic-bar" key={t.slug}>
                  <Link to={`/problems?topic=${t.slug}`} className="truncate">{t.label}</Link>
                  <Progress value={t.solved} max={t.total} label={`${t.label}: ${t.solved} of ${t.total} solved`} small />
                  <span className="xs muted" style={{ textAlign: "right" }}>{t.solved}/{t.total}</span>
                </div>
              ))}
            </div>
          </section>

          <section className="card span-5" aria-labelledby="recent-title">
            <div className="card-header"><h2 id="recent-title">Recent activity</h2></div>
            {data.recentActivity.length === 0 ? <p className="small muted">Nothing yet.</p> : (
              <div className="list">
                {data.recentActivity.slice(0, 8).map((e, i) => (
                  <div className="list-item" key={i}>
                    <div className="grow small">
                      {ACTIVITY_LABEL[e.type]} {e.problemTitle ? <Link to={`/problems/${e.problemId}`}>{e.problemTitle}</Link> : e.type === "REVIEW_REQUESTED" ? "" : "code"}
                      {e.language && <span className="subtle"> · {LANG_LABEL[e.language]}</span>}
                    </div>
                    <span className="xs subtle nowrap">{timeAgo(e.at)}</span>
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="card span-4" aria-labelledby="viewed-title">
            <div className="card-header"><h2 id="viewed-title">Recently viewed</h2></div>
            {data.recentlyViewed.length === 0 ? <p className="small muted">Problems you open will show up here.</p> : (
              <div className="list">
                {data.recentlyViewed.map((p) => (
                  <div className="list-item" key={p.id}><StatusDot status={p.status} /><div className="grow truncate"><Link to={`/problems/${p.slug}`}>{p.title}</Link></div></div>
                ))}
              </div>
            )}
          </section>

          <section className="card span-4" aria-labelledby="bm-title">
            <div className="card-header"><h2 id="bm-title">Bookmarks</h2><Link to="/bookmarks" className="small">All</Link></div>
            {data.bookmarks.length === 0 ? <p className="small muted">Bookmark problems to revisit them quickly.</p> : (
              <div className="list">
                {data.bookmarks.map((p) => (
                  <div className="list-item" key={p.id}><div className="grow truncate"><Link to={`/problems/${p.slug}`}>{p.title}</Link></div><BookmarkButton problem={p} onChange={updateBookmark} /></div>
                ))}
              </div>
            )}
          </section>

          <section className="card span-4" aria-labelledby="reviews-title">
            <div className="card-header"><h2 id="reviews-title">Recent code reviews</h2><Link to="/review" className="small">Open</Link></div>
            {data.recentReviews.length === 0 ? <p className="small muted">No reviews yet.</p> : (
              <div className="list">
                {data.recentReviews.map((r) => (
                  <div className="list-item" key={r.id}>
                    <LangBadge id={r.language} />
                    <div className="grow"><div className="small truncate">{r.summary}</div><div className="xs subtle">{r.aiGenerated ? "AI review" : "Static checks only"} · {timeAgo(r.createdAt)}</div></div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
