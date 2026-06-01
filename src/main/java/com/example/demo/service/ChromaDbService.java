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

@Service
public class ChromaDbService {

    @Value("${chromadb.base.url}")
    private String baseUrl;

    @Value("${chromadb.collection.name}")
    private String collectionName;

    @Value("${chromadb.tenant}")
    private String tenant;

    @Value("${chromadb.database}")
    private String database;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile String collectionId;

    public record SearchResult(String text, String fileName, String documentId) {
    }

    public synchronized String getOrCreateCollection() throws IOException {
        if (collectionId != null) {
            return collectionId;
        }

        String collectionsUrl = apiBaseUrl() + "/collections";
        Request getRequest = new Request.Builder()
                .url(collectionsUrl + "/" + collectionName)
                .get()
                .build();

        try (Response response = httpClient.newCall(getRequest).execute()) {
            if (response.isSuccessful()) {
                collectionId = parseCollectionId(readBody(response));
                return collectionId;
            }
            if (response.code() != 404) {
                throw new IOException("ChromaDB collection lookup failed: HTTP "
                        + response.code() + " - " + readBody(response));
            }
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("name", collectionName);
        Request createRequest = jsonPost(collectionsUrl, body);

        try (Response response = httpClient.newCall(createRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("ChromaDB collection creation failed: HTTP "
                        + response.code() + " - " + readBody(response));
            }
            collectionId = parseCollectionId(readBody(response));
            return collectionId;
        }
    }

    public void storeEmbedding(String chromaId, float[] vector, String chunkText,
                               String fileName, Long documentId) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("ids").add(chromaId);
        addVector(body.putArray("embeddings").addArray(), vector);
        body.putArray("documents").add(chunkText);
        ObjectNode metadata = body.putArray("metadatas").addObject();
        metadata.put("fileName", fileName);
        metadata.put("documentId", String.valueOf(documentId));

        executeSuccessful(jsonPost(collectionUrl() + "/add", body), "store embeddings");
    }

    public List<SearchResult> searchSimilarChunks(float[] queryVector, int topK) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        addVector(body.putArray("query_embeddings").addArray(), queryVector);
        body.put("n_results", topK);
        ArrayNode include = body.putArray("include");
        include.add("documents");
        include.add("metadatas");
        include.add("distances");

        Request request = jsonPost(collectionUrl() + "/query", body);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("ChromaDB search failed: HTTP "
                        + response.code() + " - " + readBody(response));
            }
            return parseSearchResponse(readBody(response));
        }
    }

    public void deleteEmbeddings(List<String> chromaIds) throws IOException {
        if (chromaIds.isEmpty()) {
            return;
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode ids = body.putArray("ids");
        chromaIds.forEach(ids::add);
        executeSuccessful(jsonPost(collectionUrl() + "/delete", body), "delete embeddings");
    }

    private List<SearchResult> parseSearchResponse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode documents = firstBatch(root.path("documents"));
        JsonNode metadatas = firstBatch(root.path("metadatas"));
        List<SearchResult> results = new ArrayList<>();

        if (documents == null || !documents.isArray()) {
            return results;
        }
        for (int index = 0; index < documents.size(); index++) {
            JsonNode metadata = metadatas != null && index < metadatas.size()
                    ? metadatas.get(index) : null;
            results.add(new SearchResult(
                    documents.get(index).asText(),
                    metadata == null ? "unknown" : metadata.path("fileName").asText("unknown"),
                    metadata == null ? "" : metadata.path("documentId").asText("")
            ));
        }
        return results;
    }

    private JsonNode firstBatch(JsonNode batches) {
        return batches.isArray() && !batches.isEmpty() ? batches.get(0) : null;
    }

    private String collectionUrl() throws IOException {
        return apiBaseUrl() + "/collections/" + getOrCreateCollection();
    }

    private String apiBaseUrl() {
        return baseUrl + "/api/v2/tenants/" + tenant + "/databases/" + database;
    }

    private Request jsonPost(String url, JsonNode body) throws IOException {
        return new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                        objectMapper.writeValueAsString(body),
                        MediaType.parse("application/json")
                ))
                .build();
    }

    private void executeSuccessful(Request request, String operation) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("ChromaDB failed to " + operation + ": HTTP "
                        + response.code() + " - " + readBody(response));
            }
        }
    }

    private void addVector(ArrayNode array, float[] vector) {
        for (float value : vector) {
            array.add(value);
        }
    }

    private String parseCollectionId(String json) throws IOException {
        String id = objectMapper.readTree(json).path("id").asText();
        if (id.isBlank()) {
            throw new IOException("ChromaDB returned a collection without an id: " + json);
        }
        return id;
    }

    private String readBody(Response response) throws IOException {
        return response.body() == null ? "" : response.body().string();
    }
}
