package com.haru.dev.application;

import com.haru.booking.api.dto.BookingResponse;
import com.haru.booking.api.dto.CreateBookingRequest;
import com.haru.booking.application.BookingService;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.NotFoundException;
import com.haru.dev.api.dto.AddSlotsRequest;
import com.haru.dev.api.dto.CreateTestBookingRequest;
import com.haru.dev.api.dto.CreateTestStudentRequest;
import com.haru.dev.api.dto.CreateTestTutorRequest;
import com.haru.dev.api.dto.CreateTestTutorResponse;
import com.haru.dev.api.dto.CreatedLessonResponse;
import com.haru.dev.api.dto.CreatedSlotResponse;
import com.haru.dev.api.dto.TestAccountResponse;
import com.haru.payment.domain.Payment;
import com.haru.payment.domain.PaymentMethod;
import com.haru.payment.infra.PaymentRepository;
import com.haru.schedule.domain.TutorScheduleSlot;
import com.haru.schedule.infra.TutorScheduleSlotRepository;
import com.haru.settings.application.PlatformSettingsService;
import com.haru.settings.domain.FeePolicy;
import com.haru.tutor.domain.TutorCategory;
import com.haru.tutor.domain.TutorProfile;
import com.haru.tutor.domain.TutorProfileStatus;
import com.haru.tutor.infra.TutorProfileRepository;
import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Test-data generator for local/test-MVP environments. It composes the real
 * domain flows (approve tutor, record a PAID payment, reserve a booking) so the
 * data it creates is indistinguishable from data produced through the UI — the
 * only shortcut is skipping the Lemon Squeezy round-trip by stamping the payment
 * PAID directly. Gated by {@code haru.dev.enabled} at the controller.
 */
@Service
public class DevService {

    private static final String DEFAULT_PASSWORD = "test1234";
    private static final String DEFAULT_TIME_ZONE = "Asia/Seoul";
    private static final Duration SLOT_DURATION = Duration.ofMinutes(30);
    /** First auto-generated lesson starts this soon, so it is joinable right away (join opens 10 min before). */
    private static final Duration FIRST_LESSON_LEAD = Duration.ofMinutes(5);
    /** Spacing between consecutive auto-generated lessons. */
    private static final Duration LESSON_SPACING = Duration.ofMinutes(30);
    private static final int MAX_AUTO_LESSONS = 50;
    private static final BigDecimal DEFAULT_PRICE_25 = new BigDecimal("20.00");
    private static final BigDecimal DEFAULT_PRICE_50 = new BigDecimal("38.00");

    private final UserAccountRepository userAccountRepository;
    private final TutorProfileRepository tutorProfileRepository;
    private final TutorScheduleSlotRepository tutorScheduleSlotRepository;
    private final PaymentRepository paymentRepository;
    private final PlatformSettingsService platformSettingsService;
    private final BookingService bookingService;
    private final PasswordEncoder passwordEncoder;

    public DevService(
            UserAccountRepository userAccountRepository,
            TutorProfileRepository tutorProfileRepository,
            TutorScheduleSlotRepository tutorScheduleSlotRepository,
            PaymentRepository paymentRepository,
            PlatformSettingsService platformSettingsService,
            BookingService bookingService,
            PasswordEncoder passwordEncoder
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tutorProfileRepository = tutorProfileRepository;
        this.tutorScheduleSlotRepository = tutorScheduleSlotRepository;
        this.paymentRepository = paymentRepository;
        this.platformSettingsService = platformSettingsService;
        this.bookingService = bookingService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public CreateTestTutorResponse createTutor(CreateTestTutorRequest request) {
        String name = textOr(request.name(), "Test Tutor");
        TutorCategory category = request.category() != null ? request.category() : TutorCategory.KOREAN;
        BigDecimal price25 = positiveOr(request.lessonPrice25Amount(), DEFAULT_PRICE_25);
        BigDecimal price50 = positiveOr(request.lessonPrice50Amount(), DEFAULT_PRICE_50);
        List<String> languages = request.availableLanguages() != null && !request.availableLanguages().isEmpty()
                ? request.availableLanguages()
                : List.of("Korean", "English");

        Provisioned tutorProv = provision(request.email(), request.password(), name, "dev-tutor");
        UserAccount tutorUser = tutorProv.account();
        tutorUser.addRole(Role.TUTOR);
        tutorUser.changeActiveRole(Role.TUTOR);
        userAccountRepository.save(tutorUser);

        TutorProfile profile = TutorProfile.draft(tutorUser);
        profile.update(
                name,
                name + " — 테스트용 튜터입니다.",
                "개발 테스트 목적으로 생성된 튜터 프로필입니다.",
                "테스트 한국어/문화 수업을 제공합니다.",
                category,
                null,
                null,
                null,
                languages,
                price25,
                price50,
                "테스트 가능 시간 (개발용)",
                "Lemon Squeezy"
        );
        profile.submit();
        profile.approve();
        profile = tutorProfileRepository.save(profile);

        List<CreatedSlotResponse> openSlots = new ArrayList<>();
        if (request.availabilitySlots() != null) {
            for (Instant start : request.availabilitySlots()) {
                TutorScheduleSlot slot = createSlotIfAbsent(profile, start);
                openSlots.add(slotResponse(slot));
            }
        }

        List<Instant> lessonStarts = resolveLessonStarts(request);
        List<CreatedLessonResponse> bookedLessons = new ArrayList<>();
        TestAccountResponse studentAccount = null;
        if (!lessonStarts.isEmpty()) {
            Provisioned studentProv = provision(null, null, name + " 학생", "dev-student");
            userAccountRepository.saveAndFlush(studentProv.account());
            studentAccount = accountResponse(studentProv);
            for (Instant start : lessonStarts) {
                bookedLessons.add(createBookedLesson(profile, studentProv.account(), start));
            }
        }

        return new CreateTestTutorResponse(
                profile.getId(),
                accountResponse(tutorProv),
                studentAccount,
                openSlots,
                bookedLessons
        );
    }

    @Transactional
    public TestAccountResponse createStudent(CreateTestStudentRequest request) {
        Provisioned prov = provision(
                request.email(),
                request.password(),
                textOr(request.name(), "Test Student"),
                "dev-student"
        );
        userAccountRepository.save(prov.account());
        return accountResponse(prov);
    }

    @Transactional
    public List<CreatedSlotResponse> addSlots(Long tutorProfileId, AddSlotsRequest request) {
        TutorProfile profile = tutorProfileRepository.findById(tutorProfileId)
                .orElseThrow(() -> new NotFoundException("Tutor profile was not found."));
        if (request == null || request.startAts() == null || request.startAts().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "startAts is required.");
        }
        List<CreatedSlotResponse> created = new ArrayList<>();
        for (Instant start : request.startAts()) {
            created.add(slotResponse(createSlotIfAbsent(profile, start)));
        }
        return created;
    }

    @Transactional
    public CreatedLessonResponse createBooking(CreateTestBookingRequest request) {
        if (request == null || request.tutorProfileId() == null || request.startAt() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "tutorProfileId and startAt are required.");
        }
        TutorProfile profile = tutorProfileRepository
                .findByIdAndStatusAndHiddenFalse(request.tutorProfileId(), TutorProfileStatus.APPROVED)
                .orElseThrow(() -> new NotFoundException("Approved tutor profile was not found."));

        UserAccount student;
        if (request.studentUserId() != null) {
            student = userAccountRepository.findById(request.studentUserId())
                    .orElseThrow(() -> new NotFoundException("Student was not found."));
        } else {
            Provisioned prov = provision(null, null, "Dev Student", "dev-student");
            student = userAccountRepository.saveAndFlush(prov.account());
        }
        return createBookedLesson(profile, student, request.startAt());
    }

    /** Slot + PAID payment + reserved booking, reusing the real BookingService flow. */
    private CreatedLessonResponse createBookedLesson(TutorProfile profile, UserAccount student, Instant rawStart) {
        TutorScheduleSlot slot = createSlotIfAbsent(profile, rawStart);

        FeePolicy feePolicy = platformSettingsService.currentFeePolicy();
        Payment payment = Payment.checkout(
                student,
                profile,
                Payment.LESSON_DURATION_25,
                1,
                profile.getLessonPrice25Amount(),
                PaymentMethod.LEMON_SQUEEZY,
                feePolicy
        );
        payment.markPaid();
        paymentRepository.saveAndFlush(payment);

        BookingResponse booking = bookingService.create(
                student.getId(),
                new CreateBookingRequest(profile.getId(), slot.getId(), Payment.LESSON_DURATION_25)
        );
        return new CreatedLessonResponse(
                booking.id(),
                slot.getId(),
                booking.startAt(),
                booking.endAt(),
                booking.status() == null ? null : booking.status().name()
        );
    }

    /** Reuse an existing slot at this start (per tutor) or create a new 30-min one. */
    private TutorScheduleSlot createSlotIfAbsent(TutorProfile profile, Instant rawStart) {
        Instant start = truncateToMinute(rawStart);
        Instant end = start.plus(SLOT_DURATION);
        return tutorScheduleSlotRepository
                .findAllByTutorProfileIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        profile.getId(), start, start.plusSeconds(1))
                .stream()
                .findFirst()
                .orElseGet(() -> tutorScheduleSlotRepository.saveAndFlush(TutorScheduleSlot.of(profile, start, end)));
    }

    private List<Instant> resolveLessonStarts(CreateTestTutorRequest request) {
        if (request.bookedLessons() != null && !request.bookedLessons().isEmpty()) {
            return request.bookedLessons();
        }
        int count = request.autoLessonCount() != null ? request.autoLessonCount() : 0;
        if (count <= 0) {
            return List.of();
        }
        count = Math.min(count, MAX_AUTO_LESSONS);
        // Current-time based: first lesson is joinable now, the rest are spaced out.
        Instant base = truncateToMinute(Instant.now().plus(FIRST_LESSON_LEAD));
        List<Instant> starts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            starts.add(base.plus(LESSON_SPACING.multipliedBy(i)));
        }
        return starts;
    }

    private Provisioned provision(String email, String password, String name, String emailPrefix) {
        String resolvedEmail = resolveEmail(email, emailPrefix);
        if (userAccountRepository.existsByEmail(resolvedEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already exists: " + resolvedEmail);
        }
        String resolvedPassword = textOr(password, DEFAULT_PASSWORD);
        if (resolvedPassword.length() < 8) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "password must be at least 8 characters.");
        }
        UserAccount account = UserAccount.student(
                resolvedEmail,
                passwordEncoder.encode(resolvedPassword),
                textOr(name, "Test User"),
                DEFAULT_TIME_ZONE
        );
        return new Provisioned(account, resolvedPassword);
    }

    private String resolveEmail(String email, String prefix) {
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase();
        }
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@haru.test";
    }

    /** Drop seconds/nanos so test slot times are clean minutes (keeps them near "now"). */
    private Instant truncateToMinute(Instant raw) {
        Instant base = raw != null ? raw : Instant.now();
        return base.truncatedTo(ChronoUnit.MINUTES);
    }

    private CreatedSlotResponse slotResponse(TutorScheduleSlot slot) {
        return new CreatedSlotResponse(slot.getId(), slot.getStartAt(), slot.getEndAt());
    }

    private TestAccountResponse accountResponse(Provisioned prov) {
        UserAccount account = prov.account();
        return new TestAccountResponse(
                account.getId(),
                account.getEmail(),
                prov.password(),
                account.getName(),
                account.getActiveRole().name()
        );
    }

    private static String textOr(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static BigDecimal positiveOr(BigDecimal value, BigDecimal fallback) {
        return value != null && value.signum() > 0 ? value : fallback;
    }

    /** A freshly built (unsaved or saved) account paired with its plaintext password. */
    private record Provisioned(UserAccount account, String password) {
    }
}
