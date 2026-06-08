package com.haru.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatIntegrationTest {

    @org.junit.jupiter.api.io.TempDir
    static java.nio.file.Path uploadDir;

    @org.springframework.test.context.DynamicPropertySource
    static void uploadProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("haru.upload-dir", () -> uploadDir.toString());
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Test
    void studentCanStartChatWithTutorAndExchangeMessages() throws Exception {
        String tutorToken = signupAndGetAccessToken("chat-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "Chat Korean Expert");
        String studentToken = signupAndGetAccessToken("chat-student@example.com");

        String startResponse = mockMvc.perform(post("/api/chats")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tutorProfileId\": %d}".formatted(tutorProfileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomType").value("DIRECT"))
                .andExpect(jsonPath("$.data.counterpartName").value("Chat Korean Expert"))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long roomId = objectMapper.readTree(startResponse).get("data").get("id").asLong();

        // Starting the same chat again returns the existing room.
        mockMvc.perform(post("/api/chats")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tutorProfileId\": %d}".formatted(tutorProfileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(roomId));

        mockMvc.perform(post("/api/chats/%d/messages".formatted(roomId))
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"안녕하세요, 수업 문의드립니다.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chatRoomId").value(roomId))
                .andExpect(jsonPath("$.data.messageType").value("TEXT"))
                .andExpect(jsonPath("$.data.body").value("안녕하세요, 수업 문의드립니다."));

        mockMvc.perform(post("/api/chats/%d/messages".formatted(roomId))
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"네, 환영합니다!\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chats/%d/messages".formatted(roomId))
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].body").value("안녕하세요, 수업 문의드립니다."))
                .andExpect(jsonPath("$.data.messages[1].body").value("네, 환영합니다!"))
                .andExpect(jsonPath("$.data.hasMore").value(false));

        // The tutor sees one unread message from the student (their own reply is not unread).
        String tutorRooms = mockMvc.perform(get("/api/chats")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode directRoom = findRoom(tutorRooms, roomId);
        assertThat(directRoom).isNotNull();
        assertThat(directRoom.get("unreadCount").asLong()).isEqualTo(1);
        assertThat(directRoom.get("counterpartName").asText()).isEqualTo("Test User");
        assertThat(directRoom.get("lastMessagePreview").asText()).isEqualTo("네, 환영합니다!");
    }

    @Test
    void chatListIncludesSystemRooms() throws Exception {
        String studentToken = signupAndGetAccessToken("chat-system-rooms@example.com");

        String response = mockMvc.perform(get("/api/chats")
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode rooms = objectMapper.readTree(response).get("data").get("rooms");

        assertThat(roomTypeNames(rooms)).contains("SYSTEM_NOTICE", "OPS");
        JsonNode noticeRoom = findRoomByType(rooms, "SYSTEM_NOTICE");
        assertThat(noticeRoom.get("counterpartName").asText()).isEqualTo("Haru 알림");
        JsonNode opsRoom = findRoomByType(rooms, "OPS");
        assertThat(opsRoom.get("counterpartName").asText()).isEqualTo("운영팀");

        // Users cannot post into the read-only notice room.
        long noticeRoomId = noticeRoom.get("id").asLong();
        mockMvc.perform(post("/api/chats/%d/messages".formatted(noticeRoomId))
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void nonParticipantsCannotReadOrWriteToRoom() throws Exception {
        String tutorToken = signupAndGetAccessToken("chat-access-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "Access Tutor");
        String studentToken = signupAndGetAccessToken("chat-access-student@example.com");
        String outsiderToken = signupAndGetAccessToken("chat-access-outsider@example.com");

        String startResponse = mockMvc.perform(post("/api/chats")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tutorProfileId\": %d}".formatted(tutorProfileId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long roomId = objectMapper.readTree(startResponse).get("data").get("id").asLong();

        mockMvc.perform(get("/api/chats/%d/messages".formatted(roomId))
                        .header("Authorization", auth(outsiderToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/chats/%d/messages".formatted(roomId))
                        .header("Authorization", auth(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"sneaky\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/chats/999999/messages")
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isNotFound());

        // Blank messages are rejected by validation.
        mockMvc.perform(post("/api/chats/%d/messages".formatted(roomId))
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markReadClearsUnreadCount() throws Exception {
        String tutorToken = signupAndGetAccessToken("chat-read-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "Read Tutor");
        String studentToken = signupAndGetAccessToken("chat-read-student@example.com");

        String startResponse = mockMvc.perform(post("/api/chats")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tutorProfileId\": %d}".formatted(tutorProfileId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long roomId = objectMapper.readTree(startResponse).get("data").get("id").asLong();

        String messageResponse = mockMvc.perform(post("/api/chats/%d/messages".formatted(roomId))
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"읽어주세요\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long messageId = objectMapper.readTree(messageResponse).get("data").get("id").asLong();

        mockMvc.perform(get("/api/chats/unread-count")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(post("/api/chats/%d/read".formatted(roomId))
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastMessageId\": %d}".formatted(messageId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/chats/unread-count")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        String tutorRooms = mockMvc.perform(get("/api/chats")
                        .header("Authorization", auth(tutorToken)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode directRoom = findRoom(tutorRooms, roomId);
        assertThat(directRoom.get("unreadCount").asLong()).isZero();

        // Non-participants cannot mark a room as read.
        String outsiderToken = signupAndGetAccessToken("chat-read-outsider@example.com");
        mockMvc.perform(post("/api/chats/%d/read".formatted(roomId))
                        .header("Authorization", auth(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastMessageId\": %d}".formatted(messageId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void messagePaginationUsesBeforeIdCursor() throws Exception {
        String tutorToken = signupAndGetAccessToken("chat-paging-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "Paging Tutor");
        String studentToken = signupAndGetAccessToken("chat-paging-student@example.com");

        String startResponse = mockMvc.perform(post("/api/chats")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tutorProfileId\": %d}".formatted(tutorProfileId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long roomId = objectMapper.readTree(startResponse).get("data").get("id").asLong();

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/chats/%d/messages".formatted(roomId))
                            .header("Authorization", auth(studentToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"message %d\"}".formatted(i)))
                    .andExpect(status().isOk());
        }

        String latestPage = mockMvc.perform(get("/api/chats/%d/messages".formatted(roomId))
                        .header("Authorization", auth(studentToken))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2))
                .andExpect(jsonPath("$.data.messages[0].body").value("message 4"))
                .andExpect(jsonPath("$.data.messages[1].body").value("message 5"))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long oldestIdOnPage = objectMapper.readTree(latestPage).get("data").get("messages").get(0).get("id").asLong();

        mockMvc.perform(get("/api/chats/%d/messages".formatted(roomId))
                        .header("Authorization", auth(studentToken))
                        .param("size", "2")
                        .param("beforeId", String.valueOf(oldestIdOnPage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].body").value("message 2"))
                .andExpect(jsonPath("$.data.messages[1].body").value("message 3"))
                .andExpect(jsonPath("$.data.hasMore").value(true));
    }

    @Test
    void participantsCanSendAttachments() throws Exception {
        String tutorToken = signupAndGetAccessToken("chat-file-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "File Tutor");
        String studentToken = signupAndGetAccessToken("chat-file-student@example.com");

        String startResponse = mockMvc.perform(post("/api/chats")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tutorProfileId\": %d}".formatted(tutorProfileId)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long roomId = objectMapper.readTree(startResponse).get("data").get("id").asLong();

        var imageFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "lesson-photo.png", "image/png", new byte[]{1, 2, 3, 4});
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/chats/%d/attachments".formatted(roomId))
                        .file(imageFile)
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageType").value("IMAGE"))
                .andExpect(jsonPath("$.data.attachmentName").value("lesson-photo.png"))
                .andExpect(jsonPath("$.data.attachmentUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.attachmentSize").value(4));

        var blockedFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "malware.exe", "application/x-msdownload", new byte[]{1});
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/chats/%d/attachments".formatted(roomId))
                        .file(blockedFile)
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isBadRequest());

        // Attachment messages show up in the history like normal messages.
        mockMvc.perform(get("/api/chats/%d/messages".formatted(roomId))
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].messageType").value("IMAGE"));
    }

    @Test
    void bookingCreatesSystemNoticeForStudentAndTutor() throws Exception {
        String tutorToken = signupAndGetAccessToken("chat-notice-tutor@example.com");
        long tutorProfileId = createApprovedTutor(tutorToken, "Notice Tutor");
        mockMvc.perform(put("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slots": [
                                    { "startAt": "2031-06-10T01:00:00Z" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
        String scheduleResponse = mockMvc.perform(get("/api/tutors/me/schedule")
                        .header("Authorization", auth(tutorToken))
                        .param("from", "2031-06-10T00:00:00Z")
                        .param("to", "2031-06-10T03:00:00Z"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long slotId = objectMapper.readTree(scheduleResponse).get("data").get("slots").get(0).get("id").asLong();

        String studentToken = signupAndGetAccessToken("chat-notice-student@example.com");
        mockMvc.perform(post("/api/payments/checkout")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tutorProfileId": %d,
                                  "lessonDurationMinutes": 25,
                                  "lessonPackCount": 1,
                                  "paymentMethod": "LEMON_SQUEEZY"
                                }
                                """.formatted(tutorProfileId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", auth(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tutorProfileId": %d,
                                  "scheduleSlotId": %d,
                                  "lessonDurationMinutes": 25
                                }
                                """.formatted(tutorProfileId, slotId)))
                .andExpect(status().isOk());

        // Both the student and the tutor receive a notice in their "Haru 알림" room.
        String studentRooms = mockMvc.perform(get("/api/chats")
                        .header("Authorization", auth(studentToken)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode studentNotice = findRoomByType(objectMapper.readTree(studentRooms).get("data").get("rooms"), "SYSTEM_NOTICE");
        assertThat(studentNotice.get("lastMessagePreview").asText()).contains("수업이 예약되었습니다");
        assertThat(studentNotice.get("unreadCount").asLong()).isEqualTo(1);

        String tutorRooms = mockMvc.perform(get("/api/chats")
                        .header("Authorization", auth(tutorToken)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode tutorNotice = findRoomByType(objectMapper.readTree(tutorRooms).get("data").get("rooms"), "SYSTEM_NOTICE");
        assertThat(tutorNotice.get("lastMessagePreview").asText()).contains("새로운 수업이 예약되었습니다");

        // System messages appear in the room with a null sender and SYSTEM type.
        long noticeRoomId = studentNotice.get("id").asLong();
        mockMvc.perform(get("/api/chats/%d/messages".formatted(noticeRoomId))
                        .header("Authorization", auth(studentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].messageType").value("SYSTEM"))
                .andExpect(jsonPath("$.data.messages[0].senderUserId").doesNotExist())
                .andExpect(jsonPath("$.data.messages[0].senderName").value("Haru"));
    }

    private JsonNode findRoom(String listResponse, long roomId) throws Exception {
        JsonNode rooms = objectMapper.readTree(listResponse).get("data").get("rooms");
        for (JsonNode room : rooms) {
            if (room.get("id").asLong() == roomId) {
                return room;
            }
        }
        return null;
    }

    private JsonNode findRoomByType(JsonNode rooms, String roomType) {
        for (JsonNode room : rooms) {
            if (room.get("roomType").asText().equals(roomType)) {
                return room;
            }
        }
        throw new AssertionError("Room with type %s was not found.".formatted(roomType));
    }

    private java.util.List<String> roomTypeNames(JsonNode rooms) {
        java.util.List<String> types = new java.util.ArrayList<>();
        rooms.forEach(room -> types.add(room.get("roomType").asText()));
        return types;
    }

    private long createApprovedTutor(String tutorToken, String displayName) throws Exception {
        String switchResponse = mockMvc.perform(post("/api/tutors/me/switch")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long tutorProfileId = objectMapper.readTree(switchResponse).get("data").get("id").asLong();

        mockMvc.perform(put("/api/tutors/me/profile")
                        .header("Authorization", auth(tutorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "%s",
                                  "shortIntroduction": "Practical Korean lessons.",
                                  "aboutMe": "I help learners speak Korean with confidence.",
                                  "whatIOffer": "Conversation and pronunciation.",
                                  "category": "KOREAN",
                                  "profileImageUrl": "https://example.com/profile.jpg",
                                  "introVideoUrl": "https://youtu.be/haru-intro",
                                  "thumbnailUrl": "https://example.com/thumb.jpg",
                                  "availableLanguages": ["Korean", "English"],
                                  "lessonPrice25Amount": 25.00,
                                  "lessonPrice50Amount": 45.00,
                                  "availableTimeNote": "Weekday evenings KST",
                                  "paymentMethod": "BANK_TRANSFER"
                                }
                                """.formatted(displayName)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/tutors/me/profile/submit")
                        .header("Authorization", auth(tutorToken)))
                .andExpect(status().isOk());

        String adminToken = createAdminAndLogin("chat-admin-%d@example.com".formatted(tutorProfileId));
        mockMvc.perform(patch("/api/admin/tutors/%d/approve".formatted(tutorProfileId))
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk());
        return tutorProfileId;
    }

    private String createAdminAndLogin(String email) throws Exception {
        signupAndGetAccessToken(email);
        UserAccount admin = userAccountRepository.findByEmail(email).orElseThrow();
        admin.addRole(Role.ADMIN);
        admin.changeActiveRole(Role.ADMIN);
        userAccountRepository.save(admin);
        return loginAndGetAccessToken(email);
    }

    private String signupAndGetAccessToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123!",
                                  "name": "Test User",
                                  "timeZone": "Asia/Seoul"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("data").get("accessToken").asText();
    }

    private String loginAndGetAccessToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123!"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("data").get("accessToken").asText();
    }

    private String auth(String accessToken) {
        return "Bearer " + accessToken;
    }
}
