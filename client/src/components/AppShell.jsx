import { Suspense, useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../api/auth.jsx";
import { get, post } from "../api/client.js";
import ErrorBoundary from "./ErrorBoundary.jsx";
import Icon from "./Icon.jsx";
import { useToast } from "./Toasts.jsx";
import { Avatar, Button, SkeletonRows, timeAgo } from "./ui.jsx";

const NAV = [
  { to: "/dashboard", label: "Dashboard", icon: "dashboard" },
  { to: "/review", label: "AI Code Review", icon: "review" },
  { to: "/problems", label: "Practice Problems", icon: "code" },
  { to: "/topics", label: "Topics & Patterns", icon: "layers" },
  { to: "/progress", label: "Progress", icon: "chart" },
  { to: "/bookmarks", label: "Bookmarks", icon: "bookmark" },
];
const ACCOUNT = [
  { to: "/settings", label: "Settings", icon: "settings", match: "/settings" },
  { to: "/profile", label: "Profile", icon: "user" },
];

export function Logo({ to = "/dashboard" }) {
  return (
    <Link to={to} className="logo" aria-label="HackerCode home">
      <span className="logo-mark">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M8 6 3 12l5 6M16 6l5 6-5 6" />
        </svg>
      </span>
      <span>Hacker<em>Code</em></span>
    </Link>
  );
}

function Notifications() {
  const [open, setOpen] = useState(false);
  const [data, setData] = useState({ items: [], unread: 0 });
  const ref = useRef(null);
  const load = () => get("/api/account/notifications").then(setData).catch(() => {});
  useEffect(() => { load(); }, []);
  useEffect(() => {
    if (!open) return;
    const close = (e) => { if (!ref.current?.contains(e.target)) setOpen(false); };
    const esc = (e) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", esc);
    return () => { document.removeEventListener("mousedown", close); document.removeEventListener("keydown", esc); };
  }, [open]);
  const markAll = async () => { await post("/api/account/notifications/read-all"); load(); };
  return (
    <div ref={ref} style={{ position: "relative" }}>
      <button className="icon-btn" aria-label={`Notifications${data.unread ? `, ${data.unread} unread` : ""}`} aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <Icon name="bell" />
        {data.unread > 0 && <span className="dot" />}
      </button>
      {open && (
        <div className="menu" style={{ width: 340 }} role="dialog" aria-label="Notifications">
          <div className="row between" style={{ padding: "6px 10px" }}>
            <strong className="small">Notifications</strong>
            {data.unread > 0 && <button className="btn btn-ghost btn-sm" style={{ width: "auto" }} onClick={markAll}>Mark all read</button>}
          </div>
          {data.items.length === 0 && <p className="small muted" style={{ padding: 10 }}>You're all caught up.</p>}
          {data.items.map((n) => (
            <div key={n.id} style={{ padding: "8px 10px", borderRadius: 6, background: n.readAt ? "transparent" : "var(--primary-soft)" }}>
              <div className="small" style={{ fontWeight: 600 }}>{n.title}</div>
              {n.body && <div className="xs muted">{n.body}</div>}
              <div className="xs subtle">{timeAgo(n.createdAt)}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function UserMenu() {
  const { user, signOut, isStaff } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const navigate = useNavigate();
  useEffect(() => {
    if (!open) return;
    const close = (e) => { if (!ref.current?.contains(e.target)) setOpen(false); };
    const esc = (e) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", esc);
    return () => { document.removeEventListener("mousedown", close); document.removeEventListener("keydown", esc); };
  }, [open]);
  const out = async () => { await signOut(); navigate("/signin"); };
  return (
    <div ref={ref} style={{ position: "relative" }}>
      <button className="user-btn" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <Avatar user={user} />
        <span className="user-name small" style={{ fontWeight: 500 }}>{user.displayName}</span>
        <Icon name="chevronDown" size={16} />
      </button>
      {open && (
        <div className="menu" role="menu" onClick={() => setOpen(false)}>
          <div style={{ padding: "6px 10px 10px" }}>
            <div className="small" style={{ fontWeight: 600 }}>{user.displayName}</div>
            <div className="xs muted truncate">{user.email}</div>
          </div>
          <Link role="menuitem" to="/profile"><Icon name="user" /> Profile</Link>
          <Link role="menuitem" to="/settings/account"><Icon name="settings" /> Settings</Link>
          {isStaff && <Link role="menuitem" to="/admin/overview"><Icon name="shield" /> Operator console</Link>}
          <hr />
          <button role="menuitem" onClick={out}><Icon name="logout" /> Sign out</button>
        </div>
      )}
    </div>
  );
}

function GlobalSearch() {
  const navigate = useNavigate();
  const location = useLocation();
  const [q, setQ] = useState("");
  const input = useRef(null);
  useEffect(() => {
    const onKey = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") { e.preventDefault(); input.current?.focus(); }
      if (e.key === "/" && !["INPUT", "TEXTAREA"].includes(document.activeElement?.tagName) && !document.activeElement?.closest(".monaco-editor")) {
        e.preventDefault(); input.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);
  useEffect(() => {
    if (location.pathname === "/problems") setQ(new URLSearchParams(location.search).get("q") || "");
  }, [location]);
  return (
    <form className="global-search" role="search" onSubmit={(e) => { e.preventDefault(); navigate(`/problems?q=${encodeURIComponent(q.trim())}`); }}>
      <Icon name="search" />
      <label htmlFor="global-search" className="sr-only">Search problems by title, number, topic or keyword</label>
      <input id="global-search" ref={input} className="input" placeholder="Search 442 problems: title, #number, topic…" value={q} onChange={(e) => setQ(e.target.value)} />
      <kbd>Ctrl K</kbd>
    </form>
  );
}

function VerifyBanner() {
  const { user, config } = useAuth();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  if (!user || user.emailVerified) return null;
  const resend = async () => {
    setBusy(true);
    try {
      const r = await post("/api/auth/resend-verification");
      if (r.emailDelivery === "dev_console") toast.info("Development mode: the verification link was printed in the server console.");
      else if (r.sent) toast.success("Verification email sent. Check your inbox.");
      else toast.error("Email delivery is not configured on this server. Ask an operator to configure SMTP.");
    } catch (e) { toast.error(e); } finally { setBusy(false); }
  };
  return (
    <div className="banner" role="status">
      <Icon name="mail" />
      <span>Verify <strong>{user.email}</strong> to unlock code execution and AI reviews.
        {config?.emailDelivery === "dev_console" && " (Development mode: verification links appear in the server console.)"}</span>
      <Button size="sm" loading={busy} onClick={resend}>Resend link</Button>
    </div>
  );
}

function MaintenanceBanner() {
  const { config, isStaff } = useAuth();
  if (!config?.maintenance?.enabled) return null;
  return (
    <div className="banner" role="status" style={{ background: "var(--info-soft)", color: "var(--info)" }}>
      <Icon name="info" /> <span>{config.maintenance.message}{isStaff && " (You can still make changes as staff.)"}</span>
    </div>
  );
}

export default function AppShell() {
  const { isStaff } = useAuth();
  const [navOpen, setNavOpen] = useState(false);
  const location = useLocation();
  useEffect(() => { setNavOpen(false); }, [location.pathname]);
  const link = (n) => (
    <NavLink key={n.to} to={n.to} className={({ isActive }) => `nav-link${isActive || (n.match && location.pathname.startsWith(n.match)) ? " active" : ""}`}>
      <Icon name={n.icon} /> {n.label}
    </NavLink>
  );
  return (
    <div className={`shell${navOpen ? " nav-open" : ""}`}>
      <a href="#main" className="skip-link">Skip to content</a>
      <aside className="sidebar" aria-label="Primary">
        <Logo />
        <nav className="stack-sm" style={{ gap: 2 }}>
          <div className="nav-section">Learn</div>
          {NAV.map(link)}
          <div className="nav-section">Account</div>
          {ACCOUNT.map(link)}
          {isStaff && (<><div className="nav-section">Operations</div>{link({ to: "/admin", label: "Operator console", icon: "shield", match: "/admin" })}</>)}
        </nav>
        <div className="sidebar-foot">
          Placement sheet: <strong>442 problems</strong> across 18 topics.
          <div style={{ marginTop: 6 }}><kbd>Ctrl K</kbd> search · <kbd>/</kbd> focus</div>
        </div>
      </aside>
      <div className="scrim" onClick={() => setNavOpen(false)} aria-hidden="true" />
      <div className="main">
        <header className="topbar">
          <button className="icon-btn menu-toggle" aria-label="Open navigation" aria-expanded={navOpen} onClick={() => setNavOpen(true)}>
            <Icon name="menu" />
          </button>
          <GlobalSearch />
          <div className="topbar-actions">
            <Notifications />
            <UserMenu />
          </div>
        </header>
        <MaintenanceBanner />
        <VerifyBanner />
        <main id="main" className="content" tabIndex={-1}>
          <ErrorBoundary resetKey={location.pathname}>
            <Suspense fallback={<SkeletonRows rows={6} h={56} />}>
              <Outlet />
            </Suspense>
          </ErrorBoundary>
        </main>
      </div>
    </div>
  );
}
