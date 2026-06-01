package com.example.demo.service;

import com.example.demo.model.Document;
import com.example.demo.model.DocumentChunk;
import com.example.demo.repository.DocumentChunkRepository;
import com.example.demo.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncIngestionService {

    private final FileReaderService fileReaderService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final ChromaDbService chromaDbService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    @Async
    public void runPipeline(Long documentId, String fileName, byte[] contents) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        try {
            document.setStatus("PROCESSING");
            documentRepository.save(document);

            String fullText = fileReaderService.extractText(fileName, contents);
            List<String> chunks = chunkingService.chunk(fullText);
            if (chunks.isEmpty()) {
                log.warn("No text extracted from {}. It may be a scanned PDF.", fileName);
            }

            List<float[]> embeddings = embeddingService.embedAll(chunks);
            for (int index = 0; index < chunks.size(); index++) {
                String chromaId = "doc_" + documentId + "_chunk_" + index;
                chromaDbService.storeEmbedding(
                        chromaId, embeddings.get(index), chunks.get(index), fileName, documentId
                );
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocument(document);
                chunk.setChunkText(chunks.get(index));
                chunk.setChunkIndex(index);
                chunk.setChromaId(chromaId);
                chunkRepository.save(chunk);
            }

            document.setStatus("COMPLETED");
            document.setTotalChunks(chunks.size());
            documentRepository.save(document);
        } catch (Exception exception) {
            log.error("Ingestion failed for {}", fileName, exception);
            document.setStatus("FAILED");
            documentRepository.save(document);
        }
    }
}
