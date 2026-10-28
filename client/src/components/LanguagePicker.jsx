import { useEffect, useMemo, useRef, useState } from "react";
import Icon from "./Icon.jsx";
import { LangBadge } from "./ui.jsx";

/** Searchable language dropdown (combobox pattern with keyboard support). */
export default function LanguagePicker({ languages, value, onChange }) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const ref = useRef(null);
  const inputRef = useRef(null);
  const current = languages.find((l) => l.id === value);
  const options = useMemo(
    () => languages.filter((l) => l.enabled && (l.label + " " + l.id + " " + l.fileName).toLowerCase().includes(query.toLowerCase())),
    [languages, query],
  );

  useEffect(() => {
    if (!open) return undefined;
    setActive(Math.max(0, options.findIndex((o) => o.id === value)));
    inputRef.current?.focus();
    const close = (e) => { if (!ref.current?.contains(e.target)) setOpen(false); };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const choose = (id) => { onChange(id); setOpen(false); setQuery(""); };
  const onKey = (e) => {
    if (e.key === "ArrowDown") { e.preventDefault(); setActive((a) => Math.min(options.length - 1, a + 1)); }
    else if (e.key === "ArrowUp") { e.preventDefault(); setActive((a) => Math.max(0, a - 1)); }
    else if (e.key === "Enter" && options[active]) { e.preventDefault(); choose(options[active].id); }
    else if (e.key === "Escape") setOpen(false);
  };

  return (
    <div className="lang-picker" ref={ref}>
      <button type="button" className="btn btn-sm" aria-haspopup="listbox" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        {current && <LangBadge id={current.id} />} {current?.label || "Language"} <Icon name="chevronDown" size={14} />
      </button>
      {open && (
        <div className="menu" style={{ padding: 8 }}>
          <label htmlFor="lang-search" className="sr-only">Search languages</label>
          <input id="lang-search" ref={inputRef} className="input input-sm" placeholder="Search languages" value={query}
            onChange={(e) => { setQuery(e.target.value); setActive(0); }} onKeyDown={onKey}
            role="combobox" aria-expanded="true" aria-controls="lang-list" aria-activedescendant={options[active] ? `lang-${options[active].id}` : undefined} />
          <ul id="lang-list" role="listbox" aria-label="Languages" style={{ listStyle: "none", margin: "6px 0 0", padding: 0 }}>
            {options.map((l, i) => (
              <li key={l.id} id={`lang-${l.id}`} role="option" aria-selected={l.id === value}
                onMouseDown={(e) => { e.preventDefault(); choose(l.id); }} onMouseEnter={() => setActive(i)}
                style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 10px", borderRadius: 6, cursor: "pointer",
                  background: i === active ? "var(--surface-2)" : undefined }}>
                <LangBadge id={l.id} /> <span className="grow">{l.label}</span>
                <span className="xs subtle mono">{l.fileName}</span>
                {l.id === value && <Icon name="check" size={14} />}
              </li>
            ))}
            {options.length === 0 && <li className="small muted" style={{ padding: 8 }}>No match</li>}
          </ul>
        </div>
      )}
    </div>
  );
}
