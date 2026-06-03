package com.haru.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Maps a tutor to the initial promo platform-fee waiver (first 10 tutors, free
 * until year-end). While active and within {@code waiverUntil}, settlement uses
 * a 0% platform fee for the tutor. The withdrawal fee is NOT waived — promo
 * tutors still pay the 5% promo withdrawal fee, which is later paid back (see
 * withdrawal {@code promoPaybackPending}). One grant per tutor (unique).
 */
@Entity
@Table(name = "promo_fee_waiver_grants")
public class PromoFeeWaiverGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tutor_profile_id", nullable = false, unique = true)
    private Long tutorProfileId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "waiver_until")
    private LocalDate waiverUntil;

    @Column(name = "granted_by_admin_id")
    private Long grantedByAdminId;

    @Column(name = "revoked_by_admin_id")
    private Long revokedByAdminId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PromoFeeWaiverGrant() {
    }

    private PromoFeeWaiverGrant(Long tutorProfileId, LocalDate waiverUntil, Long grantedByAdminId, Instant now) {
        this.tutorProfileId = tutorProfileId;
        this.active = true;
        this.waiverUntil = waiverUntil;
        this.grantedByAdminId = grantedByAdminId;
        this.grantedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PromoFeeWaiverGrant grant(Long tutorProfileId, LocalDate waiverUntil, Long grantedByAdminId, Instant now) {
        return new PromoFeeWaiverGrant(tutorProfileId, waiverUntil, grantedByAdminId, now);
    }

    public void regrant(LocalDate waiverUntil, Long grantedByAdminId, Instant now) {
        this.active = true;
        this.waiverUntil = waiverUntil;
        this.grantedByAdminId = grantedByAdminId;
        this.grantedAt = now;
        this.revokedByAdminId = null;
        this.revokedAt = null;
        this.updatedAt = now;
    }

    public void revoke(Long revokedByAdminId, Instant now) {
        this.active = false;
        this.revokedByAdminId = revokedByAdminId;
        this.revokedAt = now;
        this.updatedAt = now;
    }

    /** Effective only while active and not past the waiver-until date. */
    public boolean isEffective(LocalDate on) {
        if (!active) {
            return false;
        }
        return waiverUntil == null || !on.isAfter(waiverUntil);
    }

    public Long getId() {
        return id;
    }

    public Long getTutorProfileId() {
        return tutorProfileId;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getWaiverUntil() {
        return waiverUntil;
    }

    public Long getGrantedByAdminId() {
        return grantedByAdminId;
    }

    public Long getRevokedByAdminId() {
        return revokedByAdminId;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
