import { useEffect, useId, useRef, useState } from "react";
import { Link } from "react-router-dom";
import Icon from "./Icon.jsx";

export function Spinner({ label = "Loading" }) {
  return <span className="spinner" role="status" aria-label={label} />;
}

export function Button({ variant, size, block, loading, children, className = "", disabled, ...rest }) {
  const cls = ["btn", variant && `btn-${variant}`, size && `btn-${size}`, block && "btn-block", className].filter(Boolean).join(" ");
  return (
    <button className={cls} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {loading && <span className="spinner" aria-hidden="true" />}
      {children}
    </button>
  );
}

export function Field({ label, error, hint, children, id: idProp, required }) {
  const auto = useId();
  const id = idProp || auto;
  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;
  const describedBy = [error && errorId, hint && hintId].filter(Boolean).join(" ") || undefined;
  return (
    <div className="field">
      {label && (
        <label htmlFor={id}>
          {label}
          {required && <span aria-hidden="true" className="subtle"> *</span>}
        </label>
      )}
      {children({ id, "aria-invalid": error ? true : undefined, "aria-describedby": describedBy })}
      {hint && !error && <span className="field-hint" id={hintId}>{hint}</span>}
      {error && <span className="field-error" id={errorId} role="alert">{error}</span>}
    </div>
  );
}

export function PasswordInput({ show, onToggle, ...props }) {
  return (
    <div className="input-wrap">
      <input className="input" type={show ? "text" : "password"} {...props} />
      {onToggle && (
        <button type="button" className="btn btn-ghost btn-icon input-action" onClick={onToggle}
          aria-label={show ? "Hide password" : "Show password"} aria-pressed={show}>
          <Icon name={show ? "eyeOff" : "eye"} />
        </button>
      )}
    </div>
  );
}

export function Switch({ checked, onChange, label, id, disabled }) {
  return (
    <label className="switch">
      <input type="checkbox" id={id} checked={!!checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} role="switch" aria-checked={!!checked} />
      <span className="track" aria-hidden="true" />
      {label && <span className="small">{label}</span>}
    </label>
  );
}

export function Segmented({ value, options, onChange, label }) {
  return (
    <div className="segmented" role="group" aria-label={label}>
      {options.map((o) => (
        <button type="button" key={o.value} aria-pressed={value === o.value} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function Skeleton({ h = 16, w = "100%", r, style }) {
  return <div className="skeleton" style={{ height: h, width: w, borderRadius: r, ...style }} aria-hidden="true" />;
}

export function SkeletonRows({ rows = 5, h = 44 }) {
  return (
    <div className="stack-sm" role="status" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => <Skeleton key={i} h={h} />)}
    </div>
  );
}

export function EmptyState({ icon = "info", title, children, action }) {
  return (
    <div className="empty">
      <div className="empty-icon"><Icon name={icon} size={24} /></div>
      <h3>{title}</h3>
      {children && <p className="small" style={{ maxWidth: 440 }}>{children}</p>}
      {action}
    </div>
  );
}

export function Alert({ kind = "info", title, children, icon }) {
  const iconName = icon || { info: "info", warning: "alert", danger: "alert", success: "check" }[kind];
  return (
    <div className={`alert alert-${kind}`} role={kind === "danger" ? "alert" : "status"}>
      <Icon name={iconName} />
      <div className="alert-body">
        {title && <strong>{title} </strong>}
        {children}
      </div>
    </div>
  );
}

export function ErrorState({ error, onRetry }) {
  return (
    <EmptyState icon="alert" title="Something went wrong" action={onRetry && <Button onClick={onRetry}>Try again</Button>}>
      {error?.message || "We could not load this section."}
    </EmptyState>
  );
}

export function Progress({ value, max = 100, label, small }) {
  const pct = max > 0 ? Math.min(100, Math.round((value / max) * 100)) : 0;
  return (
    <div className={`progress${small ? " progress-sm" : ""}`} role="progressbar" aria-valuemin={0} aria-valuemax={max}
      aria-valuenow={value} aria-label={label}>
      <span style={{ width: `${pct}%` }} />
    </div>
  );
}

export function Avatar({ user, size }) {
  const initials = (user?.displayName || user?.email || "?").split(/\s+/).map((s) => s[0]).join("").slice(0, 2).toUpperCase();
  return (
    <span className={`avatar${size === "lg" ? " avatar-lg" : ""}`} aria-hidden="true">
      {user?.avatarUrl ? <img src={user.avatarUrl} alt="" referrerPolicy="no-referrer" /> : initials}
    </span>
  );
}

export function Breadcrumbs({ items }) {
  return (
    <nav aria-label="Breadcrumb" className="breadcrumbs">
      {items.map((it, i) => (
        <span key={i} className="row" style={{ gap: 6 }}>
          {i > 0 && <Icon name="chevronRight" size={14} />}
          {it.to ? <Link to={it.to}>{it.label}</Link> : <span aria-current="page">{it.label}</span>}
        </span>
      ))}
    </nav>
  );
}

export function PageHeader({ title, subtitle, actions, crumbs }) {
  return (
    <>
      {crumbs && <Breadcrumbs items={crumbs} />}
      <header className="page-header">
        <div>
          <h1>{title}</h1>
          {subtitle && <p>{subtitle}</p>}
        </div>
        {actions && <div className="row-wrap">{actions}</div>}
      </header>
    </>
  );
}

export function Tabs({ tabs, value, onChange, label }) {
  const refs = useRef([]);
  const onKey = (e, i) => {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    const next = (i + (e.key === "ArrowRight" ? 1 : -1) + tabs.length) % tabs.length;
    onChange(tabs[next].value);
    refs.current[next]?.focus();
  };
  return (
    <div className="tabs" role="tablist" aria-label={label}>
      {tabs.map((t, i) => (
        <button key={t.value} ref={(el) => (refs.current[i] = el)} role="tab" aria-selected={value === t.value}
          tabIndex={value === t.value ? 0 : -1} onClick={() => onChange(t.value)} onKeyDown={(e) => onKey(e, i)}>
          {t.label}
        </button>
      ))}
    </div>
  );
}

/** Modal dialog with Escape-to-close, initial focus and a simple focus trap. */
export function Dialog({ title, children, onClose, actions, wide, labelledBy }) {
  const ref = useRef(null);
  const titleId = useId();
  useEffect(() => {
    const prev = document.activeElement;
    const node = ref.current;
    const focusables = () => node.querySelectorAll("button, [href], input, select, textarea, [tabindex]:not([tabindex='-1'])");
    (focusables()[0] || node).focus();
    const onKey = (e) => {
      if (e.key === "Escape") onClose();
      if (e.key === "Tab") {
        const f = Array.from(focusables());
        if (!f.length) return;
        if (e.shiftKey && document.activeElement === f[0]) { e.preventDefault(); f[f.length - 1].focus(); }
        else if (!e.shiftKey && document.activeElement === f[f.length - 1]) { e.preventDefault(); f[0].focus(); }
      }
    };
    node.addEventListener("keydown", onKey);
    return () => { node.removeEventListener("keydown", onKey); prev?.focus?.(); };
  }, [onClose]);
  return (
    <div className="dialog-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`dialog${wide ? " dialog-wide" : ""}`} role="dialog" aria-modal="true" aria-labelledby={labelledBy || titleId} ref={ref} tabIndex={-1}>
        <h2 id={titleId} style={{ fontSize: "var(--text-lg)" }}>{title}</h2>
        {children}
        {actions && <div className="dialog-actions">{actions}</div>}
      </div>
    </div>
  );
}

/** Confirmation for destructive actions. Optionally requires typing a phrase. */
export function ConfirmDialog({ title, message, confirmLabel = "Confirm", danger, requirePhrase, onConfirm, onClose, busy, children }) {
  const [typed, setTyped] = useState("");
  const ok = !requirePhrase || typed === requirePhrase;
  return (
    <Dialog title={title} onClose={onClose}
      actions={<>
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button variant={danger ? "danger" : "primary"} disabled={!ok} loading={busy} onClick={onConfirm}>{confirmLabel}</Button>
      </>}>
      {message && <p className="muted">{message}</p>}
      {children}
      {requirePhrase && (
        <Field label={<>Type <strong>{requirePhrase}</strong> to confirm</>}>
          {(p) => <input className="input" value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" {...p} />}
        </Field>
      )}
    </Dialog>
  );
}

export function LangBadge({ id }) {
  const abbr = { c: "C", cpp: "C++", java: "JV", python: "PY", javascript: "JS", go: "GO" }[id] || id;
  return <span className={`lang-abbr lang-${id}`} aria-hidden="true">{abbr}</span>;
}

export function timeAgo(iso) {
  if (!iso) return "";
  const s = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 45) return "just now";
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  if (d < 30) return `${d} d ago`;
  return new Date(iso).toLocaleDateString();
}

export function useDocumentTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} · HackerCode` : "HackerCode";
  }, [title]);
}
