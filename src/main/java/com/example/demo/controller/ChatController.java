package com.example.demo.controller;

import com.example.demo.model.Message;
import com.example.demo.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:5173}")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<?> ask(@RequestBody ChatRequest request) {
        try {
            return ResponseEntity.ok(chatService.ask(request.sessionId(), request.question()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (Exception exception) {
            // Log the real cause so it appears in the Spring Boot console
            log.error("Chat request failed: {}", exception.getMessage(), exception);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Could not generate an answer",
                                 "detail", exception.getMessage()));
        }
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<Message>> history(@PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.history(sessionId));
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> feedback(@RequestBody FeedbackRequest request) {
        try {
            chatService.saveFeedback(request.messageId(), request.rating());
            return ResponseEntity.ok(Map.of("message", "Feedback saved"));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    public record ChatRequest(String sessionId, String question) {
    }

    public record FeedbackRequest(Long messageId, String rating) {
    }
}
