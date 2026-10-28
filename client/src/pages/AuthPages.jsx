import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../api/auth.jsx";
import { post } from "../api/client.js";
import AuthLayout from "../components/AuthLayout.jsx";
import Icon, { GoogleG } from "../components/Icon.jsx";
import { Alert, Button, Field, PasswordInput, useDocumentTitle } from "../components/ui.jsx";
import { EMAIL_RE, STRENGTH_LABEL, passwordProblems, passwordStrength, safeNext } from "../lib/password.js";

const OAUTH_ERRORS = {
  oauth: "Google sign-in did not complete. Please try again.",
  google_unverified: "Your Google account email is not verified, so we could not sign you in with it.",
  google_not_configured: "Google sign-in is not configured on this server yet.",
  suspended: "This account is suspended. Contact support if you think this is a mistake.",
  signup_closed: "New registrations are currently closed.",
};

function GoogleButton({ next }) {
  const { config } = useAuth();
  const enabled = config?.googleEnabled;
  return (
    <div className="stack-sm" style={{ maxWidth: 460 }}>
      <div className="divider">or</div>
      <a
        className="btn btn-google"
        href={enabled ? `/api/auth/google/start?next=${encodeURIComponent(next || "/dashboard")}` : undefined}
        aria-disabled={!enabled}
        onClick={(e) => { if (!enabled) e.preventDefault(); }}
        style={!enabled ? { opacity: 0.6, cursor: "not-allowed" } : undefined}
        role="button"
      >
        <GoogleG /> Continue with Google
      </a>
      {config && !enabled && <p className="field-hint">{config.googleSetupHint}</p>}
    </div>
  );
}

export function SignIn() {
  useDocumentTitle("Sign in");
  const { signIn, config } = useAuth();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const next = safeNext(params.get("next"));
  const [form, setForm] = useState({ email: "", password: "", rememberMe: false });
  const [show, setShow] = useState(false);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(OAUTH_ERRORS[params.get("error")] || "");
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const errs = {};
    if (!EMAIL_RE.test(form.email.trim())) errs.email = "Enter a valid email address.";
    if (!form.password) errs.password = "Enter your password.";
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    setError("");
    try {
      const user = await signIn(form.email.trim(), form.password, form.rememberMe);
      navigate(user.onboardingCompleted ? next : "/onboarding", { replace: true });
    } catch (err) {
      setError(err.message);
      setErrors(err.fields || {});
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout switchPrompt="New to HackerCode?" switchLabel="Create an account" switchTo="/signup">
      <h1 className="auth-title">Sign in</h1>
      {config?.maintenance?.enabled && <Alert kind="info">{config.maintenance.message}</Alert>}
      <form className="auth-form" onSubmit={submit} noValidate>
        {error && <Alert kind="danger">{error}</Alert>}
        <Field label="Email address" error={errors.email}>
          {(p) => <input className="input" type="email" autoComplete="email" placeholder="Enter your email" value={form.email}
            onChange={(e) => { setForm({ ...form, email: e.target.value }); setErrors((x) => ({ ...x, email: undefined })); }} {...p} />}
        </Field>
        <Field label="Password" error={errors.password}>
          {(p) => <PasswordInput show={show} onToggle={() => setShow(!show)} autoComplete="current-password" placeholder="Enter password"
            value={form.password} onChange={(e) => { setForm({ ...form, password: e.target.value }); setErrors((x) => ({ ...x, password: undefined })); }} {...p} />}
        </Field>
        <div className="row between">
          <label className="check">
            <input type="checkbox" checked={form.rememberMe} onChange={(e) => setForm({ ...form, rememberMe: e.target.checked })} />
            Remember me for 30 days
          </label>
          <Link to="/forgot-password" className="small">Forgot password?</Link>
        </div>
        <Button type="submit" variant="primary" size="lg" block loading={busy}>Sign in <Icon name="arrowRight" size={16} /></Button>
      </form>
      <GoogleButton next={next} />
      <p className="small muted">Don't have an account? <Link to={`/signup${params.get("next") ? `?next=${encodeURIComponent(next)}` : ""}`}>Sign up</Link></p>
    </AuthLayout>
  );
}

export function SignUp() {
  useDocumentTitle("Sign up");
  const { signUp, config } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: "", password: "", confirm: "", acceptTerms: false });
  const [show, setShow] = useState(false);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const strength = passwordStrength(form.password);
  const problems = passwordProblems(form.password, form.email);

  const validate = () => {
    const errs = {};
    if (!EMAIL_RE.test(form.email.trim())) errs.email = "Enter a valid email address.";
    if (problems.length) errs.password = `Password needs: ${problems.join(", ").toLowerCase()}.`;
    if (form.confirm !== form.password) errs.confirm = "Passwords do not match.";
    if (!form.acceptTerms) errs.acceptTerms = "You must accept the Terms and Privacy Policy.";
    return errs;
  };

  const submit = async (e) => {
    e.preventDefault();
    const errs = validate();
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setBusy(true);
    setError("");
    try {
      await signUp({ email: form.email.trim(), password: form.password, acceptTerms: form.acceptTerms });
      navigate("/onboarding", { replace: true });
    } catch (err) {
      setError(err.message);
      setErrors(err.fields || {});
    } finally {
      setBusy(false);
    }
  };

  if (config && !config.signupEnabled) {
    return (
      <AuthLayout switchPrompt="Already have an account?" switchLabel="Sign in" switchTo="/signin">
        <h1 className="auth-title">Sign up</h1>
        <Alert kind="info">New registrations are currently closed. Please check back later.</Alert>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout switchPrompt="Already have an account?" switchLabel="Sign in" switchTo="/signin">
      <h1 className="auth-title">Sign up</h1>
      <form className="auth-form" onSubmit={submit} noValidate>
        {error && <Alert kind="danger">{error}</Alert>}
        <Field label="Email address" error={errors.email}>
          {(p) => <input className="input" type="email" autoComplete="email" placeholder="Enter your email" value={form.email}
            onChange={(e) => { setForm({ ...form, email: e.target.value }); setErrors((x) => ({ ...x, email: undefined })); }} {...p} />}
        </Field>
        <Field label="Set password" error={errors.password} hint="At least 8 characters, with letters plus a number or symbol.">
          {(p) => <PasswordInput show={show} autoComplete="new-password" placeholder="Enter password" value={form.password}
            onChange={(e) => { setForm({ ...form, password: e.target.value }); setErrors((x) => ({ ...x, password: undefined })); }} {...p} />}
        </Field>
        {form.password && (
          <div className="stack-sm" aria-live="polite">
            <div className="strength" data-level={strength}><span /><span /><span /><span /></div>
            <span className="xs muted">Strength: <strong>{STRENGTH_LABEL[strength]}</strong>{problems.length > 0 && ` · needs ${problems.join(", ").toLowerCase()}`}</span>
          </div>
        )}
        <Field label="Confirm password" error={errors.confirm}>
          {(p) => <PasswordInput show={show} autoComplete="new-password" placeholder="Re-enter password" value={form.confirm}
            onChange={(e) => { setForm({ ...form, confirm: e.target.value }); setErrors((x) => ({ ...x, confirm: undefined })); }} {...p} />}
        </Field>
        <label className="check">
          <input type="checkbox" checked={show} onChange={(e) => setShow(e.target.checked)} /> Show password
        </label>
        <div className="field">
          <label className="check">
            <input type="checkbox" checked={form.acceptTerms} aria-invalid={errors.acceptTerms ? true : undefined}
              onChange={(e) => { setForm({ ...form, acceptTerms: e.target.checked }); setErrors((x) => ({ ...x, acceptTerms: undefined })); }} />
            <span>I agree to the <Link to="/terms" target="_blank">Terms</Link> and <Link to="/privacy" target="_blank">Privacy Policy</Link></span>
          </label>
          {errors.acceptTerms && <span className="field-error" role="alert">{errors.acceptTerms}</span>}
        </div>
        <Button type="submit" variant="primary" size="lg" block loading={busy}>Sign up <Icon name="arrowRight" size={16} /></Button>
      </form>
      <GoogleButton next="/onboarding" />
      <p className="small muted">Already have an account? <Link to="/signin">Sign in</Link></p>
    </AuthLayout>
  );
}

export function ForgotPassword() {
  useDocumentTitle("Forgot password");
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [done, setDone] = useState(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    if (!EMAIL_RE.test(email.trim())) { setError("Enter a valid email address."); return; }
    setBusy(true);
    setError("");
    try {
      setDone(await post("/api/auth/forgot-password", { email: email.trim() }));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <AuthLayout switchPrompt="Remembered it?" switchLabel="Sign in" switchTo="/signin">
      <h1 className="auth-title">Reset password</h1>
      {done ? (
        <div className="stack" style={{ maxWidth: 460 }}>
          <Alert kind="success">{done.message}</Alert>
          {done.emailDelivery === "dev_console" && <Alert kind="info">Development mode: SMTP is not configured, so the reset link was printed in the server console.</Alert>}
          {done.emailDelivery === "disabled" && <Alert kind="warning">Email delivery is not configured on this server, so no email can be sent. Contact an administrator.</Alert>}
          <Link to="/signin" className="btn btn-outline-primary">Back to sign in</Link>
        </div>
      ) : (
        <form className="auth-form" onSubmit={submit} noValidate>
          <p className="muted">Enter your account email and we'll send a link to choose a new password. The link expires in 30 minutes.</p>
          <Field label="Email address" error={error}>
            {(p) => <input className="input" type="email" autoComplete="email" placeholder="Enter your email" value={email} onChange={(e) => setEmail(e.target.value)} {...p} />}
          </Field>
          <Button type="submit" variant="primary" size="lg" block loading={busy}>Send reset link</Button>
          <Link to="/signin" className="small">Back to sign in</Link>
        </form>
      )}
    </AuthLayout>
  );
}

export function ResetPassword() {
  useDocumentTitle("Choose a new password");
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const navigate = useNavigate();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [show, setShow] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const problems = passwordProblems(password, "");
  const submit = async (e) => {
    e.preventDefault();
    if (problems.length) { setError(`Password needs: ${problems.join(", ").toLowerCase()}.`); return; }
    if (password !== confirm) { setError("Passwords do not match."); return; }
    setBusy(true);
    setError("");
    try {
      await post("/api/auth/reset-password", { token, password });
      setDone(true);
      setTimeout(() => navigate("/signin"), 2500);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };
  return (
    <AuthLayout switchPrompt="Remembered it?" switchLabel="Sign in" switchTo="/signin">
      <h1 className="auth-title">New password</h1>
      {!token && <Alert kind="danger">This reset link is incomplete. Request a new one from <Link to="/forgot-password">Forgot password</Link>.</Alert>}
      {done ? (
        <Alert kind="success">Password updated and all other sessions signed out. Redirecting to sign in…</Alert>
      ) : token && (
        <form className="auth-form" onSubmit={submit} noValidate>
          {error && <Alert kind="danger">{error}</Alert>}
          <Field label="New password">
            {(p) => <PasswordInput show={show} onToggle={() => setShow(!show)} autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} {...p} />}
          </Field>
          {password && <div className="strength" data-level={passwordStrength(password)}><span /><span /><span /><span /></div>}
          <Field label="Confirm new password">
            {(p) => <PasswordInput show={show} autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} {...p} />}
          </Field>
          <Button type="submit" variant="primary" size="lg" block loading={busy}>Update password</Button>
        </form>
      )}
    </AuthLayout>
  );
}

export function VerifyEmail() {
  useDocumentTitle("Verify email");
  const [params] = useSearchParams();
  const { refresh, user } = useAuth();
  const [state, setState] = useState({ status: "pending" });
  const started = useRef(false);
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const token = params.get("token");
    if (!token) { setState({ status: "error", message: "This verification link is incomplete." }); return; }
    post("/api/auth/verify-email", { token })
      .then((r) => { setState({ status: "ok", email: r.email }); refresh(); })
      .catch((e) => setState({ status: "error", message: e.message }));
  }, [params, refresh]);
  return (
    <AuthLayout>
      <h1 className="auth-title">Email verification</h1>
      <div className="stack" style={{ maxWidth: 460 }}>
        {state.status === "pending" && <p className="row"><span className="spinner" /> Verifying your email…</p>}
        {state.status === "ok" && <Alert kind="success"><strong>{state.email}</strong> is verified. Code execution and AI reviews are unlocked.</Alert>}
        {state.status === "error" && <Alert kind="danger">{state.message} You can request a new link from the banner after signing in.</Alert>}
        <Link className="btn btn-primary btn-lg" to={user ? "/dashboard" : "/signin"}>{user ? "Go to dashboard" : "Sign in"}</Link>
      </div>
    </AuthLayout>
  );
}
