import { Fragment, useEffect, useMemo, useState } from "react";
import { Link, NavLink, useNavigate, useParams } from "react-router-dom";
import { applyPreferences, useAuth } from "../../api/auth.jsx";
import { api, del, get, patch, post } from "../../api/client.js";
import Icon from "../../components/Icon.jsx";
import { useToast } from "../../components/Toasts.jsx";
import {
  Alert, Avatar, Button, ConfirmDialog, Field, PageHeader, PasswordInput, Segmented, Skeleton, Switch, timeAgo, useDocumentTitle,
} from "../../components/ui.jsx";
import { STRENGTH_LABEL, passwordProblems, passwordStrength } from "../../lib/password.js";
import useAsync from "../../lib/useAsync.js";
import { DEFAULTS, PREF_SECTIONS, SECTIONS, SETTINGS, SHORTCUTS, getPath, setPath } from "./schema.js";

function Control({ s, value, onChange, topics }) {
  const id = `set-${s.path}`;
  switch (s.type) {
    case "switch":
      return <Switch id={id} checked={value} onChange={onChange} label={value ? "On" : "Off"} />;
    case "segmented":
      return <Segmented label={s.label} value={value} onChange={onChange} options={s.options.map(([v, l]) => ({ value: v, label: l }))} />;
    case "select":
      return (
        <select id={id} className="select" value={value} onChange={(e) => onChange(e.target.value)}>
          {s.options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
      );
    case "topic":
      return (
        <select id={id} className="select" value={value || ""} onChange={(e) => onChange(e.target.value || null)}>
          <option value="">Follow the sheet in order</option>
          {topics?.map((t) => <option key={t.slug} value={t.slug}>{t.label}</option>)}
        </select>
      );
    case "number":
      return <input id={id} className="input" type="number" min={s.min} max={s.max} value={value}
        onChange={(e) => onChange(e.target.value === "" ? s.min : Math.max(s.min, Math.min(s.max, Number(e.target.value))))} />;
    case "time":
      return <input id={id} className="input" type="time" value={value} onChange={(e) => onChange(e.target.value)} />;
    case "multi":
      return (
        <div className="row-wrap" role="group" aria-label={s.label}>
          {s.options.map(([v, l]) => {
            const on = value.includes(v);
            return (
              <label key={v} className="check badge badge-outline" style={{ padding: "6px 10px" }}>
                <input type="checkbox" checked={on} disabled={on && value.length === 1}
                  onChange={(e) => onChange(e.target.checked ? [...value, v] : value.filter((x) => x !== v))} />{l}
              </label>
            );
          })}
        </div>
      );
    default:
      return null;
  }
}

function SettingRows({ rows, draft, setDraft, topics }) {
  return rows.map((s) => (
    <div className="setting-row" key={s.path}>
      <div>
        <label className="label" htmlFor={`set-${s.path}`}>{s.label}</label>
        {s.hint && <p className="field-hint">{s.hint}</p>}
      </div>
      <Control s={s} value={getPath(draft, s.path)} topics={topics} onChange={(v) => setDraft((d) => setPath(d, s.path, v))} />
    </div>
  ));
}

function AccountPanel() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState({ displayName: user.displayName, avatarUrl: user.avatarUrl || "" });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const save = async (e) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    try {
      setUser(await patch("/api/account/profile", form));
      toast.success("Profile updated.");
    } catch (err) { setErrors(err.fields || {}); toast.error(err); } finally { setBusy(false); }
  };
  return (
    <form className="stack" onSubmit={save} noValidate>
      <div className="row"><Avatar user={{ ...user, ...form }} size="lg" /><div><strong>{user.displayName}</strong><div className="small muted">{user.email}</div></div></div>
      <Field label="Display name" error={errors.displayName}>{(p) => <input className="input" maxLength={80} value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} {...p} />}</Field>
      <Field label="Profile photo URL" error={errors.avatarUrl} hint="An https:// image link. Leave empty to use your initials.">
        {(p) => <input className="input" type="url" placeholder="https://…" value={form.avatarUrl} onChange={(e) => setForm({ ...form, avatarUrl: e.target.value })} {...p} />}
      </Field>
      <dl className="kv">
        <dt>Email</dt><dd>{user.email} {user.emailVerified ? <span className="badge badge-primary">Verified</span> : <span className="badge badge-warning">Not verified</span>}</dd>
        <dt>Google account</dt><dd>{user.googleLinked ? <span className="badge badge-primary">Connected</span> : <span className="badge">Not connected</span>}</dd>
        <dt>Role</dt><dd>{user.role}</dd>
        <dt>Member since</dt><dd>{new Date(user.createdAt).toLocaleDateString()}</dd>
      </dl>
      <p className="xs muted">Changing your email address is not supported yet; contact an administrator if you need it changed.</p>
      <div><Button type="submit" variant="primary" loading={busy}>Save profile</Button></div>
    </form>
  );
}

function SecurityPanel() {
  const { user, refresh } = useAuth();
  const toast = useToast();
  const sessions = useAsync(() => get("/api/account/sessions"), []);
  const [pw, setPw] = useState({ current: "", next: "", confirm: "" });
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [confirmOthers, setConfirmOthers] = useState(false);
  const problems = passwordProblems(pw.next, user.email);
  const change = async (e) => {
    e.preventDefault();
    if (problems.length) { setError(`New password needs: ${problems.join(", ").toLowerCase()}.`); return; }
    if (pw.next !== pw.confirm) { setError("New passwords do not match."); return; }
    setBusy(true);
    setError("");
    try {
      const r = await post("/api/account/password", { currentPassword: pw.current, newPassword: pw.next });
      toast.success(r.message);
      setPw({ current: "", next: "", confirm: "" });
      sessions.reload();
      refresh();
    } catch (err) { setError(err.message); } finally { setBusy(false); }
  };
  const revoke = async (id) => {
    try { await del(`/api/account/sessions/${id}`); sessions.reload(); toast.success("Session signed out."); } catch (e) { toast.error(e); }
  };
  const revokeOthers = async () => {
    try { const r = await post("/api/account/sessions/revoke-others"); toast.success(`${r.revoked} other session(s) signed out.`); sessions.reload(); } catch (e) { toast.error(e); }
    setConfirmOthers(false);
  };
  return (
    <div className="stack-lg">
      <form className="stack" onSubmit={change} noValidate>
        <h3>{user.hasPassword ? "Change password" : "Set a password"}</h3>
        {!user.hasPassword && <p className="small muted">You sign in with Google. Setting a password also lets you sign in with email.</p>}
        {error && <Alert kind="danger">{error}</Alert>}
        {user.hasPassword && <Field label="Current password">{(p) => <PasswordInput show={show} autoComplete="current-password" value={pw.current} onChange={(e) => setPw({ ...pw, current: e.target.value })} {...p} />}</Field>}
        <Field label="New password">{(p) => <PasswordInput show={show} onToggle={() => setShow(!show)} autoComplete="new-password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} {...p} />}</Field>
        {pw.next && <div className="stack-sm"><div className="strength" data-level={passwordStrength(pw.next)}><span /><span /><span /><span /></div><span className="xs muted">{STRENGTH_LABEL[passwordStrength(pw.next)]}</span></div>}
        <Field label="Confirm new password">{(p) => <PasswordInput show={show} autoComplete="new-password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} {...p} />}</Field>
        <p className="xs muted">Changing your password signs out all other devices.</p>
        <div><Button type="submit" variant="primary" loading={busy}>Update password</Button></div>
      </form>
      <hr style={{ margin: 0 }} />
      <div className="stack">
        <div className="row between"><h3>Signed-in devices</h3><Button size="sm" onClick={() => setConfirmOthers(true)}>Sign out other devices</Button></div>
        {sessions.loading ? <Skeleton h={100} /> : sessions.data?.map((s) => (
          <div key={s.id} className="list-item">
            <Icon name="lock" />
            <div className="grow">
              <div className="small truncate" title={s.userAgent}>{describeAgent(s.userAgent)} {s.current && <span className="badge badge-primary">This device</span>} {s.rememberMe && <span className="badge">Remembered</span>}</div>
              <div className="xs subtle">{s.ipHint || "Unknown network"} · signed in {timeAgo(s.createdAt)} · active {timeAgo(s.lastSeenAt)}</div>
            </div>
            {!s.current && <Button size="sm" variant="ghost" onClick={() => revoke(s.id)}>Sign out</Button>}
          </div>
        ))}
      </div>
      {confirmOthers && <ConfirmDialog title="Sign out other devices?" message="Every other browser signed in to your account will be signed out immediately." confirmLabel="Sign out others" onConfirm={revokeOthers} onClose={() => setConfirmOthers(false)} />}
    </div>
  );
}

function describeAgent(ua = "") {
  const browser = /Edg\//.test(ua) ? "Edge" : /Chrome\//.test(ua) ? "Chrome" : /Firefox\//.test(ua) ? "Firefox" : /Safari\//.test(ua) ? "Safari" : "Browser";
  const os = /Windows/.test(ua) ? "Windows" : /Android/.test(ua) ? "Android" : /iPhone|iPad/.test(ua) ? "iOS" : /Mac OS/.test(ua) ? "macOS" : /Linux/.test(ua) ? "Linux" : "";
  return os ? `${browser} on ${os}` : browser;
}

function DataPanel() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [confirm, setConfirm] = useState(false);
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const exportData = async () => {
    try {
      const res = await api("/api/account/export", { raw: true });
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "hackercode-export.json";
      a.click();
      URL.revokeObjectURL(url);
      toast.success("Your data export has downloaded.");
    } catch (e) { toast.error(e); }
  };
  const deleteAccount = async () => {
    setBusy(true);
    try {
      await post("/api/account/delete", { password, confirm: "DELETE" });
      setUser(null);
      navigate("/signin", { replace: true });
    } catch (e) { toast.error(e); setBusy(false); }
  };
  return (
    <div className="stack">
      <div className="card card-tight row between" style={{ flexWrap: "wrap" }}>
        <div><strong>Export your data</strong><p className="small muted">Account, preferences, progress, notes, drafts, submissions and reviews as JSON.</p></div>
        <Button onClick={exportData}><Icon name="download" size={16} /> Download export</Button>
      </div>
      <div className="card card-tight danger-zone row between" style={{ flexWrap: "wrap" }}>
        <div><strong style={{ color: "var(--danger)" }}>Delete account</strong><p className="small muted">Permanently deletes your account and all associated data. This cannot be undone.</p></div>
        <Button variant="danger" onClick={() => setConfirm(true)}><Icon name="trash" size={16} /> Delete account</Button>
      </div>
      {confirm && (
        <ConfirmDialog danger title="Delete your account permanently?" confirmLabel="Delete forever" requirePhrase="DELETE" busy={busy}
          message="All progress, notes, saved code, submissions and reviews will be erased immediately." onClose={() => setConfirm(false)} onConfirm={deleteAccount}>
          {user.hasPassword && <Field label="Your password">{(p) => <input className="input" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} {...p} />}</Field>}
        </ConfirmDialog>
      )}
    </div>
  );
}

export default function Settings() {
  const { section = "account" } = useParams();
  const meta = SECTIONS.find((s) => s.id === section) || SECTIONS[0];
  useDocumentTitle(`Settings · ${meta.label}`);
  const { prefs, savePrefs } = useAuth();
  const toast = useToast();
  const [draft, setDraft] = useState(prefs);
  const [query, setQuery] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmReset, setConfirmReset] = useState(null);
  const topics = useAsync(() => get("/api/topics"), []);

  useEffect(() => { if (prefs && !draft) setDraft(prefs); }, [prefs, draft]);
  // Live preview of appearance/accessibility changes before saving.
  useEffect(() => { if (draft) applyPreferences(draft); }, [draft]);
  useEffect(() => () => applyPreferences(prefs), [prefs]);

  const dirtySections = useMemo(() => {
    if (!draft || !prefs) return new Set();
    return new Set(SETTINGS.filter((s) => JSON.stringify(getPath(draft, s.path)) !== JSON.stringify(getPath(prefs, s.path))).map((s) => s.section));
  }, [draft, prefs]);
  const dirty = dirtySections.size > 0;

  useEffect(() => {
    if (!dirty) return undefined;
    const warn = (e) => { e.preventDefault(); e.returnValue = ""; };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);

  const save = async () => {
    setBusy(true);
    try {
      const saved = await savePrefs(draft);
      setDraft(saved);
      toast.success("Settings saved.");
    } catch (e) { toast.error(e); } finally { setBusy(false); }
  };

  const resetSection = () => {
    const rows = SETTINGS.filter((s) => s.section === confirmReset);
    setDraft((d) => rows.reduce((acc, s) => setPath(acc, s.path, getPath(DEFAULTS, s.path)), d));
    setConfirmReset(null);
    toast.info("Defaults restored. Save to apply.");
  };

  if (!draft) return <Skeleton h={400} r={20} />;

  const q = query.trim().toLowerCase();
  const results = q ? SETTINGS.filter((s) => (s.label + " " + (s.hint || "") + " " + s.section).toLowerCase().includes(q)) : null;
  const rows = SETTINGS.filter((s) => s.section === meta.id);

  return (
    <>
      <PageHeader title="Settings" subtitle="Preferences are saved to your account and restored on every device." />
      <div className="settings-layout">
        <nav className="settings-nav" aria-label="Settings sections">
          <label htmlFor="settings-search" className="sr-only">Search settings</label>
          <input id="settings-search" className="input input-sm" placeholder="Search settings" value={query} onChange={(e) => setQuery(e.target.value)} style={{ marginBottom: 8 }} />
          {SECTIONS.map((s) => (
            <NavLink key={s.id} to={`/settings/${s.id}`} className={({ isActive }) => (isActive && !q ? "active" : "")} onClick={() => setQuery("")}>
              {s.label}{dirtySections.has(s.id) && <span className="status-dot attempted" title="Unsaved changes" aria-label="Unsaved changes" />}
            </NavLink>
          ))}
        </nav>
        <div className="stack">
          <section className="card" aria-labelledby="settings-title">
            {results ? (
              <>
                <h2 id="settings-title" style={{ fontSize: "var(--text-lg)" }}>Results for “{query}”</h2>
                {results.length === 0 ? <p className="muted small" style={{ marginTop: 12 }}>No settings match. Try “theme”, “font” or “email”.</p>
                  : <SettingRows rows={results} draft={draft} setDraft={setDraft} topics={topics.data} />}
              </>
            ) : (
              <>
                <div className="card-header">
                  <h2 id="settings-title" style={{ fontSize: "var(--text-lg)" }}>{meta.label}</h2>
                  {PREF_SECTIONS.has(meta.id) && <Button size="sm" variant="ghost" onClick={() => setConfirmReset(meta.id)}><Icon name="reset" size={14} /> Reset to defaults</Button>}
                </div>
                {meta.id === "account" && <AccountPanel />}
                {meta.id === "security" && <SecurityPanel />}
                {meta.id === "shortcuts" && (
                  <dl className="kv" style={{ gridTemplateColumns: "minmax(180px, auto) 1fr" }}>
                    {SHORTCUTS.map(([k, v]) => <Fragment key={k}><dt><kbd>{k}</kbd></dt><dd>{v}</dd></Fragment>)}
                  </dl>
                )}
                {meta.id === "learning" && <Alert kind="info">Reminder times use UTC. Reminder emails are sent only when the server has SMTP configured.</Alert>}
                <SettingRows rows={rows} draft={draft} setDraft={setDraft} topics={topics.data} />
                {meta.id === "privacy" && <div style={{ marginTop: 20 }}><DataPanel /></div>}
                {meta.id === "privacy" && <p className="xs muted" style={{ marginTop: 12 }}>See the <Link to="/privacy">privacy policy</Link> for retention periods.</p>}
              </>
            )}
          </section>
          {dirty && (
            <div className="save-bar" role="region" aria-label="Unsaved changes">
              <Icon name="info" /> <span className="small grow">You have unsaved changes in {[...dirtySections].map((s) => SECTIONS.find((x) => x.id === s).label).join(", ")}.</span>
              <Button size="sm" variant="ghost" onClick={() => setDraft(prefs)}>Discard</Button>
              <Button size="sm" variant="primary" loading={busy} onClick={save}>Save changes</Button>
            </div>
          )}
        </div>
      </div>
      {confirmReset && (
        <ConfirmDialog title={`Reset ${SECTIONS.find((s) => s.id === confirmReset).label} to defaults?`} confirmLabel="Reset section"
          message="Values in this section return to their defaults. Nothing is saved until you press Save changes." onConfirm={resetSection} onClose={() => setConfirmReset(null)} />
      )}
    </>
  );
}
