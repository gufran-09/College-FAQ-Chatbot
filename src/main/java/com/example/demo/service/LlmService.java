package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class LlmService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.chat.url}")
    private String chatUrl;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateAnswer(String prompt) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing Gemini API key. Set GEMINI_API_KEY.");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("contents").addObject()
                .putArray("parts").addObject()
                .put("text", prompt);

        Request request = new Request.Builder()
                .url(chatUrl + "?key=" + apiKey)
                .post(RequestBody.create(
                        objectMapper.writeValueAsString(body),
                        MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("Gemini chat API error: HTTP "
                        + response.code() + " - " + responseBody);
            }
            JsonNode text = objectMapper.readTree(responseBody)
                    .path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");
            if (text.isMissingNode() || text.asText().isBlank()) {
                throw new IOException("Unexpected Gemini chat response: " + responseBody);
            }
            return text.asText();
        }
    }
}
