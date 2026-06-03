package com.example.demo.service;

import com.example.demo.model.ChatSession;
import com.example.demo.model.FeedbackLog;
import com.example.demo.model.Message;
import com.example.demo.repository.ChatSessionRepository;
import com.example.demo.repository.FeedbackLogRepository;
import com.example.demo.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
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

        // Fetch only 2 chunks — each chunk is now ~150 words, so the total
        // context sent to the LLM is ~300 words instead of ~800.
        List<ChromaDbService.SearchResult> context =
                chromaDbService.searchSimilarChunks(embeddingService.embed(question), 2);

        log.info("Retrieved {} context chunks for question: '{}'",
                context.size(), question.length() > 60 ? question.substring(0, 60) + "…" : question);

        String prompt = buildPrompt(question, context);
        log.info("Prompt length: {} chars", prompt.length());

        String answer = llmService.generateAnswer(prompt);

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
        StringBuilder prompt = new StringBuilder(
                "You are a college FAQ assistant for Vardhaman College of Engineering. " +
                "Answer using ONLY the documents below. Be concise. " +
                "If the answer is not in the documents, say: " +
                "\"I don't have that information. Please contact the college office.\"\n\n" +
                "DOCUMENTS:\n"
        );
        for (int i = 0; i < context.size(); i++) {
            ChromaDbService.SearchResult result = context.get(i);
            prompt.append("[").append(i + 1).append("] ")
                  .append(result.fileName()).append(": ")
                  .append(result.text()).append("\n\n");
        }
        prompt.append("QUESTION: ").append(question).append("\n\nANSWER:");
        return prompt.toString();
    }

    private void validateRequired(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    public record ChatResponse(String answer, String sessionId, Long messageId, List<String> sources) {
    }
}
