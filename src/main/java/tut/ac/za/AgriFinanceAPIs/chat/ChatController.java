package tut.ac.za.AgriFinanceAPIs.chat;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tut.ac.za.AgriFinanceAPIs.chat.ChatDtos.ConversationOut;
import tut.ac.za.AgriFinanceAPIs.chat.ChatDtos.FarmerOut;
import tut.ac.za.AgriFinanceAPIs.chat.ChatDtos.MessageOut;
import tut.ac.za.AgriFinanceAPIs.chat.ChatDtos.SendRequest;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @GetMapping("/farmers")
    public List<FarmerOut> farmers(Authentication auth) {
        return service.directory(auth.getName());
    }

    @GetMapping("/conversations")
    public List<ConversationOut> conversations(Authentication auth) {
        return service.conversations(auth.getName());
    }

    @GetMapping("/rooms/{room}/messages")
    public List<MessageOut> history(@PathVariable String room, Authentication auth) {
        return service.history(auth.getName(), room);
    }

    @PostMapping("/rooms/{room}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageOut send(@PathVariable String room, @RequestBody SendRequest request, Authentication auth) {
        return service.send(auth.getName(), room, request.body());
    }
}