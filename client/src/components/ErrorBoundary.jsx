import { Component } from "react";
import Icon from "./Icon.jsx";

/** Catches render errors (including failed lazy chunk loads) so a broken page never leaves a blank screen. */
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidUpdate(prev) {
    if (prev.resetKey !== this.props.resetKey && this.state.error) this.setState({ error: null });
  }

  render() {
    if (!this.state.error) return this.props.children;
    const chunk = /dynamically imported module|Importing a module script failed|Loading chunk/i.test(String(this.state.error?.message));
    return (
      <div className="empty" role="alert">
        <div className="empty-icon"><Icon name="alert" size={24} /></div>
        <h3>{chunk ? "HackerCode was updated" : "This page hit an error"}</h3>
        <p className="small" style={{ maxWidth: 420 }}>
          {chunk ? "A newer version is available. Reload to continue." : "Something went wrong while showing this page. Reloading usually fixes it."}
        </p>
        <button className="btn btn-primary" onClick={() => window.location.reload()}>Reload</button>
      </div>
    );
  }
}
