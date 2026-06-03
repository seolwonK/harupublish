package com.haru.settlement.application;

import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.ForbiddenException;
import com.haru.common.security.HaruPrincipal;
import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.infra.TutorProfileRepository;
import com.haru.user.domain.Role;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the calling user's own tutor profile id for {@code /api/tutors/me/**}
 * endpoints, enforcing the TUTOR role and profile ownership at the service tier
 * (path-level security only requires authentication). A non-tutor, or a user
 * with no tutor profile, gets 403.
 */
@Component
public class TutorOwnershipResolver {

    private final TutorProfileRepository tutorProfileRepository;

    public TutorOwnershipResolver(TutorProfileRepository tutorProfileRepository) {
        this.tutorProfileRepository = tutorProfileRepository;
    }

    @Transactional(readOnly = true)
    public Long requireOwnTutorProfileId(HaruPrincipal principal) {
        if (principal == null || principal.roles() == null || !principal.roles().contains(Role.TUTOR)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "Tutor role is required for this resource.");
        }
        return tutorProfileRepository.findByUserId(principal.userId())
                .map(TutorProfile::getId)
                .orElseThrow(() -> new ForbiddenException(ErrorCode.FORBIDDEN, "You do not have a tutor profile."));
    }
}
