package com.haru.user.domain;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "mobile_number", length = 30)
    private String mobileNumber;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "active_role", nullable = false, length = 20)
    private Role activeRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected UserAccount() {
    }

    private UserAccount(String email, String passwordHash, String name, String timeZone) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.timeZone = normalizeTimeZone(timeZone);
        this.activeRole = Role.STUDENT;
        this.accountStatus = AccountStatus.ACTIVE;
        this.roles = EnumSet.of(Role.STUDENT);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static UserAccount student(String email, String passwordHash, String name, String timeZone) {
        return new UserAccount(email.toLowerCase(), passwordHash, name, timeZone);
    }

    public void updateProfile(String name, String mobileNumber, String timeZone) {
        this.name = name;
        this.mobileNumber = mobileNumber;
        this.timeZone = normalizeTimeZone(timeZone);
        touch();
    }

    public void changeActiveRole(Role activeRole) {
        if (!roles.contains(activeRole)) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ASSIGNED, "The requested activeRole is not assigned to this user.");
        }
        this.activeRole = activeRole;
        touch();
    }

    public void ensureActive() {
        if (accountStatus != AccountStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE, "This account is not active.");
        }
    }

    public void recordLogin() {
        this.lastLoginAt = Instant.now();
        touch();
    }

    public void addRole(Role role) {
        roles.add(role);
        touch();
    }

    private static String normalizeTimeZone(String timeZone) {
        String resolved = timeZone == null || timeZone.isBlank() ? "Asia/Seoul" : timeZone;
        try {
            return ZoneId.of(resolved).getId();
        } catch (DateTimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "timeZone must be a valid IANA timezone.");
        }
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Role getActiveRole() {
        return activeRole;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
