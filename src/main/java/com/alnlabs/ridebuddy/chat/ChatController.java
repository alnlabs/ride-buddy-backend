package com.alnlabs.ridebuddy.chat;

import com.alnlabs.ridebuddy.common.AuthUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    public List<ChatService.ConversationResponse> conversations() {
        return chatService.listMine(AuthUser.requireUserId());
    }

    @PostMapping("/conversations/open")
    public ChatService.ConversationResponse open(@RequestBody ChatService.OpenConversationRequest body) {
        return chatService.open(AuthUser.requireUserId(), body);
    }

    @GetMapping("/conversations/{id}/messages")
    public List<ChatService.MessageResponse> messages(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return chatService.messages(AuthUser.requireUserId(), id, before, limit);
    }

    @PostMapping("/conversations/{id}/messages")
    public ChatService.MessageResponse send(
            @PathVariable UUID id,
            @RequestBody ChatService.SendMessageRequest body
    ) {
        return chatService.send(AuthUser.requireUserId(), id, body == null ? null : body.body());
    }
}
