package dev.hackercode.admin;

import dev.hackercode.config.AppProperties;
import dev.hackercode.user.Role;
import dev.hackercode.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controlled bootstrap of the first administrator. There are no built-in admin credentials: the operator signs
 * up normally, sets HC_BOOTSTRAP_ADMIN_EMAIL to that address on the server and restarts. The promotion is
 * written to the audit log. Remove the variable afterwards.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties props;
    private final UserRepository users;
    private final AuditService audit;

    public AdminBootstrap(AppProperties props, UserRepository users, AuditService audit) {
        this.props = props;
        this.users = users;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = props.bootstrapAdminEmail();
        if (email == null || email.isBlank()) return;
        users.findByEmailIgnoreCase(email.trim()).ifPresentOrElse(user -> {
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                user.touch();
                audit.recordAs(null, "system:bootstrap", "admin.bootstrap_promoted", "user", user.getId(), "promoted to ADMIN via HC_BOOTSTRAP_ADMIN_EMAIL");
                log.warn("Promoted {} to ADMIN via HC_BOOTSTRAP_ADMIN_EMAIL. Unset the variable now.", user.getEmail());
            }
        }, () -> log.warn("HC_BOOTSTRAP_ADMIN_EMAIL is set but no account with that email exists yet. Sign up first, then restart."));
    }
}
