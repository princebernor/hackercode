package dev.hackercode.review;

import dev.hackercode.execution.Language;
import java.util.List;

/** An AI review backend. Implementations keep their API keys server side and never return them. */
public interface AiReviewer {

    record Request(Language language, String code, String problemTitle, String detailLevel, List<String> categories,
                   boolean beginnerFriendly, boolean includeRefactor) {}

    /** Either a report or a user-safe failure message (never both). */
    record Outcome(ReviewReport report, String model, String failureCode, String failureMessage) {
        static Outcome ok(ReviewReport report, String model) { return new Outcome(report, model, null, null); }
        static Outcome failed(String code, String message) { return new Outcome(null, null, code, message); }
        boolean succeeded() { return report != null; }
    }

    String name();

    boolean configured();

    String model();

    String setupHint();

    Outcome review(Request request);
}
