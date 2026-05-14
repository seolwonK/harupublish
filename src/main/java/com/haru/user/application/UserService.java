package com.haru.user.application;

import com.haru.common.exception.NotFoundException;
import com.haru.tutor.domain.TutorProfileStatus;
import com.haru.tutor.infra.TutorProfileRepository;
import com.haru.user.api.dto.ChangeActiveRoleRequest;
import com.haru.user.api.dto.UpdateMyProfileRequest;
import com.haru.user.api.dto.UserMeResponse;
import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;
    private final TutorProfileRepository tutorProfileRepository;

    public UserService(UserAccountRepository userAccountRepository, TutorProfileRepository tutorProfileRepository) {
        this.userAccountRepository = userAccountRepository;
        this.tutorProfileRepository = tutorProfileRepository;
    }

    @Transactional(readOnly = true)
    public UserAccount getActiveUser(Long userId) {
        UserAccount user = userAccountRepository.findWithRolesById(userId)
                .orElseThrow(() -> new NotFoundException("User was not found."));
        user.ensureActive();
        return user;
    }

    @Transactional(readOnly = true)
    public UserMeResponse getMe(Long userId) {
        UserAccount user = getActiveUser(userId);
        return UserMeResponse.from(user, getTutorProfileStatus(userId));
    }

    @Transactional
    public UserMeResponse updateMe(Long userId, UpdateMyProfileRequest request) {
        UserAccount user = getActiveUser(userId);
        user.updateProfile(request.name(), request.mobileNumber(), request.timeZone());
        return UserMeResponse.from(user, getTutorProfileStatus(userId));
    }

    @Transactional
    public UserMeResponse changeActiveRole(Long userId, ChangeActiveRoleRequest request) {
        UserAccount user = getActiveUser(userId);
        user.changeActiveRole(request.activeRole());
        return UserMeResponse.from(user, getTutorProfileStatus(userId));
    }

    private TutorProfileStatus getTutorProfileStatus(Long userId) {
        return tutorProfileRepository.findStatusByUserId(userId).orElse(null);
    }
}
