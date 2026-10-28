package dev.hackercode.progress;

import dev.hackercode.common.ApiException;
import dev.hackercode.execution.Language;
import dev.hackercode.problem.ProblemCatalog;
import dev.hackercode.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Saved code per problem (or the review workspace) and language, restored when the user comes back. */
@RestController
@RequestMapping("/api/drafts")
public class DraftController {
    public static final int MAX_CODE = 100_000;

    public record DraftView(String draftKey, String language, String code, Instant updatedAt) {}

    public record SaveDraft(@NotBlank @Size(max = 40) String draftKey, @NotBlank String language,
                            @NotNull @Size(max = MAX_CODE, message = "Code is limited to 100,000 characters") String code) {}

    private final CodeDraftRepository drafts;
    private final ProblemCatalog catalog;

    public DraftController(CodeDraftRepository drafts, ProblemCatalog catalog) {
        this.drafts = drafts;
        this.catalog = catalog;
    }

    private void checkKey(String key) {
        if (!CodeDraft.WORKSPACE.equals(key) && !catalog.get().byId().containsKey(key)) throw ApiException.notFound("Problem");
    }

    @GetMapping
    public List<DraftView> list(@RequestParam String draftKey) {
        checkKey(draftKey);
        return drafts.findByUserIdAndDraftKey(CurrentUser.id(), draftKey).stream()
                .map(d -> new DraftView(d.getDraftKey(), d.getLanguage(), d.getCode(), d.getUpdatedAt())).toList();
    }

    @PutMapping
    @Transactional
    public Map<String, Object> save(@Valid @RequestBody SaveDraft body) {
        checkKey(body.draftKey());
        String lang = Language.require(body.language()).id();
        Long userId = CurrentUser.id();
        CodeDraft draft = drafts.findByUserIdAndDraftKeyAndLanguage(userId, body.draftKey(), lang)
                .orElseGet(() -> new CodeDraft(userId, body.draftKey(), lang, body.code()));
        draft.update(body.code());
        drafts.save(draft);
        return Map.of("saved", true, "updatedAt", draft.getUpdatedAt());
    }

    @DeleteMapping
    @Transactional
    public Map<String, Object> reset(@RequestParam String draftKey, @RequestParam String language) {
        checkKey(draftKey);
        drafts.deleteByUserIdAndDraftKeyAndLanguage(CurrentUser.id(), draftKey, Language.require(language).id());
        return Map.of("deleted", true);
    }
}
