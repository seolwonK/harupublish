package com.haru.user.infra;

import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
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
}
