package tut.ac.za.AgriFinanceAPIs.chat;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.chat.ChatDtos.ConversationOut;
import tut.ac.za.AgriFinanceAPIs.chat.ChatDtos.FarmerOut;
import tut.ac.za.AgriFinanceAPIs.chat.ChatDtos.MessageOut;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.grouporder.GroupOrderRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private static final int MAX_LEN = 1000;

    private final ChatMessageRepository messages;
    private final FarmerRepository farmers;
    private final GroupOrderRepository orders;

    public ChatService(ChatMessageRepository messages, FarmerRepository farmers, GroupOrderRepository orders) {
        this.messages = messages;
        this.farmers = farmers;
        this.orders = orders;
    }

    /** Other farmers you can message. Only id, name and location are exposed (never contact or password). */
    public List<FarmerOut> directory(String me) {
        return farmers.findAll().stream()
                .filter(f -> !f.getId().equals(me))
                .sorted(Comparator.comparing((Farmer f) -> f.getName() == null ? "" : f.getName().toLowerCase()))
                .map(f -> new FarmerOut(f.getId(), f.getName(), f.getLocation()))
                .toList();
    }

    public List<MessageOut> history(String me, String room) {
        checkAccess(me, room);
        List<ChatMessage> asc = new ArrayList<>(messages.findByRoomOrderByCreatedAtDesc(room, PageRequest.of(0, 100)));
        Collections.reverse(asc);
        return toOut(asc);
    }

    @Transactional
    public MessageOut send(String me, String room, String body) {
        checkAccess(me, room);
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) throw bad("Message cannot be empty");
        if (text.length() > MAX_LEN) throw bad("Message is too long (max " + MAX_LEN + " characters)");
        ChatMessage m = new ChatMessage();
        m.setRoom(room);
        m.setSenderId(me);
        m.setBody(text);
        return toOut(List.of(messages.save(m))).get(0);
    }

    /** One entry per direct-message conversation you are part of, newest first. */
    public List<ConversationOut> conversations(String me) {
        Map<String, ChatMessage> lastByRoom = new LinkedHashMap<>();
        for (ChatMessage m : messages.findDirectFor(me, PageRequest.of(0, 500))) {
            lastByRoom.putIfAbsent(m.getRoom(), m);
        }
        List<ConversationOut> out = new ArrayList<>();
        for (Map.Entry<String, ChatMessage> e : lastByRoom.entrySet()) {
            String[] p = e.getKey().split(":");
            if (p.length != 3 || !(p[1].equals(me) || p[2].equals(me))) continue;
            String other = p[1].equals(me) ? p[2] : p[1];
            String name = farmers.findById(other).map(Farmer::getName).orElse("Farmer");
            ChatMessage m = e.getValue();
            out.add(new ConversationOut(e.getKey(), other, name, m.getBody(), m.getSenderId(), m.getCreatedAt()));
        }
        return out;
    }

    private void checkAccess(String me, String room) {
        if (room == null || room.length() > 120) throw bad("Invalid room");
        if (room.equals("community")) return;
        if (room.startsWith("order:")) {
            if (!orders.existsById(room.substring(6))) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group order not found");
            }
            return;
        }
        if (room.startsWith("dm:")) {
            String[] p = room.split(":");
            if (p.length != 3 || p[1].equals(p[2]) || p[1].compareTo(p[2]) > 0) throw bad("Invalid room");
            if (!p[1].equals(me) && !p[2].equals(me)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not part of this conversation");
            }
            String other = p[1].equals(me) ? p[2] : p[1];
            if (!farmers.existsById(other)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found");
            return;
        }
        throw bad("Unknown room");
    }

    private List<MessageOut> toOut(List<ChatMessage> list) {
        Map<String, String> names = new HashMap<>();
        for (ChatMessage m : list) {
            names.computeIfAbsent(m.getSenderId(), id -> farmers.findById(id).map(Farmer::getName).orElse("Farmer"));
        }
        List<MessageOut> out = new ArrayList<>();
        for (ChatMessage m : list) {
            out.add(new MessageOut(m.getId(), m.getRoom(), m.getSenderId(), names.get(m.getSenderId()), m.getBody(), m.getCreatedAt()));
        }
        return out;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}