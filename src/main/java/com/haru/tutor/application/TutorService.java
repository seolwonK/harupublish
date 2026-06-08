package com.haru.tutor.application;

import com.haru.common.exception.NotFoundException;
import com.haru.review.infra.ReviewRepository;
import com.haru.review.infra.ReviewRepository.TutorReviewStats;
import com.haru.settings.application.PlatformSettingsService;
import com.haru.settings.domain.FeePolicy;
import com.haru.tutor.api.dto.ExpertListResponse;
import com.haru.tutor.api.dto.TutorProfileRequest;
import com.haru.tutor.api.dto.TutorProfileResponse;
import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.domain.TutorProfileStatus;
import com.haru.tutor.infra.TutorProfileRepository;
import com.haru.user.application.UserService;
import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TutorService {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private final UserService userService;
    private final TutorProfileRepository tutorProfileRepository;
    private final ReviewRepository reviewRepository;
    private final PlatformSettingsService platformSettingsService;

    public TutorService(
            UserService userService,
            TutorProfileRepository tutorProfileRepository,
            ReviewRepository reviewRepository,
            PlatformSettingsService platformSettingsService
    ) {
        this.userService = userService;
        this.tutorProfileRepository = tutorProfileRepository;
        this.reviewRepository = reviewRepository;
        this.platformSettingsService = platformSettingsService;
    }

    @Transactional
    public TutorProfileResponse switchToTutor(Long userId) {
        UserAccount user = userService.getActiveUser(userId);
        user.addRole(Role.TUTOR);
        user.changeActiveRole(Role.TUTOR);

        TutorProfile profile = tutorProfileRepository.findByUserId(userId)
                .orElseGet(() -> tutorProfileRepository.save(TutorProfile.draft(user)));
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public TutorProfileResponse getMyProfile(Long userId) {
        return toResponse(getProfileByUserId(userId));
    }

    @Transactional
    public TutorProfileResponse updateMyProfile(Long userId, TutorProfileRequest request) {
        TutorProfile profile = getProfileByUserId(userId);
        profile.update(
                request.displayName(),
                request.shortIntroduction(),
                request.aboutMe(),
                request.whatIOffer(),
                request.category(),
                request.profileImageUrl(),
                request.introVideoUrl(),
                request.thumbnailUrl(),
                request.availableLanguages(),
                request.lessonPrice25Amount(),
                request.lessonPrice50Amount(),
                request.availableTimeNote(),
                request.paymentMethod()
        );
        return toResponse(profile);
    }

    @Transactional
    public TutorProfileResponse submitMyProfile(Long userId) {
        TutorProfile profile = getProfileByUserId(userId);
        profile.submit();
        return toResponse(profile);
    }

    @Transactional
    public TutorProfileResponse approve(Long tutorProfileId) {
        TutorProfile profile = getProfile(tutorProfileId);
        profile.approve();
        return toResponse(profile);
    }

    @Transactional
    public TutorProfileResponse reject(Long tutorProfileId) {
        TutorProfile profile = getProfile(tutorProfileId);
        profile.reject();
        return toResponse(profile);
    }

    @Transactional(readOnly = true)
    public List<ExpertListResponse> getApprovedExperts() {
        List<TutorProfile> profiles = tutorProfileRepository.findAllByStatusAndHiddenFalseOrderByApprovedAtDesc(TutorProfileStatus.APPROVED);
        List<Long> profileIds = profiles.stream().map(TutorProfile::getId).toList();
        Map<Long, TutorReviewStats> reviewStatsByProfileId = profileIds.isEmpty()
                ? Map.of()
                : reviewRepository.findStatsByTutorProfileIds(profileIds)
                .stream()
                .collect(Collectors.toMap(TutorReviewStats::getTutorProfileId, Function.identity()));

        BigDecimal studentFeeRate = resolveStudentFeeRate();
        return profiles
                .stream()
                .map(profile -> {
                    TutorReviewStats stats = reviewStatsByProfileId.get(profile.getId());
                    return ExpertListResponse.from(
                            profile,
                            studentPrice25(profile.getLessonPrice25Amount(), studentFeeRate),
                            roundedAverageRating(stats == null ? null : stats.getAverageRating()),
                            stats == null ? 0 : Math.toIntExact(stats.getReviewCount())
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TutorProfileResponse> getPendingProfiles() {
        BigDecimal studentFeeRate = resolveStudentFeeRate();
        return tutorProfileRepository.findAllByStatusOrderBySubmittedAtAsc(TutorProfileStatus.PENDING)
                .stream()
                .map(profile -> TutorProfileResponse.from(
                        profile,
                        studentPrice25(profile.getLessonPrice25Amount(), studentFeeRate)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TutorProfileResponse getApprovedProfile(Long tutorProfileId) {
        TutorProfile profile = tutorProfileRepository.findByIdAndStatusAndHiddenFalse(tutorProfileId, TutorProfileStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
        return toResponse(profile);
    }

    private TutorProfile getProfileByUserId(Long userId) {
        return tutorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
    }

    private TutorProfile getProfile(Long tutorProfileId) {
        return tutorProfileRepository.findById(tutorProfileId)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
    }

    private Double roundedAverageRating(Double average) {
        if (average == null) {
            return 0.0;
        }
        return Math.round(average * 10.0) / 10.0;
    }

    /** Build a profile response with the student-facing 25-min price filled (#9). */
    private TutorProfileResponse toResponse(TutorProfile profile) {
        return TutorProfileResponse.from(
                profile,
                studentPrice25(profile.getLessonPrice25Amount(), resolveStudentFeeRate()));
    }

    /**
     * Active student fee rate from the runtime platform settings (#9), or null if
     * no active settings row is configured (then studentPrice is left null and the
     * frontend falls back to the raw tutor price).
     */
    private BigDecimal resolveStudentFeeRate() {
        try {
            FeePolicy policy = platformSettingsService.currentFeePolicy();
            return policy == null ? null : policy.studentFeeRate();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Student-facing 25-min price = lessonPrice25 * (1 + studentFeeRate), HALF_UP
     * scale 2. Returns null when either input is missing so the response stays a
     * pure display value (authoritative price is the checkout response).
     */
    private static BigDecimal studentPrice25(BigDecimal lessonPrice25Amount, BigDecimal studentFeeRate) {
        if (lessonPrice25Amount == null || studentFeeRate == null) {
            return null;
        }
        return lessonPrice25Amount
                .multiply(ONE.add(studentFeeRate))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
