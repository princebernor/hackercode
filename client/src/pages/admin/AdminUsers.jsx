import { useState } from "react";
import { useAuth } from "../../api/auth.jsx";
import { get, patch, qs } from "../../api/client.js";
import { useToast } from "../../components/Toasts.jsx";
import { Button, ConfirmDialog, Dialog, ErrorState, Field, SkeletonRows, timeAgo } from "../../components/ui.jsx";
import useAsync from "../../lib/useAsync.js";

function UserDetail({ id, onClose }) {
  const { data, loading } = useAsync(() => get(`/api/admin/users/${id}`), [id]);
  return (
    <Dialog title="Account details" onClose={onClose} wide actions={<Button onClick={onClose}>Close</Button>}>
      {loading ? <SkeletonRows rows={4} /> : (
        <div className="stack">
          <dl className="kv">
            <dt>Email</dt><dd>{data.user.email}</dd>
            <dt>Name</dt><dd>{data.user.displayName}</dd>
            <dt>Role / status</dt><dd>{data.user.role} / {data.user.status}</dd>
            <dt>Sign-in methods</dt><dd>{[data.user.hasPassword && "password", data.user.googleLinked && "Google"].filter(Boolean).join(", ")}</dd>
            <dt>Last sign-in</dt><dd>{data.user.lastLoginAt ? new Date(data.user.lastLoginAt).toLocaleString() : "never"}</dd>
            <dt>Solved / attempted</dt><dd>{data.solved} / {data.attempted}</dd>
          </dl>
          <div>
            <h3 className="small">Active sessions ({data.activeSessions.length})</h3>
            {data.activeSessions.map((s) => <div key={s.id} className="xs muted truncate">{s.userAgent} · {timeAgo(s.lastSeenAt)}</div>)}
          </div>
          <div>
            <h3 className="small">Recent activity (metadata only)</h3>
            {data.recentActivity.length === 0 ? <p className="xs muted">None.</p> : data.recentActivity.map((a, i) => <div key={i} className="xs muted">{a.type} · {timeAgo(a.at)}</div>)}
          </div>
          <p className="xs subtle">Passwords, tokens, notes and source code are never shown here. Viewing this record was written to the audit log.</p>
        </div>
      )}
    </Dialog>
  );
}

export default function AdminUsers() {
  const { isAdmin, user: me } = useAuth();
  const toast = useToast();
  const [filters, setFilters] = useState({ q: "", role: "", status: "", page: 0 });
  const [query, setQuery] = useState("");
  const { data, error, loading, reload } = useAsync(() => get(`/api/admin/users${qs({ ...filters, size: 25 })}`), [filters.q, filters.role, filters.status, filters.page]);
  const [detail, setDetail] = useState(null);
  const [pending, setPending] = useState(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  const apply = async () => {
    setBusy(true);
    try {
      if (pending.kind === "status") await patch(`/api/admin/users/${pending.user.id}/status`, { status: pending.value, reason });
      else await patch(`/api/admin/users/${pending.user.id}/role`, { role: pending.value });
      toast.success("Account updated.");
      setPending(null);
      setReason("");
      reload();
    } catch (e) { toast.error(e); } finally { setBusy(false); }
  };

  return (
    <div className="stack">
      <form className="filters" style={{ gridTemplateColumns: "2fr 1fr 1fr auto" }} onSubmit={(e) => { e.preventDefault(); setFilters({ ...filters, q: query, page: 0 }); }}>
        <div className="filter-search"><label htmlFor="uq" className="sr-only">Search users</label><input id="uq" className="input" placeholder="Search email or name" value={query} onChange={(e) => setQuery(e.target.value)} /></div>
        <select className="select" aria-label="Role" value={filters.role} onChange={(e) => setFilters({ ...filters, role: e.target.value, page: 0 })}>
          <option value="">All roles</option><option>USER</option><option>MODERATOR</option><option>ADMIN</option>
        </select>
        <select className="select" aria-label="Status" value={filters.status} onChange={(e) => setFilters({ ...filters, status: e.target.value, page: 0 })}>
          <option value="">Any status</option><option>ACTIVE</option><option>SUSPENDED</option>
        </select>
        <Button type="submit">Search</Button>
      </form>
      {!isAdmin && <p className="small muted">Moderators can view accounts. Only administrators can change roles or suspend accounts.</p>}
      {error ? <ErrorState error={error} onRetry={reload} /> : loading && !data ? <SkeletonRows rows={6} /> : (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th scope="col">User</th><th scope="col">Role</th><th scope="col">Status</th><th scope="col">Verified</th><th scope="col">Last sign-in</th><th scope="col"><span className="sr-only">Actions</span></th></tr></thead>
            <tbody>
              {data.items.map((u) => (
                <tr key={u.id}>
                  <td><div style={{ fontWeight: 500 }}>{u.displayName}</div><div className="xs muted">{u.email}</div></td>
                  <td>{isAdmin && u.id !== me.id ? (
                    <select className="select input-sm" aria-label={`Role for ${u.email}`} value={u.role} style={{ minHeight: 32, width: "auto" }}
                      onChange={(e) => setPending({ kind: "role", user: u, value: e.target.value })}>
                      <option>USER</option><option>MODERATOR</option><option>ADMIN</option>
                    </select>) : <span className="badge">{u.role}</span>}</td>
                  <td><span className={`badge ${u.status === "ACTIVE" ? "badge-primary" : "badge-danger"}`}>{u.status}</span></td>
                  <td>{u.emailVerified ? "Yes" : "No"}</td>
                  <td className="small muted nowrap">{u.lastLoginAt ? timeAgo(u.lastLoginAt) : "never"}</td>
                  <td className="nowrap" style={{ textAlign: "right" }}>
                    <Button size="sm" variant="ghost" onClick={() => setDetail(u.id)}>View</Button>
                    {isAdmin && u.id !== me.id && (u.status === "ACTIVE"
                      ? <Button size="sm" variant="ghost" onClick={() => setPending({ kind: "status", user: u, value: "SUSPENDED" })}>Suspend</Button>
                      : <Button size="sm" variant="ghost" onClick={() => setPending({ kind: "status", user: u, value: "ACTIVE" })}>Reactivate</Button>)}
                  </td>
                </tr>
              ))}
              {data.items.length === 0 && <tr><td colSpan={6} className="muted small">No users match.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
      {data && data.totalPages > 1 && (
        <nav className="pagination" aria-label="Pagination">
          <span className="small muted">{data.total} users · page {filters.page + 1} of {data.totalPages}</span>
          <div className="row"><Button size="sm" disabled={filters.page === 0} onClick={() => setFilters({ ...filters, page: filters.page - 1 })}>Previous</Button>
            <Button size="sm" disabled={filters.page + 1 >= data.totalPages} onClick={() => setFilters({ ...filters, page: filters.page + 1 })}>Next</Button></div>
        </nav>
      )}
      {detail && <UserDetail id={detail} onClose={() => setDetail(null)} />}
      {pending && (
        <ConfirmDialog busy={busy} danger={pending.value === "SUSPENDED" || pending.value === "ADMIN"}
          title={pending.kind === "role" ? `Change role to ${pending.value}?` : pending.value === "SUSPENDED" ? "Suspend this account?" : "Reactivate this account?"}
          message={pending.kind === "role"
            ? `${pending.user.email} will become ${pending.value}. ${pending.value === "ADMIN" ? "Administrators have full control of the platform." : ""}`
            : pending.value === "SUSPENDED" ? `${pending.user.email} will be signed out everywhere and blocked from signing in.` : `${pending.user.email} will be able to sign in again.`}
          confirmLabel="Confirm" onConfirm={apply} onClose={() => { setPending(null); setReason(""); }}>
          {pending.kind === "status" && <Field label="Reason (recorded in the audit log)">{(p) => <input className="input" maxLength={300} value={reason} onChange={(e) => setReason(e.target.value)} {...p} />}</Field>}
        </ConfirmDialog>
      )}
    </div>
  );
}
