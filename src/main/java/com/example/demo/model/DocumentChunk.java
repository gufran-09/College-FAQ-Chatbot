package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One text chunk extracted from a Document.
 *
 * A single PDF might produce 30 chunks.
 * Each chunk stores its text AND its ChromaDB vector ID
 * so we can trace an answer back to its source document.
 *
 * Maps to the "document_chunks" table in PostgreSQL.
 */
@Entity
@Table(name = "document_chunks")
@Data
@NoArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which document this chunk came from
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    // The actual text of this chunk (300–500 words)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String chunkText;

    // Position of this chunk within the document (0, 1, 2, ...)
    @Column(nullable = false)
    private Integer chunkIndex;

    // The ID we stored this chunk under in ChromaDB
    // We need this to retrieve context later during chat
    @Column(nullable = false, unique = true)
    private String chromaId;       // e.g. "doc_5_chunk_3"
}
