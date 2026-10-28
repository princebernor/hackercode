package dev.hackercode.problem;

/**
 * Source headings often embed advice, e.g. "Two pointer(This is very imp pattern ...)" or
 * "Array - Data structure". This splits them into a short label plus the original note for display,
 * without altering the stored source text.
 */
public record Labels(String label, String kind, String note) {

    public static Labels of(String source) {
        if (source == null) return new Labels(null, null, null);
        String head = source;
        String note = null;
        int paren = source.indexOf('(');
        if (paren > 0) {
            head = source.substring(0, paren).trim();
            note = source.substring(paren + 1).replaceAll("\\)\\s*$", "").trim();
        }
        String kind = null;
        int dash = head.indexOf(" - ");
        if (dash > 0) {
            kind = head.substring(dash + 3).trim();
            head = head.substring(0, dash).trim();
        }
        return new Labels(head, kind, note == null || note.isEmpty() ? null : note);
    }
}
