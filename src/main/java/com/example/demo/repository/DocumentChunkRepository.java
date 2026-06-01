package com.example.demo.repository;

import com.example.demo.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    // Get all chunks for a specific document, in order
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);

    // Count how many chunks a document produced
    int countByDocumentId(Long documentId);

    @Transactional
    void deleteByDocumentId(Long documentId);
}
