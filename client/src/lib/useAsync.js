import { useCallback, useEffect, useRef, useState } from "react";

/** Runs an async loader when deps change. Ignores stale responses so fast navigation never shows old data. */
export default function useAsync(loader, deps) {
  const [state, setState] = useState({ data: null, error: null, loading: true });
  const counter = useRef(0);
  const run = useCallback(() => {
    const id = ++counter.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    return Promise.resolve()
      .then(loader)
      .then((data) => { if (id === counter.current) setState({ data, error: null, loading: false }); })
      .catch((error) => { if (id === counter.current) setState((s) => ({ data: s.data, error, loading: false })); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { run(); }, [run]);
  const setData = useCallback((updater) => setState((s) => ({ ...s, data: typeof updater === "function" ? updater(s.data) : updater })), []);
  return { ...state, reload: run, setData };
}
