package com.haru.tutor.application;

import com.haru.common.exception.NotFoundException;
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

@Service
public class TutorService {

    private final UserService userService;
    private final TutorProfileRepository tutorProfileRepository;

    public TutorService(UserService userService, TutorProfileRepository tutorProfileRepository) {
        this.userService = userService;
        this.tutorProfileRepository = tutorProfileRepository;
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
                request.lessonPriceAmount(),
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
        return tutorProfileRepository.findAllByStatusOrderByApprovedAtDesc(TutorProfileStatus.APPROVED)
                .stream()
                .map(ExpertListResponse::from)
                .toList();
    }

    private TutorProfile getProfileByUserId(Long userId) {
        return tutorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
    }

    private TutorProfile getProfile(Long tutorProfileId) {
        return tutorProfileRepository.findById(tutorProfileId)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
    }
}
