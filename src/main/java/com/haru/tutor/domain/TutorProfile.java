package com.haru.tutor.domain;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.user.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tutor_profiles")
public class TutorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount user;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "short_introduction", length = 255)
    private String shortIntroduction;

    @Column(name = "about_me", columnDefinition = "TEXT")
    private String aboutMe;

    @Column(name = "what_i_offer", columnDefinition = "TEXT")
    private String whatIOffer;

    @Column(length = 50)
    private String category;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "intro_video_url", length = 500)
    private String introVideoUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "available_languages", length = 255)
    private String availableLanguages;

    @Column(name = "lesson_price_amount", precision = 10, scale = 2)
    private BigDecimal lessonPriceAmount;

    @Column(name = "available_time_note", length = 500)
    private String availableTimeNote;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TutorProfileStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TutorProfile() {
    }

    private TutorProfile(UserAccount user) {
        this.user = user;
        this.status = TutorProfileStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static TutorProfile draft(UserAccount user) {
        return new TutorProfile(user);
    }

    public void update(
            String displayName,
            String shortIntroduction,
            String aboutMe,
            String whatIOffer,
            String category,
            String profileImageUrl,
            String introVideoUrl,
            String thumbnailUrl,
            String availableLanguages,
            BigDecimal lessonPriceAmount,
            String availableTimeNote,
            String paymentMethod
    ) {
        this.displayName = displayName;
        this.shortIntroduction = shortIntroduction;
        this.aboutMe = aboutMe;
        this.whatIOffer = whatIOffer;
        this.category = category;
        this.profileImageUrl = profileImageUrl;
        this.introVideoUrl = introVideoUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.availableLanguages = availableLanguages;
        this.lessonPriceAmount = lessonPriceAmount;
        this.availableTimeNote = availableTimeNote;
        this.paymentMethod = paymentMethod;
        if (status == TutorProfileStatus.REJECTED || status == TutorProfileStatus.APPROVED) {
            status = TutorProfileStatus.DRAFT;
            rejectedAt = null;
            approvedAt = null;
        }
        touch();
    }

    public void submit() {
        validateRequiredForSubmit();
        status = TutorProfileStatus.PENDING;
        submittedAt = Instant.now();
        approvedAt = null;
        rejectedAt = null;
        touch();
    }

    public void approve() {
        if (status != TutorProfileStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only pending tutor profiles can be approved.");
        }
        status = TutorProfileStatus.APPROVED;
        approvedAt = Instant.now();
        rejectedAt = null;
        touch();
    }

    public void reject() {
        if (status != TutorProfileStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only pending tutor profiles can be rejected.");
        }
        status = TutorProfileStatus.REJECTED;
        rejectedAt = Instant.now();
        approvedAt = null;
        touch();
    }

    private void validateRequiredForSubmit() {
        if (isBlank(displayName)
                || isBlank(shortIntroduction)
                || isBlank(aboutMe)
                || isBlank(whatIOffer)
                || isBlank(category)
                || isBlank(availableLanguages)
                || lessonPriceAmount == null
                || lessonPriceAmount.signum() < 0
                || isBlank(availableTimeNote)
                || isBlank(paymentMethod)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Tutor profile required fields must be completed before submit.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getShortIntroduction() {
        return shortIntroduction;
    }

    public String getAboutMe() {
        return aboutMe;
    }

    public String getWhatIOffer() {
        return whatIOffer;
    }

    public String getCategory() {
        return category;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public String getIntroVideoUrl() {
        return introVideoUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getAvailableLanguages() {
        return availableLanguages;
    }

    public BigDecimal getLessonPriceAmount() {
        return lessonPriceAmount;
    }

    public String getAvailableTimeNote() {
        return availableTimeNote;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public TutorProfileStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
