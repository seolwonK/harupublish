package com.haru.chat.application;

import com.haru.booking.domain.Booking;
import com.haru.user.domain.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Booking lifecycle notifications delivered into each party's "Haru 알림" chat
 * room. Message contents are resolved eagerly (while the booking session is
 * open) and the actual delivery runs AFTER the booking transaction commits, in
 * {@code sendSystemNotice}'s own REQUIRES_NEW transaction. A notice failure
 * (e.g. a concurrent first-time room creation) is logged and dropped — it can
 * never fail or roll back the booking itself.
 */
@Service
public class ChatNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ChatNotificationService.class);

    private final ChatService chatService;

    public ChatNotificationService(ChatService chatService) {
        this.chatService = chatService;
    }

    public void notifyBookingConfirmed(Booking booking) {
        UserAccount student = booking.getStudent();
        UserAccount tutorUser = booking.getTutorProfile().getUser();
        String tutorName = booking.getTutorProfile().getDisplayName();

        Long studentId = student.getId();
        Long tutorUserId = tutorUser.getId();
        String studentMessage = "수업이 예약되었습니다. %s 튜터 · %s".formatted(tutorName, lessonTime(booking, student));
        String tutorMessage = "새로운 수업이 예약되었습니다. %s 학생 · %s".formatted(student.getName(), lessonTime(booking, tutorUser));
        // 예약이 성사되면 학생-튜터 1:1 방을 만들어 양쪽 채팅 목록에 서로가 뜨게 한다.
        String directMessage = "수업이 예약되었습니다. %s 튜터 · %s 학생 · %s"
                .formatted(tutorName, student.getName(), lessonTime(booking, tutorUser));

        afterCommit(() -> {
            runQuietly("direct room ensure", () -> chatService.ensureDirectRoom(studentId, tutorUserId, directMessage));
            sendQuietly(studentId, studentMessage);
            sendQuietly(tutorUserId, tutorMessage);
        });
    }

    public void notifyBookingCancelled(Booking booking) {
        UserAccount student = booking.getStudent();
        UserAccount tutorUser = booking.getTutorProfile().getUser();
        String tutorName = booking.getTutorProfile().getDisplayName();

        Long studentId = student.getId();
        Long tutorUserId = tutorUser.getId();
        String studentMessage = "수업이 취소되었습니다. %s 튜터 · %s".formatted(tutorName, lessonTime(booking, student));
        String tutorMessage = "수업이 취소되었습니다. %s 학생 · %s".formatted(student.getName(), lessonTime(booking, tutorUser));

        afterCommit(() -> {
            sendQuietly(studentId, studentMessage);
            sendQuietly(tutorUserId, tutorMessage);
        });
    }

    /** Run after the surrounding transaction commits (never on rollback). */
    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /** Notices are best-effort: never let them turn a committed booking into a 500. */
    private void sendQuietly(Long userId, String body) {
        try {
            chatService.sendSystemNotice(userId, body);
        } catch (RuntimeException exception) {
            log.warn("System notice delivery failed for user {}: {}", userId, exception.getMessage());
        }
    }

    /** Best-effort side task (e.g. direct-room creation): log and swallow failures. */
    private void runQuietly(String what, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException exception) {
            log.warn("{} failed: {}", what, exception.getMessage());
        }
    }

    private String lessonTime(Booking booking, UserAccount recipient) {
        ZoneId zone = ZoneId.of(recipient.getTimeZone());
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(zone)
                .format(booking.getStartAt()) + " (" + zone.getId() + ")";
    }
}
