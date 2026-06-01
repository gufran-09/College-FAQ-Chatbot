package com.example.demo.service;

import com.example.demo.model.ChatSession;
import com.example.demo.model.FeedbackLog;
import com.example.demo.model.Message;
import com.example.demo.repository.ChatSessionRepository;
import com.example.demo.repository.FeedbackLogRepository;
import com.example.demo.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final List<String> VALID_RATINGS = List.of("THUMBS_UP", "THUMBS_DOWN");

    private final ChatSessionRepository chatSessionRepository;
    private final MessageRepository messageRepository;
    private final FeedbackLogRepository feedbackLogRepository;
    private final EmbeddingService embeddingService;
    private final ChromaDbService chromaDbService;
    private final LlmService llmService;

    public ChatResponse ask(String sessionId, String question) throws IOException {
        validateRequired(sessionId, "sessionId");
        validateRequired(question, "question");
        ensureSession(sessionId);
        saveMessage(sessionId, "USER", question);

        List<ChromaDbService.SearchResult> context =
                chromaDbService.searchSimilarChunks(embeddingService.embed(question), 5);
        String answer = llmService.generateAnswer(buildPrompt(question, context));
        Message assistantMessage = saveMessage(sessionId, "ASSISTANT", answer);
        List<String> sources = context.stream()
                .map(ChromaDbService.SearchResult::fileName)
                .distinct()
                .toList();
        return new ChatResponse(answer, sessionId, assistantMessage.getId(), sources);
    }

    public List<Message> history(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    public FeedbackLog saveFeedback(Long messageId, String rating) {
        if (!VALID_RATINGS.contains(rating)) {
            throw new IllegalArgumentException("rating must be THUMBS_UP or THUMBS_DOWN");
        }
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found: " + messageId));
        if (!"ASSISTANT".equals(message.getRole())) {
            throw new IllegalArgumentException("Feedback can only be submitted for assistant messages");
        }
        FeedbackLog feedback = new FeedbackLog();
        feedback.setMessage(message);
        feedback.setRating(rating);
        return feedbackLogRepository.save(feedback);
    }

    private void ensureSession(String sessionId) {
        chatSessionRepository.findBySessionId(sessionId).orElseGet(() -> {
            ChatSession session = new ChatSession();
            session.setSessionId(sessionId);
            return chatSessionRepository.save(session);
        });
    }

    private Message saveMessage(String sessionId, String role, String content) {
        Message message = new Message();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        return messageRepository.save(message);
    }

    private String buildPrompt(String question, List<ChromaDbService.SearchResult> context) {
        StringBuilder prompt = new StringBuilder("""
                You are a helpful college FAQ assistant for Vardhaman College of Engineering.
                Answer the student's question ONLY using the information provided below.
                If the answer is not in the provided information, say:
                "I don't have information about that. Please contact the college office."
                Always cite the source document name.

                COLLEGE DOCUMENTS:
                """);
        for (int index = 0; index < context.size(); index++) {
            ChromaDbService.SearchResult result = context.get(index);
            prompt.append("\n[Document ").append(index + 1).append(" - ")
                    .append(result.fileName()).append("]: ")
                    .append(result.text()).append('\n');
        }
        return prompt.append("\nSTUDENT QUESTION: ").append(question)
                .append("\n\nProvide a clear, helpful answer with the source document name.")
                .toString();
    }

    private void validateRequired(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    public record ChatResponse(String answer, String sessionId, Long messageId, List<String> sources) {
    }
}
