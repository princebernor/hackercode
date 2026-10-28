package dev.hackercode.progress;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeDraftRepository extends JpaRepository<CodeDraft, Long> {
    Optional<CodeDraft> findByUserIdAndDraftKeyAndLanguage(Long userId, String draftKey, String language);

    List<CodeDraft> findByUserIdAndDraftKey(Long userId, String draftKey);

    List<CodeDraft> findByUserId(Long userId);

    void deleteByUserIdAndDraftKeyAndLanguage(Long userId, String draftKey, String language);
}
