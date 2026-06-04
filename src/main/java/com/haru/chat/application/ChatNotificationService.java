package com.haru.chat.application;

import com.haru.booking.domain.Booking;
import com.haru.user.domain.UserAccount;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Booking lifecycle notifications delivered into each party's "Haru 알림" chat
 * room. Called inside the booking transaction so notices roll back with it.
 */
@Service
public class ChatNotificationService {

    private final ChatService chatService;

    public ChatNotificationService(ChatService chatService) {
        this.chatService = chatService;
    }

    public void notifyBookingConfirmed(Booking booking) {
        UserAccount student = booking.getStudent();
        UserAccount tutorUser = booking.getTutorProfile().getUser();
        String tutorName = booking.getTutorProfile().getDisplayName();

        chatService.sendSystemNotice(student.getId(),
                "수업이 예약되었습니다. %s 튜터 · %s".formatted(tutorName, lessonTime(booking, student)));
        chatService.sendSystemNotice(tutorUser.getId(),
                "새로운 수업이 예약되었습니다. %s 학생 · %s".formatted(student.getName(), lessonTime(booking, tutorUser)));
    }

    public void notifyBookingCancelled(Booking booking) {
        UserAccount student = booking.getStudent();
        UserAccount tutorUser = booking.getTutorProfile().getUser();
        String tutorName = booking.getTutorProfile().getDisplayName();

        chatService.sendSystemNotice(student.getId(),
                "수업이 취소되었습니다. %s 튜터 · %s".formatted(tutorName, lessonTime(booking, student)));
        chatService.sendSystemNotice(tutorUser.getId(),
                "수업이 취소되었습니다. %s 학생 · %s".formatted(student.getName(), lessonTime(booking, tutorUser)));
    }

    private String lessonTime(Booking booking, UserAccount recipient) {
        ZoneId zone = ZoneId.of(recipient.getTimeZone());
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(zone)
                .format(booking.getStartAt()) + " (" + zone.getId() + ")";
    }
}
