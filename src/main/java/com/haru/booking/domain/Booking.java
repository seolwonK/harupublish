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

import java.math.BigDecimal;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_state", length = 30)
    private BookingCompletionState completionState;

    @Column(name = "settled", nullable = false)
    private boolean settled;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "earning_amount_usd", precision = 12, scale = 2)
    private BigDecimal earningAmountUsd;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "jitsi_provider", length = 30)
    private String jitsiProvider;

    @Column(name = "jitsi_room_name", length = 160)
    private String jitsiRoomName;

    @Column(name = "jitsi_created_at")
    private Instant jitsiCreatedAt;

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
        this.settled = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Booking reserve(UserAccount student, TutorProfile tutorProfile, TutorScheduleSlot scheduleSlot, int lessonDurationMinutes) {
        if (lessonDurationMinutes != V1_LESSON_DURATION_MINUTES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only 25-minute lessons are supported in Booking v1.");
        }
        return new Booking(student, tutorProfile, scheduleSlot, lessonDurationMinutes);
    }

    /**
     * Cancel a booking. The cancel window (hours before start) comes from the
     * runtime {@link com.haru.settings.domain.FeePolicy} rather than a hard-coded
     * 3 hours. Instead of rejecting late cancels, we now branch:
     *
     * <ul>
     *   <li><b>now &le; startAt - window</b> = normal cancel: status CANCELLED,
     *       completion state CANCELLED_NORMAL, unused credit is refundable
     *       (credit issuance is the credit track's job).</li>
     *   <li><b>now &gt; startAt - window</b> = late cancel: status CANCELLED but
     *       completion state CANCELLED_LATE, credit consumed, student refunded 0,
     *       and 100% of the lesson price accrues to the tutor.</li>
     * </ul>
     *
     * @return true when this was a late cancel (lesson consumed / tutor earns).
     */
    public boolean cancel(String reason, int cancelWindowHours, Instant now) {
        if (status != BookingStatus.RESERVED) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only reserved bookings can be cancelled.");
        }
        boolean lateCancel = now.isAfter(cancelDeadline(cancelWindowHours));
        this.status = BookingStatus.CANCELLED;
        this.completionState = lateCancel
                ? BookingCompletionState.CANCELLED_LATE
                : BookingCompletionState.CANCELLED_NORMAL;
        this.cancelReason = reason;
        touch(now);
        return lateCancel;
    }

    public Instant cancelDeadline(int cancelWindowHours) {
        return startAt.minus(Duration.ofHours(cancelWindowHours));
    }

    /**
     * Persist the completion outcome for a reserved booking whose end time has
     * passed. Idempotent at the call site via the {@code settled} flag.
     */
    public void markCompleted(Instant now) {
        if (status == BookingStatus.RESERVED) {
            this.status = BookingStatus.COMPLETED;
        }
        if (this.completionState == null) {
            this.completionState = BookingCompletionState.COMPLETED;
        }
        touch(now);
    }

    public void markNoShow(Instant now) {
        if (status == BookingStatus.RESERVED) {
            this.status = BookingStatus.NO_SHOW;
        }
        if (this.completionState == null) {
            this.completionState = BookingCompletionState.NO_SHOW;
        }
        touch(now);
    }

    /**
     * Stamp settlement bookkeeping once the earning has been written to the
     * tutor ledger. Only the {@code settled = false} guard at the call site
     * keeps this idempotent.
     */
    public void markSettled(BigDecimal earningAmountUsd, Instant now) {
        this.settled = true;
        this.settledAt = now;
        this.earningAmountUsd = earningAmountUsd;
        touch(now);
    }

    public boolean tutorEarns() {
        return completionState != null && completionState.tutorEarns();
    }

    public void assignJitsiRoom(String provider, String roomName, Instant now) {
        this.jitsiProvider = provider;
        this.jitsiRoomName = roomName;
        this.jitsiCreatedAt = now;
        touch(now);
    }

    public boolean isJoinAvailable(Instant now) {
        return effectiveStatus(now) == BookingStatus.RESERVED
                && !now.isBefore(startAt.minus(Duration.ofMinutes(10)));
    }

    public BookingStatus effectiveStatus(Instant now) {
        if (status == BookingStatus.RESERVED && !now.isBefore(endAt)) {
            return BookingStatus.COMPLETED;
        }
        return status;
    }

    private void touch(Instant now) {
        updatedAt = now;
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

    public BookingCompletionState getCompletionState() {
        return completionState;
    }

    public boolean isSettled() {
        return settled;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public BigDecimal getEarningAmountUsd() {
        return earningAmountUsd;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public String getJitsiProvider() {
        return jitsiProvider;
    }

    public String getJitsiRoomName() {
        return jitsiRoomName;
    }

    public Instant getJitsiCreatedAt() {
        return jitsiCreatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
