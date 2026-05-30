package com.example.demo.service;

import com.example.demo.model.Document;
import com.example.demo.model.DocumentChunk;
import com.example.demo.repository.DocumentChunkRepository;
import com.example.demo.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * STEP 3 — Orchestrates the full ingestion pipeline.
 *
 * The pipeline runs in this exact order:
 *
 *   MultipartFile (uploaded PDF/DOCX)
 *       │
 *       ▼
 *   FileReaderService.extractText()     →  raw String of full document text
 *       │
 *       ▼
 *   ChunkingService.chunk()             →  List<String> of 400-word pieces
 *       │
 *       ▼
 *   EmbeddingService.embedAll()         →  List<float[]> vectors from HuggingFace
 *       │
 *       ▼
 *   ChromaDbService.store()             →  vectors stored in ChromaDB
 *       │
 *       ▼
 *   DocumentChunkRepository.save()      →  chunk metadata saved in PostgreSQL
 *
 * The @Async annotation means this runs in a background thread.
 * The HTTP response returns immediately while processing happens in the background.
 * This is important because a large PDF can take 30+ seconds to process.
 */
@Service
@RequiredArgsConstructor   // Lombok: generates constructor for all final fields
@Slf4j                     // Lombok: generates log.info(), log.error() etc.
public class IngestionService {

    private final FileReaderService fileReaderService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final ChromaDbService chromaDbService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;

    /**
     * Start the ingestion pipeline for an uploaded file.
     *
     * Called by the AdminController when a file is uploaded.
     * Saves a Document record first, then runs the pipeline async.
     *
     * @return the saved Document (with id and PENDING status)
     */
    public Document startIngestion(MultipartFile file, String uploadedBy) {
        // Create and save the Document record immediately
        Document doc = new Document();
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(getFileType(file.getOriginalFilename()));
        doc.setUploadedBy(uploadedBy);
        doc.setStatus("PENDING");
        doc = documentRepository.save(doc);

        // Run the heavy processing in a background thread
        runPipeline(doc, file);

        return doc;  // Return immediately — UI shows "Processing..."
    }

    /**
     * The actual pipeline. @Async makes Spring run this in a thread pool.
     *
     * You need @EnableAsync on your main application class for this to work.
     */
    @Async
    public void runPipeline(Document doc, MultipartFile file) {
        log.info("Starting ingestion for: {}", doc.getFileName());

        try {
            // ── Stage 1: Extract text ─────────────────────────────────────────
            doc.setStatus("PROCESSING");
            documentRepository.save(doc);

            log.info("Stage 1: Extracting text from {}", doc.getFileName());
            String fullText = fileReaderService.extractText(file);
            log.info("Extracted {} characters", fullText.length());

            // ── Stage 2: Split into chunks ────────────────────────────────────
            log.info("Stage 2: Splitting into chunks...");
            List<String> chunks = chunkingService.chunk(fullText);
            log.info("Created {} chunks", chunks.size());

            // ── Stage 3: Embed each chunk ─────────────────────────────────────
            log.info("Stage 3: Embedding {} chunks via HuggingFace...", chunks.size());
            List<float[]> embeddings = embeddingService.embedAll(chunks);

            // ── Stage 4: Store in ChromaDB + PostgreSQL ───────────────────────
            log.info("Stage 4: Storing in ChromaDB and PostgreSQL...");

            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                float[] vector = embeddings.get(i);

                // Build a unique ID for this chunk in ChromaDB
                // Format: "doc_{documentId}_chunk_{chunkIndex}"
                String chromaId = "doc_" + doc.getId() + "_chunk_" + i;

                // Store the vector in ChromaDB with metadata
                chromaDbService.storeEmbedding(
                        chromaId,
                        vector,
                        chunkText,
                        doc.getFileName(),
                        doc.getId()
                );

                // Save chunk metadata in PostgreSQL
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocument(doc);
                chunk.setChunkText(chunkText);
                chunk.setChunkIndex(i);
                chunk.setChromaId(chromaId);
                chunkRepository.save(chunk);
            }

            // ── Done ──────────────────────────────────────────────────────────
            doc.setStatus("COMPLETED");
            doc.setTotalChunks(chunks.size());
            documentRepository.save(doc);
            log.info("Ingestion complete: {} chunks stored for {}", chunks.size(), doc.getFileName());

        } catch (Exception e) {
            // If anything fails, mark the document as FAILED
            log.error("Ingestion failed for {}: {}", doc.getFileName(), e.getMessage(), e);
            doc.setStatus("FAILED");
            documentRepository.save(doc);
        }
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "UNKNOWN";
        if (fileName.toLowerCase().endsWith(".pdf"))  return "PDF";
        if (fileName.toLowerCase().endsWith(".docx")) return "DOCX";
        return "UNKNOWN";
    }
}