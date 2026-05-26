package com.haru.tutor.infra;

import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.domain.TutorProfileStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TutorProfileRepository extends JpaRepository<TutorProfile, Long> {

    @EntityGraph(attributePaths = {"user", "user.roles"})
    Optional<TutorProfile> findByUserId(Long userId);

    @Query("select profile.status from TutorProfile profile where profile.user.id = :userId")
    Optional<TutorProfileStatus> findStatusByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "user.roles"})
    List<TutorProfile> findAllByStatusAndHiddenFalseOrderByApprovedAtDesc(TutorProfileStatus status);

    @EntityGraph(attributePaths = {"user", "user.roles"})
    List<TutorProfile> findAllByStatusOrderBySubmittedAtAsc(TutorProfileStatus status);

    @EntityGraph(attributePaths = {"user", "user.roles"})
    Optional<TutorProfile> findByIdAndStatusAndHiddenFalse(Long id, TutorProfileStatus status);
}
