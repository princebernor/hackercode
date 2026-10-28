// Bundles Monaco locally (no CDN), so the editor works offline and under the app's strict Content Security Policy.
import * as monaco from "monaco-editor";
import EditorWorker from "monaco-editor/editor/editor.worker?worker";
import TsWorker from "monaco-editor/languages/features/typescript/ts.worker?worker";
import { loader } from "@monaco-editor/react";

self.MonacoEnvironment = {
  getWorker(_id, label) {
    if (label === "typescript" || label === "javascript") return new TsWorker();
    return new EditorWorker();
  },
};

monaco.editor.defineTheme("hackercode-dark", {
  base: "vs-dark",
  inherit: true,
  rules: [
    { token: "comment", foreground: "6f8b96", fontStyle: "italic" },
    { token: "keyword", foreground: "3ee0b7" },
    { token: "string", foreground: "f8ac57" },
    { token: "number", foreground: "f5c84b" },
    { token: "type", foreground: "7fd6ff" },
  ],
  colors: {
    "editor.background": "#0d1b26",
    "editor.lineHighlightBackground": "#12283a",
    "editorLineNumber.foreground": "#4b6676",
    "editorLineNumber.activeForeground": "#a9c3cc",
    "editor.selectionBackground": "#1f5a5a",
    "editorCursor.foreground": "#22d3a6",
  },
});

monaco.editor.defineTheme("hackercode-light", {
  base: "vs",
  inherit: true,
  rules: [
    { token: "comment", foreground: "7d8c93", fontStyle: "italic" },
    { token: "keyword", foreground: "0b7c64" },
    { token: "string", foreground: "b8641a" },
    { token: "number", foreground: "9a6b00" },
  ],
  colors: {
    "editor.background": "#fbfdfc",
    "editor.lineHighlightBackground": "#eef7f3",
    "editorCursor.foreground": "#0e9f78",
  },
});

loader.config({ monaco });

export default monaco;
