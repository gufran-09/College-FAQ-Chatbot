package com.example.demo.service;

import com.example.demo.model.Document;
import com.example.demo.model.DocumentChunk;
import com.example.demo.repository.DocumentChunkRepository;
import com.example.demo.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final ChromaDbService chromaDbService;
    private final AsyncIngestionService asyncIngestionService;

    public Document startIngestion(MultipartFile file, String uploadedBy) throws IOException {
        Document document = new Document();
        document.setFileName(file.getOriginalFilename());
        document.setFileType(getFileType(file.getOriginalFilename()));
        document.setUploadedBy(uploadedBy);
        document = documentRepository.save(document);

        // Multipart storage may disappear after the request returns, so snapshot it first.
        asyncIngestionService.runPipeline(document.getId(), document.getFileName(), file.getBytes());
        return document;
    }

    public void deleteDocument(Long documentId) throws IOException {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndex(documentId);
        chromaDbService.deleteEmbeddings(chunks.stream().map(DocumentChunk::getChromaId).toList());
        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.delete(document);
    }

    private String getFileType(String fileName) {
        if (fileName == null) {
            return "UNKNOWN";
        }
        return fileName.toLowerCase().endsWith(".pdf") ? "PDF" : "DOCX";
    }
}
