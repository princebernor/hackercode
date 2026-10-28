import { useState } from "react";
import { useAuth } from "../../api/auth.jsx";
import { get, put } from "../../api/client.js";
import { useToast } from "../../components/Toasts.jsx";
import { Alert, Button, ConfirmDialog, ErrorState, LangBadge, Skeleton, Switch } from "../../components/ui.jsx";
import useAsync from "../../lib/useAsync.js";
import { HealthList } from "./AdminOverview.jsx";

const GROUPS = [
  { title: "Feature flags", keys: ["feature.signup", "feature.execution", "feature.aiReview", "notifications.welcome"] },
  { title: "Maintenance", keys: ["maintenance.enabled", "maintenance.message"] },
  { title: "Rate limits", keys: ["rateLimit.authPer10Min", "rateLimit.runsPerMinute", "rateLimit.reviewsPerHour"] },
];
const SENSITIVE = new Set(["maintenance.enabled", "feature.signup", "feature.execution"]);

export default function AdminConfig() {
  const { isAdmin, refresh } = useAuth();
  const toast = useToast();
  const { data, error, loading, reload, setData } = useAsync(() => get("/api/admin/config"), []);
  const [edits, setEdits] = useState({});
  const [confirm, setConfirm] = useState(null);

  if (error) return <ErrorState error={error} onRetry={reload} />;
  if (loading) return <Skeleton h={420} r={20} />;

  const commit = async (key, value) => {
    try {
      setData(await put(`/api/admin/config/${key}`, { value: String(value) }));
      setEdits((e) => { const n = { ...e }; delete n[key]; return n; });
      toast.success("Configuration saved and recorded in the audit log.");
      if (key.startsWith("maintenance") || key === "feature.signup") refresh();
    } catch (e) { toast.error(e); }
    setConfirm(null);
  };
  const request = (key, value) => (SENSITIVE.has(key) ? setConfirm({ key, value }) : commit(key, value));

  const languages = data.languages;
  const setLanguage = (id, on) => {
    const next = languages.filter((l) => (l.id === id ? on : l.enabled)).map((l) => l.id);
    if (!next.length) { toast.error("At least one language must stay enabled."); return; }
    commit("languages.enabled", next.join(","));
  };

  return (
    <div className="dash-grid">
      {!isAdmin && <div className="span-12"><Alert kind="info">Moderators can view platform configuration. Only administrators can change it.</Alert></div>}
      {GROUPS.map((g) => (
        <section className="card span-6" key={g.title}>
          <div className="card-header"><h2>{g.title}</h2></div>
          {g.keys.map((k) => {
            const s = data.settings[k];
            const isBool = s.default === "true" || s.default === "false";
            const value = edits[k] ?? s.value;
            return (
              <div className="setting-row" key={k}>
                <div><span className="label">{s.description}</span><p className="field-hint mono">{k}</p></div>
                {isBool ? (
                  <Switch checked={s.value === "true"} disabled={!isAdmin} onChange={(v) => request(k, v)} label={s.value === "true" ? "On" : "Off"} />
                ) : (
                  <div className="row">
                    <input className="input input-sm" aria-label={s.description} value={value} disabled={!isAdmin} inputMode={/^\d+$/.test(s.default) ? "numeric" : undefined}
                      onChange={(e) => setEdits({ ...edits, [k]: e.target.value })} />
                    {isAdmin && edits[k] !== undefined && <Button size="sm" variant="primary" onClick={() => request(k, edits[k])}>Save</Button>}
                  </div>
                )}
              </div>
            );
          })}
        </section>
      ))}
      <section className="card span-6">
        <div className="card-header"><h2>Supported languages</h2></div>
        {languages.map((l) => (
          <div className="setting-row" key={l.id}>
            <div className="row"><LangBadge id={l.id} /><div><span className="label">{l.label}</span><p className="field-hint mono">Docker image: {l.dockerImage}</p></div></div>
            <Switch checked={l.enabled} disabled={!isAdmin} onChange={(v) => setLanguage(l.id, v)} label={l.enabled ? "Enabled" : "Disabled"} />
          </div>
        ))}
      </section>
      <section className="card span-6">
        <div className="card-header"><h2>Integrations</h2></div>
        <HealthList health={data.health} />
        {!data.health.execution.configured && <p className="xs muted" style={{ marginTop: 10 }}>{data.health.execution.setupHint}</p>}
        {!data.health.aiReview.configured && <p className="xs muted" style={{ marginTop: 6 }}>{data.health.aiReview.setupHint}</p>}
        <p className="xs muted" style={{ marginTop: 10 }}>Provider credentials are configured with server environment variables and are never shown in the browser.</p>
      </section>
      {confirm && (
        <ConfirmDialog danger title="Change a platform-wide setting?" confirmLabel="Apply"
          message={`Set ${confirm.key} to ${String(confirm.value)}. This affects every user immediately.`}
          onConfirm={() => commit(confirm.key, confirm.value)} onClose={() => setConfirm(null)} />
      )}
    </div>
  );
}
