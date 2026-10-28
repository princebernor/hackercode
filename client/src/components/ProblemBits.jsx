import { Link } from "react-router-dom";
import { put } from "../api/client.js";
import Icon from "./Icon.jsx";
import { useToast } from "./Toasts.jsx";

export const STATUS_LABEL = { NOT_STARTED: "Not started", ATTEMPTED: "Attempted", SOLVED: "Solved" };

export function StatusDot({ status }) {
  const cls = status === "SOLVED" ? "solved" : status === "ATTEMPTED" ? "attempted" : "";
  return <span className={`status-dot ${cls}`} title={STATUS_LABEL[status]} aria-label={STATUS_LABEL[status]} role="img" />;
}

export function BookmarkButton({ problem, onChange }) {
  const toast = useToast();
  const toggle = async () => {
    const next = !problem.bookmarked;
    onChange?.({ ...problem, bookmarked: next });
    try {
      await put(`/api/problems/${problem.id}/progress`, { bookmarked: next });
    } catch (e) {
      onChange?.({ ...problem, bookmarked: !next });
      toast.error(e);
    }
  };
  return (
    <button className="bookmark-btn" aria-pressed={!!problem.bookmarked} onClick={toggle}
      aria-label={problem.bookmarked ? `Remove bookmark from ${problem.title}` : `Bookmark ${problem.title}`}>
      <Icon name="bookmark" />
    </button>
  );
}

/** One problem in a list: status, source number, title, topic/pattern, practice-link indicator, bookmark. */
export function ProblemRow({ problem, onChange, showTopic = true }) {
  return (
    <div className="problem-row">
      <span className="num">{problem.number ? `#${problem.number}` : "new"}</span>
      <div style={{ minWidth: 0 }}>
        <div className="row" style={{ gap: 8 }}>
          <StatusDot status={problem.status} />
          <Link className="title truncate" to={`/problems/${problem.slug}`}>{problem.title}</Link>
        </div>
        <div className="meta">
          {showTopic && <span className="badge">{problem.topic.label}</span>}
          {problem.subsectionLabel && <span className="badge badge-outline">{problem.subsectionLabel}</span>}
          {problem.difficulty && <span className="badge badge-info">{problem.difficulty}</span>}
          {problem.provenance === "ADMIN" && <span className="badge badge-accent">Added by staff</span>}
          {problem.archived && <span className="badge badge-danger">Archived</span>}
        </div>
      </div>
      <span className="hide-sm xs subtle">{problem.hasPracticeUrl ? "Practice link" : "No source link"}</span>
      <BookmarkButton problem={problem} onChange={onChange} />
    </div>
  );
}
