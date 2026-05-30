package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * STEP 3d — Store and retrieve embeddings in ChromaDB.
 *
 * ChromaDB is a vector database. It stores:
 *   - The embedding vector (float[384])
 *   - The original text (as "document")
 *   - Metadata (filename, document ID)
 *
 * ChromaDB has a REST API running at http://localhost:8000 (via Docker).
 * We call it directly using OkHttp — no official Java client needed.
 *
 * ChromaDB concepts:
 *   Collection = a named group of embeddings (like a table in PostgreSQL)
 *   We use one collection: "college_docs"
 */
@Service
public class ChromaDbService {

    @Value("${chromadb.base.url}")
    private String baseUrl;

    @Value("${chromadb.collection.name}")
    private String collectionName;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Cached collection ID (fetched once from ChromaDB)
    private String collectionId = null;

    // ── Collection management ─────────────────────────────────────────────────

    /**
     * Get (or create) the ChromaDB collection.
     * Called automatically before any store/search operation.
     *
     * ChromaDB REST: POST /api/v2/collections
     * Body: { "name": "college_docs" }
     */
    @Value("${chromadb.tenant}")
    private String tenant;

    @Value("${chromadb.database}")
    private String database;

    public String getOrCreateCollection() throws IOException {
        if (collectionId != null) return collectionId;

        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections";

        String body = objectMapper.writeValueAsString(
                new java.util.HashMap<>() {{ put("name", collectionName); }}
        );

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body().string();
            JsonNode json = objectMapper.readTree(responseBody);
            collectionId = json.get("id").asText();
            return collectionId;
        }
    }
    // ── Store an embedding ────────────────────────────────────────────────────

    /**
     * Store one chunk's embedding in ChromaDB.
     *
     * ChromaDB REST: POST /api/v2/collections/{collection_id}/add
     * Body: {
     *   "ids":        ["doc_5_chunk_3"],
     *   "embeddings": [[0.123, -0.456, ...]],
     *   "documents":  ["The actual chunk text..."],
     *   "metadatas":  [{"fileName": "admissions.pdf", "documentId": "5"}]
     * }
     *
     * Note: ChromaDB expects arrays even for a single item.
     */
    public void storeEmbedding(
            String chromaId,
            float[] vector,
            String chunkText,
            String fileName,
            Long documentId
    ) throws IOException {
        String colId = getOrCreateCollection();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + colId + "/add";

        // Build the request body as a Jackson JSON tree
        ObjectNode body = objectMapper.createObjectNode();

        // ids: ["doc_5_chunk_3"]
        ArrayNode ids = body.putArray("ids");
        ids.add(chromaId);

        // embeddings: [[0.123, -0.456, ...]]
        ArrayNode embeddings = body.putArray("embeddings");
        ArrayNode vectorArray = embeddings.addArray();
        for (float v : vector) {
            vectorArray.add(v);
        }

        // documents: ["The actual chunk text"]
        ArrayNode documents = body.putArray("documents");
        documents.add(chunkText);

        // metadatas: [{"fileName": "admissions.pdf", "documentId": "5"}]
        ArrayNode metadatas = body.putArray("metadatas");
        ObjectNode meta = metadatas.addObject();
        meta.put("fileName", fileName);
        meta.put("documentId", String.valueOf(documentId));

        // Make the POST request
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                        objectMapper.writeValueAsString(body),
                        MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("ChromaDB store failed: HTTP " + response.code() + " — " + err);
            }
        }
    }

    // ── Search for similar chunks ─────────────────────────────────────────────

    /**
     * Find the top-k chunks most semantically similar to a query vector.
     *
     * Called during chat: embed the user's question → search ChromaDB →
     * get the most relevant document chunks → pass them to the LLM.
     *
     * ChromaDB REST: POST /api/v2/collections/{collection_id}/query
     * Body: {
     *   "query_embeddings": [[0.1, -0.2, ...]],
     *   "n_results": 5
     * }
     *
     * @param queryVector  embedding of the user's question
     * @param topK         how many chunks to return (typically 3–5)
     * @return             List of chunk texts, most relevant first
     */
    public List<String> searchSimilarChunks(float[] queryVector, int topK) throws IOException {
        String colId = getOrCreateCollection();
        String url = baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database + "/collections/" + colId + "/query";

        ObjectNode body = objectMapper.createObjectNode();

        // query_embeddings: [[...]] — note: outer array (for batching, we send 1)
        ArrayNode queryEmbeddings = body.putArray("query_embeddings");
        ArrayNode vectorArray = queryEmbeddings.addArray();
        for (float v : queryVector) {
            vectorArray.add(v);
        }
        body.put("n_results", topK);

        // Also return the document text and metadata in the response
        ArrayNode include = body.putArray("include");
        include.add("documents");
        include.add("metadatas");
        include.add("distances");

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                        objectMapper.writeValueAsString(body),
                        MediaType.parse("application/json")
                ))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String err = response.body() != null ? response.body().string() : "";
                throw new IOException("ChromaDB search failed: HTTP " + response.code() + " — " + err);
            }

            String responseBody = response.body().string();
            return parseSearchResponse(responseBody);
        }
    }

    /**
     * Parse ChromaDB's query response to extract the chunk texts.
     *
     * Response shape:
     * {
     *   "documents": [["chunk text 1", "chunk text 2", ...]],
     *   "metadatas": [[{...}, {...}, ...]],
     *   "distances": [[0.12, 0.34, ...]]
     * }
     *
     * The outer array is per-query (we only sent 1 query).
     * The inner array contains the top-k results.
     */
    private List<String> parseSearchResponse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);

        // Get the first (only) query's results
        JsonNode documents = root.path("documents").get(0);

        List<String> results = new ArrayList<>();
        if (documents != null && documents.isArray()) {
            for (JsonNode doc : documents) {
                results.add(doc.asText());
            }
        }
        return results;
    }
}