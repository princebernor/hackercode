package dev.hackercode.user;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    long countByLastLoginAtAfter(Instant since);

    long countByRole(Role role);

    @Query("""
            select u from User u
            where (:q is null or lower(u.email) like lower(concat('%', :q, '%'))
                              or lower(u.displayName) like lower(concat('%', :q, '%')))
              and (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
            """)
    Page<User> search(@Param("q") String q, @Param("role") Role role, @Param("status") User.Status status, Pageable pageable);
}
