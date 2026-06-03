package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    @Value("${ollama.base.url}")
    private String ollamaBaseUrl;

    @Value("${ollama.chat.model}")
    private String chatModel;

    // 3-minute read timeout — enough for a slow CPU inference run.
    // If it takes longer than this, the model is too large for the hardware.
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Warm up the model at startup so the first real user request doesn't
     * pay the cold-load penalty (can be 30-60 s on first call).
     */
    @PostConstruct
    public void warmUp() {
        new Thread(() -> {
            try {
                log.info("Warming up Ollama model '{}'...", chatModel);
                generateAnswer("Hello");
                log.info("Ollama model '{}' is warm and ready.", chatModel);
            } catch (Exception e) {
                log.warn("Ollama warm-up failed (model may load on first real request): {}",
                        e.getMessage());
            }
        }, "ollama-warmup").start();
    }

    public String generateAnswer(String prompt) throws IOException {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", chatModel);
        body.put("prompt", prompt);
        body.put("stream", false);

        // ── Generation options — these are the key performance levers ──────────
        ObjectNode options = body.putObject("options");

        // Cap the output at 300 tokens. A FAQ answer never needs more.
        // Without this, Ollama defaults to filling the entire context window.
        options.put("num_predict", 300);

        // Context window: 1024 is enough for a short RAG prompt + answer.
        // Smaller = faster prefill. Increase only if you see truncated answers.
        options.put("num_ctx", 1024);

        // Lower temperature = more focused, factual answers (good for FAQ).
        options.put("temperature", 0.2);

        // ──────────────────────────────────────────────────────────────────────

        String url = ollamaBaseUrl + "/api/generate";
        log.info("Calling Ollama — model={} promptLength={} chars", chatModel, prompt.length());

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                        objectMapper.writeValueAsString(body),
                        MediaType.parse("application/json; charset=utf-8")
                ))
                .build();

        long start = System.currentTimeMillis();
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            long elapsed = System.currentTimeMillis() - start;
            log.info("Ollama responded in {}ms — HTTP {}", elapsed, response.code());

            if (!response.isSuccessful()) {
                log.error("Ollama error: {}", responseBody);
                throw new IOException("Ollama chat error: " + response.code()
                        + " — " + responseBody);
            }

            JsonNode root = objectMapper.readTree(responseBody);
            String answer = root.path("response").asText();

            if (answer == null || answer.isBlank()) {
                log.error("Ollama returned empty response: {}", responseBody);
                throw new IOException("Ollama returned an empty response: " + responseBody);
            }

            log.info("Answer length: {} chars", answer.length());
            return answer;
        }
    }
}
