package com.haru.tutor.application;

import com.haru.common.exception.NotFoundException;
import com.haru.review.infra.ReviewRepository;
import com.haru.review.infra.ReviewRepository.TutorReviewStats;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TutorService {

    private final UserService userService;
    private final TutorProfileRepository tutorProfileRepository;
    private final ReviewRepository reviewRepository;

    public TutorService(UserService userService, TutorProfileRepository tutorProfileRepository, ReviewRepository reviewRepository) {
        this.userService = userService;
        this.tutorProfileRepository = tutorProfileRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public TutorProfileResponse switchToTutor(Long userId) {
        UserAccount user = userService.getActiveUser(userId);
        user.addRole(Role.TUTOR);
        user.changeActiveRole(Role.TUTOR);

        TutorProfile profile = tutorProfileRepository.findByUserId(userId)
                .orElseGet(() -> tutorProfileRepository.save(TutorProfile.draft(user)));
        return TutorProfileResponse.from(profile);
    }

    @Transactional(readOnly = true)
    public TutorProfileResponse getMyProfile(Long userId) {
        return TutorProfileResponse.from(getProfileByUserId(userId));
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
        return TutorProfileResponse.from(profile);
    }

    @Transactional
    public TutorProfileResponse submitMyProfile(Long userId) {
        TutorProfile profile = getProfileByUserId(userId);
        profile.submit();
        return TutorProfileResponse.from(profile);
    }

    @Transactional
    public TutorProfileResponse approve(Long tutorProfileId) {
        TutorProfile profile = getProfile(tutorProfileId);
        profile.approve();
        return TutorProfileResponse.from(profile);
    }

    @Transactional
    public TutorProfileResponse reject(Long tutorProfileId) {
        TutorProfile profile = getProfile(tutorProfileId);
        profile.reject();
        return TutorProfileResponse.from(profile);
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

        return profiles
                .stream()
                .map(profile -> {
                    TutorReviewStats stats = reviewStatsByProfileId.get(profile.getId());
                    return ExpertListResponse.from(
                            profile,
                            roundedAverageRating(stats == null ? null : stats.getAverageRating()),
                            stats == null ? 0 : Math.toIntExact(stats.getReviewCount())
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TutorProfileResponse> getPendingProfiles() {
        return tutorProfileRepository.findAllByStatusOrderBySubmittedAtAsc(TutorProfileStatus.PENDING)
                .stream()
                .map(TutorProfileResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TutorProfileResponse getApprovedProfile(Long tutorProfileId) {
        TutorProfile profile = tutorProfileRepository.findByIdAndStatusAndHiddenFalse(tutorProfileId, TutorProfileStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
        return TutorProfileResponse.from(profile);
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
}
