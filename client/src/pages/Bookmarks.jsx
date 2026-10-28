import { Link } from "react-router-dom";
import { get } from "../api/client.js";
import { ProblemRow } from "../components/ProblemBits.jsx";
import { EmptyState, ErrorState, PageHeader, SkeletonRows, useDocumentTitle } from "../components/ui.jsx";
import useAsync from "../lib/useAsync.js";

export default function Bookmarks() {
  useDocumentTitle("Bookmarks");
  const { data, error, loading, reload, setData } = useAsync(() => get("/api/problems?bookmarked=true&size=100"), []);
  const onChange = (p) => setData((d) => ({ ...d, items: d.items.filter((x) => x.id !== p.id || p.bookmarked) }));
  return (
    <>
      <PageHeader title="Bookmarks" subtitle="Problems you saved to revisit." />
      {error ? <ErrorState error={error} onRetry={reload} /> : loading ? <SkeletonRows rows={6} h={58} /> : data.items.length === 0 ? (
        <div className="card"><EmptyState icon="bookmark" title="No bookmarks yet" action={<Link className="btn btn-primary" to="/problems">Browse problems</Link>}>
          Use the bookmark icon on any problem to keep it here.
        </EmptyState></div>
      ) : (
        <div className="table-wrap">{data.items.map((p) => <ProblemRow key={p.id} problem={p} onChange={onChange} />)}</div>
      )}
    </>
  );
}
