package com.haru.schedule.domain;

import com.haru.tutor.domain.TutorProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tutor_schedule_slots")
public class TutorScheduleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_profile_id", nullable = false)
    private TutorProfile tutorProfile;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TutorScheduleSlot() {
    }

    private TutorScheduleSlot(TutorProfile tutorProfile, Instant startAt, Instant endAt) {
        this.tutorProfile = tutorProfile;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static TutorScheduleSlot of(TutorProfile tutorProfile, Instant startAt, Instant endAt) {
        return new TutorScheduleSlot(tutorProfile, startAt, endAt);
    }

    public Long getId() {
        return id;
    }

    public TutorProfile getTutorProfile() {
        return tutorProfile;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
