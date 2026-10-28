import { Link } from "react-router-dom";
import { prefersReducedMotion, useAuth } from "../api/auth.jsx";
import Orbs from "./Orbs.jsx";

/** Split layout from the reference design: immersive 3D panel on the left, clean form on the right. */
export default function AuthLayout({ children, switchPrompt, switchLabel, switchTo }) {
  const { prefs } = useAuth();
  const reduced = prefersReducedMotion(prefs);
  return (
    <div className="auth-page">
      <div className="auth-shell">
        <section className="auth-visual" aria-label="About HackerCode">
          <Orbs reduced={reduced} />
          <div className="auth-glass">
            <div className="brand-box"><strong>Hacker</strong>Code</div>
            <div className="auth-accent" />
            <p className="auth-eyebrow">HackerCode</p>
            <h2 className="auth-headline">Understand your code. Master your patterns. Get placement-ready.</h2>
            <p className="auth-sub">
              Review code intelligently, practice interview problems systematically, and track your improvement over time.
            </p>
            <ul className="auth-points">
              <li>442 placement problems across 18 topics</li>
              <li>C, C++, Java, Python, JavaScript and Go</li>
              <li>AI-assisted reviews with complexity analysis</li>
            </ul>
            {switchTo && (
              <div className="auth-switch">
                <p>{switchPrompt}</p>
                <Link to={switchTo}>{switchLabel}</Link>
              </div>
            )}
          </div>
        </section>
        <span className="orb teal auth-corner-orb" aria-hidden="true" style={{ animationPlayState: reduced ? "paused" : undefined }} />
        <section className="auth-form-panel">{children}</section>
      </div>
    </div>
  );
}
