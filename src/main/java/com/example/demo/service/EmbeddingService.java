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

    @Value("${ollama.base.url}")
    private String ollamaBaseUrl;

    @Value("${ollama.embedding.model}")
    private String embeddingModel;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public float[] embed(String text) throws IOException {

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", embeddingModel);
        requestBody.put("prompt", text);

        Request request = new Request.Builder()
                .url(ollamaBaseUrl + "/api/embeddings")
                .post(RequestBody.create(
                        objectMapper.writeValueAsString(requestBody),
                        MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            String body = response.body() != null
                    ? response.body().string()
                    : "";

            if (!response.isSuccessful()) {
                throw new IOException(
                        "Ollama embedding error: "
                                + response.code()
                                + " - "
                                + body
                );
            }

            JsonNode root = objectMapper.readTree(body);

            JsonNode embeddingNode = root.path("embedding");

            float[] vector = new float[embeddingNode.size()];

            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = embeddingNode.get(i).floatValue();
            }

            return vector;
        }
    }

    public List<float[]> embedAll(List<String> chunks)
            throws IOException, InterruptedException {

        List<float[]> embeddings = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {

            System.out.printf(
                    "Embedding chunk %d / %d%n",
                    i + 1,
                    chunks.size()
            );

            embeddings.add(embed(chunks.get(i)));

            if (i < chunks.size() - 1) {
                Thread.sleep(100);
            }
        }

        return embeddings;
    }
}