package com.haru.booking.application;

import com.haru.booking.api.dto.BookingJoinResponse;
import com.haru.booking.api.dto.BookingListResponse;
import com.haru.booking.api.dto.BookingResponse;
import com.haru.booking.api.dto.CancelBookingRequest;
import com.haru.booking.api.dto.CreateBookingRequest;
import com.haru.booking.domain.Booking;
import com.haru.booking.domain.BookingStatus;
import com.haru.booking.infra.BookingRepository;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.ForbiddenException;
import com.haru.common.exception.NotFoundException;
import com.haru.meeting.application.JitsiRoomNameGenerator;
import com.haru.meeting.application.JitsiTokenService;
import com.haru.schedule.domain.TutorScheduleSlot;
import com.haru.schedule.infra.TutorScheduleSlotRepository;
import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.domain.TutorProfileStatus;
import com.haru.tutor.infra.TutorProfileRepository;
import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class BookingService {

    private final UserAccountRepository userAccountRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final TutorScheduleSlotRepository tutorScheduleSlotRepository;
    private final BookingRepository bookingRepository;
    private final JitsiRoomNameGenerator jitsiRoomNameGenerator;
    private final JitsiTokenService jitsiTokenService;

    public BookingService(
            UserAccountRepository userAccountRepository,
            TutorProfileRepository tutorProfileRepository,
            TutorScheduleSlotRepository tutorScheduleSlotRepository,
            BookingRepository bookingRepository,
            JitsiRoomNameGenerator jitsiRoomNameGenerator,
            JitsiTokenService jitsiTokenService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tutorProfileRepository = tutorProfileRepository;
        this.tutorScheduleSlotRepository = tutorScheduleSlotRepository;
        this.bookingRepository = bookingRepository;
        this.jitsiRoomNameGenerator = jitsiRoomNameGenerator;
        this.jitsiTokenService = jitsiTokenService;
    }

    @Transactional
    public BookingResponse create(Long studentUserId, CreateBookingRequest request) {
        if (request.lessonDurationMinutes() == null || request.lessonDurationMinutes() != Booking.V1_LESSON_DURATION_MINUTES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only 25-minute lessons are supported in Booking v1.");
        }
        UserAccount student = userAccountRepository.findWithRolesById(studentUserId)
                .orElseThrow(() -> new NotFoundException("User was not found."));
        TutorProfile tutorProfile = tutorProfileRepository.findByIdAndStatus(request.tutorProfileId(), TutorProfileStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
        TutorScheduleSlot slot = tutorScheduleSlotRepository.findById(request.scheduleSlotId())
                .orElseThrow(() -> new NotFoundException("Schedule slot was not found."));

        if (!slot.getTutorProfile().getId().equals(tutorProfile.getId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Schedule slot does not belong to the tutor profile.");
        }
        if (tutorProfile.getUser().getId().equals(studentUserId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Tutors cannot book their own lessons.");
        }
        if (bookingRepository.existsByScheduleSlotIdAndStatus(slot.getId(), BookingStatus.RESERVED)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Schedule slot is already booked.");
        }

        Booking booking = bookingRepository.save(Booking.reserve(student, tutorProfile, slot, request.lessonDurationMinutes()));
        booking.assignJitsiRoom(
                JitsiTokenService.PROVIDER,
                jitsiRoomNameGenerator.createRoomName(booking.getId()),
                Instant.now()
        );
        return BookingResponse.from(booking, Instant.now());
    }

    @Transactional(readOnly = true)
    public BookingListResponse getMyBookings(Long userId) {
        UserAccount user = userAccountRepository.findWithRolesById(userId)
                .orElseThrow(() -> new NotFoundException("User was not found."));
        List<Booking> bookings = user.getActiveRole() == Role.TUTOR
                ? tutorProfileRepository.findByUserId(userId)
                        .map(profile -> bookingRepository.findAllByTutorProfileIdOrderByStartAtAsc(profile.getId()))
                        .orElseGet(List::of)
                : bookingRepository.findAllByStudentIdOrderByStartAtAsc(userId);
        return BookingListResponse.from(bookings, Instant.now());
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long userId, Long bookingId) {
        Booking booking = getBookingWithAccess(userId, bookingId);
        return BookingResponse.from(booking, Instant.now());
    }

    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId, CancelBookingRequest request) {
        Booking booking = getBookingWithAccess(userId, bookingId);
        booking.cancel(request == null ? null : request.reason(), Instant.now());
        return BookingResponse.from(booking, Instant.now());
    }

    @Transactional(readOnly = true)
    public BookingJoinResponse join(Long userId, Long bookingId) {
        Booking booking = getBookingWithAccess(userId, bookingId);
        Instant now = Instant.now();
        if (!booking.isJoinAvailable(now)) {
            return BookingJoinResponse.unavailable(booking.getId(), "Lesson can be joined 10 minutes before start.");
        }

        boolean moderator = booking.getTutorProfile().getUser().getId().equals(userId);
        UserAccount participant = moderator ? booking.getTutorProfile().getUser() : booking.getStudent();
        return BookingJoinResponse.available(
                booking.getId(),
                jitsiTokenService.createJoinPayload(booking, participant, moderator, now)
        );
    }

    private Booking getBookingWithAccess(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findWithDetailsById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking was not found."));
        if (!canAccess(userId, booking)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN, "You do not have access to this booking.");
        }
        return booking;
    }

    private boolean canAccess(Long userId, Booking booking) {
        return booking.getStudent().getId().equals(userId)
                || booking.getTutorProfile().getUser().getId().equals(userId);
    }
}
