package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.embedding.url}")
    private String embeddingUrl;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public float[] embed(String text) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing Gemini API key. Set GEMINI_API_KEY.");
        }

        // Gemini request body format
        ObjectNode requestBody = objectMapper.createObjectNode();
        ObjectNode content = requestBody.putObject("content");
        content.putArray("parts").addObject().put("text", text);

        String url = embeddingUrl + "?key=" + apiKey;

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                        objectMapper.writeValueAsString(requestBody),
                        MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("Gemini API error: " + response.code() + " — " + err);
            }
            return parseResponse(response.body().string());
        }
    }

    private float[] parseResponse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        // Gemini returns: {"embedding": {"values": [0.1, 0.2, ...]}}
        JsonNode values = root.path("embedding").path("values");
        if (!values.isArray() || values.isEmpty()) {
            throw new IOException("Unexpected Gemini embedding response: " + json);
        }

        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).floatValue();
        }
        return vector;
    }

    public List<float[]> embedAll(List<String> chunks) throws IOException, InterruptedException {
        List<float[]> embeddings = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            System.out.printf("  Embedding chunk %d / %d...%n", i + 1, chunks.size());
            embeddings.add(embed(chunks.get(i)));
            if (i < chunks.size() - 1) {
                Thread.sleep(200); // stay within free tier rate limit
            }
        }
        return embeddings;
    }
}
