import Editor from "@monaco-editor/react";
import { useEffect, useRef } from "react";
import "../lib/monaco.js";

/**
 * Monaco editor configured from the user's editor preferences. Built-in features include find/replace
 * (Ctrl+F / Ctrl+H), multi-cursor, folding and auto-indent. Extra shortcuts:
 *   Ctrl/Cmd+Enter        run
 *   Ctrl/Cmd+Shift+Enter  submit
 *   Ctrl/Cmd+S            save draft now
 */
export default function CodeEditor({ value, onChange, language, prefs, onRun, onSubmit, onSave, onMount, ariaLabel = "Code editor" }) {
  const handlers = useRef({ onRun, onSubmit, onSave });
  handlers.current = { onRun, onSubmit, onSave };
  const editorRef = useRef(null);

  const e = prefs?.editor || {};
  const options = {
    fontSize: e.fontSize || 14,
    tabSize: e.tabSize || 4,
    insertSpaces: e.insertSpaces !== false,
    wordWrap: e.wordWrap ? "on" : "off",
    minimap: { enabled: !!e.minimap },
    fontFamily: "'JetBrains Mono', Consolas, monospace",
    fontLigatures: false,
    scrollBeyondLastLine: false,
    automaticLayout: true,
    smoothScrolling: true,
    padding: { top: 12, bottom: 12 },
    renderLineHighlight: "line",
    tabFocusMode: false,
    ariaLabel,
    accessibilitySupport: "auto",
    formatOnPaste: false,
    detectIndentation: false,
  };

  useEffect(() => {
    editorRef.current?.updateOptions(options);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [e.fontSize, e.tabSize, e.insertSpaces, e.wordWrap, e.minimap]);

  return (
    <Editor
      height="100%"
      language={language === "cpp" || language === "c" ? "cpp" : language}
      value={value}
      theme={e.theme || "hackercode-dark"}
      options={options}
      onChange={(v) => onChange(v ?? "")}
      loading={<div className="editor-fallback"><span className="spinner" /> Loading editor…</div>}
      onMount={(editor, monaco) => {
        editorRef.current = editor;
        editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => handlers.current.onRun?.());
        editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyMod.Shift | monaco.KeyCode.Enter, () => handlers.current.onSubmit?.());
        editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => handlers.current.onSave?.());
        onMount?.(editor, monaco);
      }}
    />
  );
}
