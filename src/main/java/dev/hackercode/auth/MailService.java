package dev.hackercode.auth;

import dev.hackercode.config.AppProperties;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email through SMTP when SMTP_HOST is configured.
 * <p>
 * Without SMTP, and only when the app runs on localhost, the message (including its one-time link) is printed to
 * the server console so the flows can be exercised during development. On any other host nothing is sent and
 * the UI reports that email delivery is not configured.
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    public enum Mode { SMTP, DEV_CONSOLE, DISABLED }

    private final JavaMailSender sender;
    private final AppProperties props;
    private final Mode mode;

    public MailService(ObjectProvider<JavaMailSender> sender, AppProperties props, @Value("${spring.mail.host:}") String smtpHost) {
        this.props = props;
        if (smtpHost != null && !smtpHost.isBlank() && sender.getIfAvailable() != null) {
            this.sender = sender.getIfAvailable();
            this.mode = Mode.SMTP;
        } else {
            this.sender = null;
            this.mode = isLocal(props.baseUrl()) ? Mode.DEV_CONSOLE : Mode.DISABLED;
        }
    }

    private static boolean isLocal(String baseUrl) {
        try {
            String host = URI.create(baseUrl).getHost();
            return "localhost".equals(host) || "127.0.0.1".equals(host);
        } catch (Exception e) {
            return false;
        }
    }

    public Mode mode() { return mode; }

    /** Returns true when the message was handed to SMTP or printed in local development mode. */
    public boolean send(String to, String subject, String body) {
        switch (mode) {
            case SMTP -> {
                try {
                    SimpleMailMessage msg = new SimpleMailMessage();
                    msg.setFrom(props.mailFrom());
                    msg.setTo(to);
                    msg.setSubject(subject);
                    msg.setText(body);
                    sender.send(msg);
                    return true;
                } catch (Exception e) {
                    log.error("Email delivery failed: {}", e.getMessage());
                    return false;
                }
            }
            case DEV_CONSOLE -> {
                log.info("\n==== [DEV MAIL - SMTP not configured, localhost only] ====\nTo: {}\nSubject: {}\n\n{}\n==========================================================",
                        to, subject, body);
                return true;
            }
            default -> {
                log.warn("Email to a user was not sent because SMTP is not configured (subject: {})", subject);
                return false;
            }
        }
    }

    public String link(String path) {
        String base = props.baseUrl().endsWith("/") ? props.baseUrl().substring(0, props.baseUrl().length() - 1) : props.baseUrl();
        return base + path;
    }
}
