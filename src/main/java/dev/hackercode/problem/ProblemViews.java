package dev.hackercode.problem;

import dev.hackercode.progress.ProblemProgress;
import java.time.Instant;
import java.util.List;

/** API representations of problems and topics. */
public final class ProblemViews {
    private ProblemViews() {}

    public record TopicRef(String slug, String name, String label) {
        static TopicRef of(Topic t) {
            return new TopicRef(t.getSlug(), t.getName(), Labels.of(t.getName()).label());
        }
    }

    public record Summary(String id, Integer number, String slug, String title, String sourceNote, TopicRef topic,
                          String subsection, String subsectionLabel, String difficulty, boolean hasPracticeUrl,
                          boolean hasStatement, String provenance, boolean archived, String status, boolean bookmarked) {
        public static Summary of(Problem p, Topic t, ProblemProgress pr) {
            return new Summary(p.getId(), p.getSourceNumber(), p.getSlug(), p.getTitle(), p.getSourceNote(), TopicRef.of(t),
                    p.getSubsection(), Labels.of(p.getSubsection()).label(), p.getDifficulty(), !p.practiceUrlList().isEmpty(),
                    p.getStatement() != null, p.getProvenance().name(), p.isArchived(),
                    pr == null ? ProblemProgress.Status.NOT_STARTED.name() : pr.getStatus().name(), pr != null && pr.isBookmarked());
        }
    }

    public record Progress(String status, boolean bookmarked, String notes, Instant lastViewedAt, Instant solvedAt) {
        public static Progress of(ProblemProgress p) {
            if (p == null) return new Progress(ProblemProgress.Status.NOT_STARTED.name(), false, null, null, null);
            return new Progress(p.getStatus().name(), p.isBookmarked(), p.getNotes(), p.getLastViewedAt(), p.getSolvedAt());
        }
    }

    /** Fields copied verbatim from the source PDF. */
    public record Source(String document, Integer number, String text, String note, String topic, String subsection, List<String> practiceUrls) {}

    /** Fields added after import (by an operator). Null when nothing has been added. */
    public record AddedContent(String statement, String constraints, String examples, String difficulty, int testCount, String addedBy) {}

    public record Detail(String id, Integer number, String slug, String title, TopicRef topic, String subsection,
                         String subsectionLabel, String subsectionNote, String provenance, boolean archived,
                         Source source, AddedContent added, Progress progress, Summary previous, Summary next,
                         List<Summary> related) {}

    public record SubsectionStats(String name, String label, String note, int total, int solved) {}

    public record TopicStats(String slug, String name, String label, String kind, String note, int position,
                             int total, int solved, int attempted, List<SubsectionStats> subsections) {}
}
