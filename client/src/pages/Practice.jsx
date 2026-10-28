import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { get, qs } from "../api/client.js";
import { ProblemRow } from "../components/ProblemBits.jsx";
import { Button, EmptyState, ErrorState, PageHeader, SkeletonRows, useDocumentTitle } from "../components/ui.jsx";
import useAsync from "../lib/useAsync.js";

const PAGE_SIZE = 50;

export default function Practice() {
  useDocumentTitle("Practice problems");
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => ({
    q: params.get("q") || "",
    topic: params.get("topic") || "",
    subsection: params.get("subsection") || "",
    status: params.get("status") || "",
    sort: params.get("sort") || "number",
    page: Number(params.get("page") || 0),
  }), [params]);
  const [q, setQ] = useState(filters.q);
  useEffect(() => setQ(filters.q), [filters.q]);

  const topics = useAsync(() => get("/api/topics"), []);
  const list = useAsync(
    () => get(`/api/problems${qs({ ...filters, size: PAGE_SIZE })}`),
    [filters.q, filters.topic, filters.subsection, filters.status, filters.sort, filters.page],
  );

  const update = (patch) => {
    const next = { ...filters, ...patch };
    if (!("page" in patch)) next.page = 0;
    const sp = new URLSearchParams();
    Object.entries(next).forEach(([k, v]) => { if (v && !(k === "sort" && v === "number") && !(k === "page" && v === 0)) sp.set(k, v); });
    setParams(sp);
  };

  // Debounced search-as-you-type.
  useEffect(() => {
    if (q === filters.q) return undefined;
    const t = setTimeout(() => update({ q }), 300);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);

  const currentTopic = topics.data?.find((t) => t.slug === filters.topic);
  const total = list.data?.total ?? 0;
  const hasFilters = filters.q || filters.topic || filters.status || filters.subsection;

  // Group consecutive rows by subsection when browsing in sheet order (keeps the PDF's structure visible).
  const groups = useMemo(() => {
    const items = list.data?.items || [];
    if (filters.sort !== "number") return [{ key: "all", items }];
    const out = [];
    items.forEach((p) => {
      const key = `${p.topic.slug}|${p.subsection}`;
      if (!out.length || out[out.length - 1].key !== key) out.push({ key, topic: p.topic, subsection: p.subsectionLabel, items: [] });
      out[out.length - 1].items.push(p);
    });
    return out;
  }, [list.data, filters.sort]);

  const updateRow = (p) => list.setData((d) => ({ ...d, items: d.items.map((x) => (x.id === p.id ? p : x)) }));

  return (
    <>
      <PageHeader
        title="Practice problems"
        subtitle="All 442 problems from the placement sheet, in the sheet's original order and grouping."
        crumbs={currentTopic ? [{ label: "Practice", to: "/problems" }, { label: currentTopic.label }] : undefined}
      />
      <div className="filters" role="search" aria-label="Filter problems">
        <div className="filter-search">
          <label htmlFor="pq" className="sr-only">Search by title, number, topic or keyword</label>
          <input id="pq" className="input" placeholder="Search title, #number, topic or keyword" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div>
          <label htmlFor="ptopic" className="sr-only">Topic</label>
          <select id="ptopic" className="select" value={filters.topic} onChange={(e) => update({ topic: e.target.value, subsection: "" })}>
            <option value="">All topics</option>
            {topics.data?.map((t) => <option key={t.slug} value={t.slug}>{t.label} ({t.total})</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="psub" className="sr-only">Pattern</label>
          <select id="psub" className="select" value={filters.subsection} disabled={!currentTopic} onChange={(e) => update({ subsection: e.target.value })}>
            <option value="">{currentTopic ? "All patterns" : "Pick a topic first"}</option>
            {currentTopic?.subsections.map((s) => <option key={s.name} value={s.name}>{s.label} ({s.total})</option>)}
          </select>
        </div>
        <div>
          <label htmlFor="pstatus" className="sr-only">Status</label>
          <select id="pstatus" className="select" value={filters.status} onChange={(e) => update({ status: e.target.value })}>
            <option value="">Any status</option>
            <option value="NOT_STARTED">Not started</option>
            <option value="ATTEMPTED">Attempted</option>
            <option value="SOLVED">Solved</option>
          </select>
        </div>
        <div>
          <label htmlFor="psort" className="sr-only">Sort</label>
          <select id="psort" className="select" value={filters.sort} onChange={(e) => update({ sort: e.target.value })}>
            <option value="number">Sheet order</option>
            <option value="title">Title A–Z</option>
            <option value="recent">Recently viewed</option>
          </select>
        </div>
      </div>

      <div className="row between small muted" style={{ marginBottom: 10 }} aria-live="polite">
        <span>{list.loading ? "Searching…" : `${total} problem${total === 1 ? "" : "s"}`}</span>
        {hasFilters && <Button size="sm" variant="ghost" onClick={() => setParams(new URLSearchParams())}>Clear filters</Button>}
      </div>

      {list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : list.loading && !list.data ? <SkeletonRows rows={10} h={58} /> : total === 0 ? (
        <div className="card"><EmptyState icon="search" title="No matching problems" action={<Button onClick={() => setParams(new URLSearchParams())}>Clear filters</Button>}>
          Try a different keyword, a problem number like <kbd>#38</kbd>, or a topic name.
        </EmptyState></div>
      ) : (
        <div className="table-wrap" style={{ opacity: list.loading ? 0.6 : 1 }}>
          {groups.map((g) => (
            <div key={g.key}>
              {g.topic && <div className="group-head"><span>{g.topic.label} · {g.subsection}</span></div>}
              {g.items.map((p) => <ProblemRow key={p.id} problem={p} onChange={updateRow} showTopic={!g.topic} />)}
            </div>
          ))}
        </div>
      )}

      {list.data && list.data.totalPages > 1 && (
        <nav className="pagination" aria-label="Pagination">
          <span className="small muted">Page {filters.page + 1} of {list.data.totalPages}</span>
          <div className="row">
            <Button size="sm" disabled={filters.page === 0} onClick={() => update({ page: filters.page - 1 })}>Previous</Button>
            <Button size="sm" disabled={filters.page + 1 >= list.data.totalPages} onClick={() => update({ page: filters.page + 1 })}>Next</Button>
          </div>
        </nav>
      )}
    </>
  );
}
