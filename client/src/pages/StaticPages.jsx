import { Link } from "react-router-dom";
import { Logo } from "../components/AppShell.jsx";
import { EmptyState, useDocumentTitle } from "../components/ui.jsx";

function DocShell({ children }) {
  return (
    <div style={{ minHeight: "100vh", background: "var(--bg-app)" }}>
      <header style={{ background: "var(--navy-900)", padding: "12px 24px" }}><Logo to="/" /></header>
      <main className="content doc">{children}</main>
    </div>
  );
}

export function Terms() {
  useDocumentTitle("Terms");
  return (
    <DocShell>
      <h1>Terms of use</h1>
      <p className="muted">Applies to this HackerCode deployment. The operator of this instance may replace this text.</p>
      <h2>Your account</h2>
      <p>Keep your credentials private. You are responsible for activity on your account. Operators may suspend accounts that abuse the service.</p>
      <h2>Acceptable use</h2>
      <p>Code execution exists for learning. Do not submit code intended to attack the sandbox, other users, or third-party systems, and do not attempt to bypass rate limits.</p>
      <h2>AI reviews</h2>
      <p>AI-generated reviews can be incomplete or wrong. They are suggestions, not guarantees that code is correct or secure.</p>
      <h2>Problem library</h2>
      <p>Problem titles and links come from the CodeSheet problem list. Linked practice pages belong to their respective owners.</p>
    </DocShell>
  );
}

export function Privacy() {
  useDocumentTitle("Privacy");
  return (
    <DocShell>
      <h1>Privacy policy</h1>
      <h2>What we store</h2>
      <p>Your email, display name, hashed password (or Google account link), preferences, problem progress, notes, saved code, run results and review history. Device sessions record a browser description and a partial IP address.</p>
      <h2>What we do not store</h2>
      <p>Plain-text passwords, payment details, or full IP addresses. Source code is never written to server logs.</p>
      <h2>AI providers</h2>
      <p>When you request an AI review, the code you submit is sent to the configured AI provider to generate the review.</p>
      <h2>Retention</h2>
      <p>Plain code runs are deleted after 30 days, reviews after 180 days, activity and audit records after 365 days. Saved submissions stay until you delete your account.</p>
      <h2>Your controls</h2>
      <p>In Settings → Privacy &amp; data you can turn off activity tracking and history, export all your data as JSON, or permanently delete your account.</p>
    </DocShell>
  );
}

export function NotFound({ inShell }) {
  useDocumentTitle("Not found");
  const body = (
    <EmptyState icon="search" title="Page not found" action={<Link className="btn btn-primary" to="/dashboard">Go to dashboard</Link>}>
      The page you are looking for does not exist or you do not have access to it.
    </EmptyState>
  );
  return inShell ? body : <DocShell>{body}</DocShell>;
}
