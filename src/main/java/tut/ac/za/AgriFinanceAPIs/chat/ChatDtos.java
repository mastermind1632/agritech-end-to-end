package tut.ac.za.AgriFinanceAPIs.chat;

import java.time.LocalDateTime;

public final class ChatDtos {
    private ChatDtos() { }

    public record MessageOut(String id, String room, String senderId, String senderName, String body, LocalDateTime createdAt) { }

    public record FarmerOut(String id, String name, String location) { }

    public record ConversationOut(String room, String otherId, String otherName, String lastBody, String lastSenderId, LocalDateTime lastAt) { }

    public record SendRequest(String body) { }
}