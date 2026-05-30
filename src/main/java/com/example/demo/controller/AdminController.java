package com.example.demo.controller;

import com.example.demo.model.Document;
import com.example.demo.repository.DocumentRepository;
import com.example.demo.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST API for the Admin panel.
 *
 * Endpoints:
 *   POST /api/admin/upload         — upload a PDF or DOCX file
 *   GET  /api/admin/documents      — list all uploaded documents
 *   GET  /api/admin/documents/{id} — check ingestion status of one document
 *
 * In a real app you'd add authentication here so only admins can call these.
 * For now, all endpoints are open (fine for a college project demo).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:3000}")  // Configure per environment
public class AdminController {

    private final IngestionService ingestionService;
    private final DocumentRepository documentRepository;

    /**
     * Upload a PDF or DOCX file for ingestion.
     *
     * The React admin panel sends a multipart/form-data POST with the file.
     * This endpoint saves the Document record and starts the pipeline async.
     *
     * Example curl:
     *   curl -X POST http://localhost:8080/api/admin/upload \
     *        -F "file=@admissions.pdf" \
     *        -F "uploadedBy=admin"
     *
     * Returns immediately with status PENDING — don't wait for processing.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "uploadedBy", defaultValue = "admin") String uploadedBy
    ) {
        // Basic validation
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No file provided"));
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null ||
                (!fileName.toLowerCase().endsWith(".pdf") &&
                        !fileName.toLowerCase().endsWith(".docx"))) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only PDF and DOCX files are supported"));
        }

        // Start ingestion (saves Document record, then processes async)
        Document doc = ingestionService.startIngestion(file, uploadedBy);

        return ResponseEntity.ok(Map.of(
                "message", "File uploaded successfully. Processing started.",
                "documentId", doc.getId(),
                "fileName",   doc.getFileName(),
                "status",     doc.getStatus()
        ));
    }

    /**
     * List all uploaded documents and their ingestion status.
     * The React admin panel polls this to show a progress table.
     */
    @GetMapping("/documents")
    public ResponseEntity<List<Document>> getAllDocuments() {
        return ResponseEntity.ok(documentRepository.findAll());
    }

    /**
     * Get the status of a specific document.
     * React polls this every 5 seconds after uploading to check if done.
     *
     * Status values:
     *   PENDING    → saved, not yet processed
     *   PROCESSING → pipeline is running
     *   COMPLETED  → all chunks embedded and stored
     *   FAILED     → something went wrong (check server logs)
     */
    @GetMapping("/documents/{id}")
    public ResponseEntity<?> getDocumentStatus(@PathVariable Long id) {
        return documentRepository.findById(id)
                .map(doc -> ResponseEntity.ok(Map.of(
                        "id",          doc.getId(),
                        "fileName",    doc.getFileName(),
                        "status",      doc.getStatus(),
                        "totalChunks", doc.getTotalChunks() != null ? doc.getTotalChunks() : 0,
                        "uploadedAt",  doc.getUploadedAt().toString()
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
