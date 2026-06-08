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
import java.util.List;

@Entity
@Table(name = "tutor_profiles")
public class TutorProfile {

    /** Truth-ledger / default pricing currency. KRW etc. are display-only. */
    public static final String BASE_CURRENCY = "USD";

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

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private TutorCategory category;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "intro_video_url", length = 500)
    private String introVideoUrl;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "available_languages", length = 255)
    private String availableLanguages;

    @Column(name = "lesson_price_25_amount", precision = 10, scale = 2)
    private BigDecimal lessonPrice25Amount;

    @Column(name = "lesson_price_50_amount", precision = 10, scale = 2)
    private BigDecimal lessonPrice50Amount;

    /**
     * Currency the tutor entered their price in. Truth ledger is USD, so this is
     * the input/display currency only.
     */
    @Column(name = "price_currency", length = 3)
    private String priceCurrency;

    /**
     * USD snapshots of the lesson prices captured at registration time so later
     * FX moves never reprice an existing offer. For now the platform prices in
     * USD directly, so these mirror the entered amount.
     */
    @Column(name = "lesson_price_25_usd_amount", precision = 10, scale = 2)
    private BigDecimal lessonPrice25UsdAmount;

    @Column(name = "lesson_price_50_usd_amount", precision = 10, scale = 2)
    private BigDecimal lessonPrice50UsdAmount;

    @Column(name = "available_time_note", length = 500)
    private String availableTimeNote;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

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
        this.hidden = false;
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
            TutorCategory category,
            String profileImageUrl,
            String introVideoUrl,
            String thumbnailUrl,
            List<String> availableLanguages,
            BigDecimal lessonPrice25Amount,
            BigDecimal lessonPrice50Amount,
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
        this.availableLanguages = joinLanguages(availableLanguages);
        this.lessonPrice25Amount = lessonPrice25Amount;
        this.lessonPrice50Amount = lessonPrice50Amount;
        // Truth ledger is USD. The platform prices directly in USD for now, so
        // snapshot the entered amounts as the USD figures and stamp the currency.
        this.priceCurrency = BASE_CURRENCY;
        this.lessonPrice25UsdAmount = lessonPrice25Amount;
        this.lessonPrice50UsdAmount = lessonPrice50Amount;
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

    public void hide() {
        if (hidden) {
            return;
        }
        hidden = true;
        touch();
    }

    public void show() {
        if (!hidden) {
            return;
        }
        hidden = false;
        touch();
    }

    private void validateRequiredForSubmit() {
        if (isBlank(displayName)
                || isBlank(shortIntroduction)
                || isBlank(aboutMe)
                || isBlank(whatIOffer)
                || category == null
                || isBlank(availableLanguages)
                || lessonPrice25Amount == null
                || lessonPrice25Amount.signum() <= 0
                || lessonPrice50Amount == null
                || lessonPrice50Amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Tutor profile required fields must be completed before submit.");
        }
    }

    private String joinLanguages(List<String> languages) {
        if (languages == null) {
            return null;
        }
        String joined = languages.stream()
                .map(String::trim)
                .filter(language -> !language.isBlank())
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        if (joined.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "availableLanguages must fit within 255 characters.");
        }
        return joined;
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

    public TutorCategory getCategory() {
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

    public List<String> getAvailableLanguages() {
        if (isBlank(availableLanguages)) {
            return List.of();
        }
        return List.of(availableLanguages.split(",")).stream()
                .map(String::trim)
                .filter(language -> !language.isBlank())
                .toList();
    }

    public BigDecimal getLessonPrice25Amount() {
        return lessonPrice25Amount;
    }

    public BigDecimal getLessonPrice50Amount() {
        return lessonPrice50Amount;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public BigDecimal getLessonPrice25UsdAmount() {
        return lessonPrice25UsdAmount;
    }

    public BigDecimal getLessonPrice50UsdAmount() {
        return lessonPrice50UsdAmount;
    }

    public String getAvailableTimeNote() {
        return availableTimeNote;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public boolean isHidden() {
        return hidden;
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
