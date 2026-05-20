package com.haru.booking.domain;

import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.schedule.domain.TutorScheduleSlot;
import com.haru.tutor.domain.TutorProfile;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    public static final int V1_LESSON_DURATION_MINUTES = 25;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_user_id", nullable = false)
    private UserAccount student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_profile_id", nullable = false)
    private TutorProfile tutorProfile;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_slot_id", nullable = false, unique = true)
    private TutorScheduleSlot scheduleSlot;

    @Column(name = "lesson_duration_minutes", nullable = false)
    private int lessonDurationMinutes;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {
    }

    private Booking(UserAccount student, TutorProfile tutorProfile, TutorScheduleSlot scheduleSlot, int lessonDurationMinutes) {
        this.student = student;
        this.tutorProfile = tutorProfile;
        this.scheduleSlot = scheduleSlot;
        this.lessonDurationMinutes = lessonDurationMinutes;
        this.startAt = scheduleSlot.getStartAt();
        this.endAt = scheduleSlot.getStartAt().plus(Duration.ofMinutes(lessonDurationMinutes));
        this.status = BookingStatus.RESERVED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Booking reserve(UserAccount student, TutorProfile tutorProfile, TutorScheduleSlot scheduleSlot, int lessonDurationMinutes) {
        if (lessonDurationMinutes != V1_LESSON_DURATION_MINUTES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only 25-minute lessons are supported in Booking v1.");
        }
        return new Booking(student, tutorProfile, scheduleSlot, lessonDurationMinutes);
    }

    public void cancel(String reason, Instant now) {
        if (status != BookingStatus.RESERVED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only reserved bookings can be cancelled.");
        }
        if (now.isAfter(startAt.minus(Duration.ofHours(3)))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Booking can be cancelled until 3 hours before lesson start.");
        }
        this.status = BookingStatus.CANCELLED;
        this.cancelReason = reason;
        touch();
    }

    public boolean isJoinAvailable(Instant now) {
        return status == BookingStatus.RESERVED && !now.isBefore(startAt.minus(Duration.ofMinutes(10)));
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UserAccount getStudent() {
        return student;
    }

    public TutorProfile getTutorProfile() {
        return tutorProfile;
    }

    public TutorScheduleSlot getScheduleSlot() {
        return scheduleSlot;
    }

    public int getLessonDurationMinutes() {
        return lessonDurationMinutes;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
