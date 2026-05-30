package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * STEP 3c — Convert a text chunk into an embedding vector.
 *
 * WHAT IS AN EMBEDDING?
 * An embedding is a list of ~384 floating point numbers that represents
 * the MEANING of a piece of text. Two chunks that mean similar things
 * will have vectors that are mathematically close to each other.
 * ChromaDB uses this to find relevant chunks when a user asks a question.
 *
 * We use the free HuggingFace model: sentence-transformers/all-MiniLM-L6-v2
 *   - Input:  a string of text
 *   - Output: float[384]  (384-dimensional vector)
 *   - Cost:   free up to the rate limit (~1000 calls/day on free tier)
 */
@Service
public class EmbeddingService {

    @Value("${huggingface.api.key}")
    private String apiKey;

    @Value("${huggingface.model.url}")
    private String modelUrl;

    // OkHttp client — reuse one instance across all calls (thread-safe)
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @jakarta.annotation.PostConstruct
    void validateConfiguration() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing HuggingFace API key. Set HUGGINGFACE_API_KEY.");
        }
        if (modelUrl == null || modelUrl.isBlank()) {
            throw new IllegalStateException("Missing HuggingFace model URL. Set HUGGINGFACE_MODEL_URL.");
        }
    }

    /**
     * Convert one text chunk into a float vector.
     *
     * HuggingFace API format:
     *   POST https://api-inference.huggingface.co/pipeline/feature-extraction/<model>
     *   Headers: Authorization: Bearer <api_key>
     *   Body:    { "inputs": "your text here" }
     *   Response: [[0.123, -0.456, 0.789, ...]]   (array of arrays)
     *
     * @param text  The chunk text to embed
     * @return      float[] of length 384
     */
    public float[] embed(String text) throws IOException {

        // Build the JSON request body: {"inputs": "text here"}
        String requestBody = objectMapper.writeValueAsString(
                new java.util.HashMap<>() {{ put("inputs", text); }}
        );

        // Build the HTTP request
        Request request = new Request.Builder()
                .url(modelUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        // Execute the request (blocking call — waits for response)
        try (Response response = httpClient.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "no body";
                throw new IOException(
                        "HuggingFace API error: HTTP " + response.code() + " — " + errorBody
                );
            }

            String responseJson = response.body().string();
            return parseEmbeddingResponse(responseJson);
        }
    }

    /**
     * HuggingFace returns the embedding as a nested JSON array:
     *   [[0.123, -0.456, 0.789, ...]]
     *                        ^^ outer array has 1 element (our 1 input)
     *                            ^^ inner array has 384 floats
     *
     * This method unpacks that structure into a plain float[].
     */
    private float[] parseEmbeddingResponse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);

        // The response is [[...]] — get the first (only) element
        JsonNode vectorNode = root.get(0);

        if (vectorNode == null || !vectorNode.isArray()) {
            throw new IOException("Unexpected HuggingFace response format: " + json);
        }

        // Convert JsonNode array → float[]
        List<Float> floatList = new ArrayList<>();
        for (JsonNode num : vectorNode) {
            floatList.add(num.floatValue());
        }

        // Convert List<Float> → float[] (Java doesn't do this automatically)
        float[] vector = new float[floatList.size()];
        for (int i = 0; i < floatList.size(); i++) {
            vector[i] = floatList.get(i);
        }

        return vector;
    }

    /**
     * Rate-limited batch embed: embed multiple chunks with a small delay
     * between calls to avoid hitting HuggingFace's free tier rate limit.
     *
     * @param chunks  List of text chunks
     * @return        List of float[] vectors, same order as input
     */
    public List<float[]> embedAll(List<String> chunks) throws IOException, InterruptedException {
        List<float[]> embeddings = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            System.out.printf("  Embedding chunk %d / %d...%n", i + 1, chunks.size());

            embeddings.add(embed(chunks.get(i)));

            // Wait 200ms between calls to respect the rate limit
            // Remove this if you have a paid HuggingFace account
            if (i < chunks.size() - 1) {
                Thread.sleep(200);
            }
        }

        return embeddings;
    }
}
