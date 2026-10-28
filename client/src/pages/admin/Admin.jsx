import { useNavigate, useParams } from "react-router-dom";
import { useAuth } from "../../api/auth.jsx";
import { PageHeader, Tabs, useDocumentTitle } from "../../components/ui.jsx";
import { AuditLog, Operations } from "./AdminAudit.jsx";
import AdminConfig from "./AdminConfig.jsx";
import AdminOverview from "./AdminOverview.jsx";
import AdminProblems from "./AdminProblems.jsx";
import AdminUsers from "./AdminUsers.jsx";

export default function Admin() {
  const { tab = "overview" } = useParams();
  const { isAdmin } = useAuth();
  const navigate = useNavigate();
  useDocumentTitle("Operator console");
  // Moderators see the tabs they are permitted to use; the server enforces the same rules.
  const tabs = [
    { value: "overview", label: "Overview" },
    { value: "users", label: "Users" },
    { value: "problems", label: "Problems" },
    { value: "config", label: "Platform" },
    ...(isAdmin ? [{ value: "audit", label: "Audit log" }, { value: "operations", label: "Operations" }] : []),
  ];
  const current = tabs.some((t) => t.value === tab) ? tab : "overview";
  return (
    <>
      <PageHeader title="Operator console" subtitle={isAdmin ? "Administrator access" : "Moderator access: problem curation and read-only user views"}
        crumbs={[{ label: "Operator console", to: "/admin/overview" }, { label: tabs.find((t) => t.value === current).label }]} />
      <div className="admin-tabs"><Tabs label="Console sections" value={current} onChange={(v) => navigate(`/admin/${v}`)} tabs={tabs} /></div>
      {current === "overview" && <AdminOverview />}
      {current === "users" && <AdminUsers />}
      {current === "problems" && <AdminProblems />}
      {current === "config" && <AdminConfig />}
      {current === "audit" && <AuditLog />}
      {current === "operations" && <Operations />}
    </>
  );
}
