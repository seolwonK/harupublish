package com.haru.user.infra;

import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findWithRolesById(Long id);

    @Query("select u from UserAccount u join u.roles r where r = :role")
    List<UserAccount> findAllByRole(@Param("role") Role role);

    /** Chat contact search by name or email, excluding self and admins. */
    @Query("""
            select u from UserAccount u
            where u.id <> :excludeId
              and (lower(u.name) like lower(concat('%', :query, '%'))
                   or lower(u.email) like lower(concat('%', :query, '%')))
              and :adminRole not member of u.roles
            order by u.name asc
            """)
    List<UserAccount> searchContacts(
            @Param("query") String query,
            @Param("excludeId") Long excludeId,
            @Param("adminRole") Role adminRole,
            Pageable pageable
    );
}
