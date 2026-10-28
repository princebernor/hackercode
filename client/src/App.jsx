import { lazy, Suspense } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./api/auth.jsx";
import AppShell from "./components/AppShell.jsx";
import ErrorBoundary from "./components/ErrorBoundary.jsx";
import { SkeletonRows } from "./components/ui.jsx";
import { safeNext } from "./lib/password.js";
import { SignIn, SignUp, ForgotPassword, ResetPassword, VerifyEmail } from "./pages/AuthPages.jsx";
import { Terms, Privacy, NotFound } from "./pages/StaticPages.jsx";

// Route-level code splitting keeps the sign-in page light; the editor and 3D scene load only where used.
const Dashboard = lazy(() => import("./pages/Dashboard.jsx"));
const Onboarding = lazy(() => import("./pages/Onboarding.jsx"));
const Practice = lazy(() => import("./pages/Practice.jsx"));
const Topics = lazy(() => import("./pages/Topics.jsx"));
const ProblemDetail = lazy(() => import("./pages/ProblemDetail.jsx"));
const ReviewWorkspace = lazy(() => import("./pages/ReviewWorkspace.jsx"));
const ProgressPage = lazy(() => import("./pages/ProgressPage.jsx"));
const Bookmarks = lazy(() => import("./pages/Bookmarks.jsx"));
const Settings = lazy(() => import("./pages/settings/Settings.jsx"));
const Profile = lazy(() => import("./pages/Profile.jsx"));
const Admin = lazy(() => import("./pages/admin/Admin.jsx"));

function Loading() {
  return <div className="content"><SkeletonRows rows={6} h={56} /></div>;
}

function RequireAuth({ children }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <Loading />;
  if (!user) return <Navigate to={`/signin?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
  if (!user.onboardingCompleted && location.pathname !== "/onboarding") return <Navigate to="/onboarding" replace />;
  return children;
}

function RequireStaff({ children }) {
  const { isStaff } = useAuth();
  // The server enforces permissions on every admin endpoint; this only avoids showing a page that would 403.
  if (!isStaff) return <NotFound />;
  return children;
}

function GuestOnly({ children }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return null;
  if (user) {
    const next = safeNext(new URLSearchParams(location.search).get("next"));
    return <Navigate to={user.onboardingCompleted ? next : "/onboarding"} replace />;
  }
  return children;
}

export default function App() {
  return (
    <ErrorBoundary>
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/signin" element={<GuestOnly><SignIn /></GuestOnly>} />
        <Route path="/signup" element={<GuestOnly><SignUp /></GuestOnly>} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="/verify-email" element={<VerifyEmail />} />
        <Route path="/terms" element={<Terms />} />
        <Route path="/privacy" element={<Privacy />} />
        <Route path="/onboarding" element={<RequireAuth><Onboarding /></RequireAuth>} />
        <Route element={<RequireAuth><AppShell /></RequireAuth>}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/review" element={<ReviewWorkspace />} />
          <Route path="/problems" element={<Practice />} />
          <Route path="/problems/:idOrSlug" element={<ProblemDetail />} />
          <Route path="/topics" element={<Topics />} />
          <Route path="/progress" element={<ProgressPage />} />
          <Route path="/bookmarks" element={<Bookmarks />} />
          <Route path="/settings" element={<Navigate to="/settings/account" replace />} />
          <Route path="/settings/:section" element={<Settings />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/admin" element={<Navigate to="/admin/overview" replace />} />
          <Route path="/admin/:tab" element={<RequireStaff><Admin /></RequireStaff>} />
          <Route path="*" element={<NotFound inShell />} />
        </Route>
      </Routes>
    </Suspense>
    </ErrorBoundary>
  );
}
