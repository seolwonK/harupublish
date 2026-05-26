package com.haru.schedule.application;

import com.haru.booking.domain.BookingStatus;
import com.haru.booking.infra.BookingRepository;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.NotFoundException;
import com.haru.schedule.api.dto.TutorScheduleRequest;
import com.haru.schedule.api.dto.TutorScheduleResponse;
import com.haru.schedule.domain.TutorScheduleSlot;
import com.haru.schedule.infra.TutorScheduleSlotRepository;
import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.domain.TutorProfileStatus;
import com.haru.tutor.infra.TutorProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class TutorScheduleService {

    private static final Duration SLOT_DURATION = Duration.ofMinutes(30);
    private static final int MAX_SLOT_COUNT = 200;

    private final TutorProfileRepository tutorProfileRepository;
    private final TutorScheduleSlotRepository tutorScheduleSlotRepository;
    private final BookingRepository bookingRepository;

    public TutorScheduleService(
            TutorProfileRepository tutorProfileRepository,
            TutorScheduleSlotRepository tutorScheduleSlotRepository,
            BookingRepository bookingRepository
    ) {
        this.tutorProfileRepository = tutorProfileRepository;
        this.tutorScheduleSlotRepository = tutorScheduleSlotRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public TutorScheduleResponse replaceMySchedule(Long userId, TutorScheduleRequest request) {
        TutorProfile profile = getProfileByUserId(userId);
        List<Instant> startTimes = validateAndNormalizeSlots(request);

        tutorScheduleSlotRepository.deleteAllByTutorProfileId(profile.getId());
        List<TutorScheduleSlot> slots = tutorScheduleSlotRepository.saveAll(
                startTimes.stream()
                        .map(startAt -> TutorScheduleSlot.of(profile, startAt, startAt.plus(SLOT_DURATION)))
                        .toList()
        );
        return TutorScheduleResponse.from(slots, List.of());
    }

    @Transactional(readOnly = true)
    public TutorScheduleResponse getMySchedule(Long userId, Instant from, Instant to) {
        TutorProfile profile = getProfileByUserId(userId);
        return buildScheduleResponse(profile.getId(), from, to);
    }

    @Transactional(readOnly = true)
    public TutorScheduleResponse getPublicSchedule(Long tutorProfileId, Instant from, Instant to) {
        TutorProfile profile = tutorProfileRepository.findByIdAndStatusAndHiddenFalse(tutorProfileId, TutorProfileStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
        return buildScheduleResponse(profile.getId(), from, to);
    }

        private TutorScheduleResponse buildScheduleResponse(Long tutorProfileId, Instant from, Instant to) {
        List<TutorScheduleSlot> slots = findSlots(tutorProfileId, from, to);
        Set<Long> bookedSlotIds = Set.copyOf(
            bookingRepository.findScheduleSlotIdsByTutorProfileIdAndStatusAndStartAtGreaterThanEqualAndStartAtLessThan(
                tutorProfileId,
                BookingStatus.RESERVED,
                from,
                to
            )
        );
        return TutorScheduleResponse.from(slots, bookedSlotIds);
        }

    private List<TutorScheduleSlot> findSlots(Long tutorProfileId, Instant from, Instant to) {
        validateRange(from, to);
        return tutorScheduleSlotRepository.findAllByTutorProfileIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                tutorProfileId,
                from,
                to
        );
    }

    private TutorProfile getProfileByUserId(Long userId) {
        return tutorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
    }

    private List<Instant> validateAndNormalizeSlots(TutorScheduleRequest request) {
        if (request == null || request.slots() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "slots is required.");
        }
        if (request.slots().size() > MAX_SLOT_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "slots can contain up to 200 items.");
        }

        TreeSet<Instant> startTimes = new TreeSet<>();
        request.slots().forEach(slot -> {
            if (slot == null || slot.startAt() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "slot startAt is required.");
            }
            validateSlotStart(slot.startAt());
            startTimes.add(slot.startAt());
        });
        return List.copyOf(startTimes);
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "from and to are required.");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "from must be before to.");
        }
    }

    private void validateSlotStart(Instant startAt) {
        if (startAt.getNano() != 0 || Math.floorMod(startAt.getEpochSecond(), SLOT_DURATION.toSeconds()) != 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "startAt must be aligned to a 30-minute UTC slot.");
        }
    }
}
