import { useCallback, useEffect, useRef, useState } from "react";
import { del, get, put, qs } from "../api/client.js";

/**
 * Editor state shared by the problem page and the review workspace: available languages, per-language saved
 * drafts (server side), starter templates and debounced autosave.
 */
export default function useCodeWorkspace(draftKey, prefs, initial) {
  const [languages, setLanguages] = useState([]);
  const [execution, setExecution] = useState(null);
  const [language, setLanguageState] = useState(null);
  const [code, setCodeState] = useState("");
  const [saveState, setSaveState] = useState("saved");
  const [ready, setReady] = useState(false);
  const [error, setError] = useState(null);
  const drafts = useRef({});
  const saved = useRef({});
  const timer = useRef(null);
  const autosave = prefs?.editor?.autosave !== false;

  const starterFor = useCallback((id, langs = languages) => langs.find((l) => l.id === id)?.starter || "", [languages]);

  useEffect(() => {
    let cancelled = false;
    setReady(false);
    Promise.all([get("/api/languages"), get(`/api/drafts${qs({ draftKey })}`)])
      .then(([langRes, draftList]) => {
        if (cancelled) return;
        const langs = langRes.languages;
        setLanguages(langs);
        setExecution(langRes.execution);
        drafts.current = {};
        draftList.forEach((d) => { drafts.current[d.language] = d.code; saved.current[d.language] = d.code; });
        const enabled = langs.filter((l) => l.enabled).map((l) => l.id);
        const latestDraft = [...draftList].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0]?.language;
        const pick = [initial?.language, latestDraft, prefs?.editor?.defaultLanguage, enabled[0]].find((l) => l && enabled.includes(l));
        setLanguageState(pick);
        setCodeState(initial?.code ?? drafts.current[pick] ?? langs.find((l) => l.id === pick)?.starter ?? "");
        setSaveState(initial?.code ? "unsaved" : "saved");
        setReady(true);
      })
      .catch((e) => { if (!cancelled) setError(e); });
    return () => { cancelled = true; clearTimeout(timer.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [draftKey]);

  const persist = useCallback(async (lang, value) => {
    if (!lang || saved.current[lang] === value) { setSaveState("saved"); return; }
    setSaveState("saving");
    try {
      await put("/api/drafts", { draftKey, language: lang, code: value });
      saved.current[lang] = value;
      setSaveState("saved");
    } catch {
      setSaveState("error");
    }
  }, [draftKey]);

  const setCode = useCallback((value) => {
    setCodeState(value);
    drafts.current[language] = value;
    setSaveState(saved.current[language] === value ? "saved" : "unsaved");
    if (autosave) {
      clearTimeout(timer.current);
      timer.current = setTimeout(() => persist(language, value), 1200);
    }
  }, [language, autosave, persist]);

  const saveNow = useCallback(() => {
    clearTimeout(timer.current);
    return persist(language, code);
  }, [language, code, persist]);

  const setLanguage = useCallback((next) => {
    if (next === language) return;
    clearTimeout(timer.current);
    if (autosave && saved.current[language] !== code) persist(language, code);
    setLanguageState(next);
    const value = drafts.current[next] ?? starterFor(next);
    setCodeState(value);
    setSaveState(saved.current[next] === value || !drafts.current[next] ? "saved" : "unsaved");
  }, [language, code, autosave, persist, starterFor]);

  /** Restore the starter template and forget the saved draft for the current language. */
  const reset = useCallback(async () => {
    clearTimeout(timer.current);
    const starter = starterFor(language);
    setCodeState(starter);
    drafts.current[language] = starter;
    try {
      await del(`/api/drafts${qs({ draftKey, language })}`);
      delete saved.current[language];
      setSaveState("saved");
    } catch {
      setSaveState("error");
    }
  }, [draftKey, language, starterFor]);

  const current = languages.find((l) => l.id === language);
  return { languages, execution, language, setLanguage, current, code, setCode, saveState, saveNow, reset, ready, error, autosave };
}

export const SAVE_LABEL = { saved: "Saved", saving: "Saving…", unsaved: "Unsaved changes", error: "Save failed" };
