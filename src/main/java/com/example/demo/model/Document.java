package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Represents one uploaded document (a PDF or DOCX file).
 * Each document can produce many DocumentChunks.
 *
 * This maps to the "documents" table in PostgreSQL.
 */
@Entity
@Table(name = "documents")
@Data                   // Lombok: generates getters, setters, toString, equals
@NoArgsConstructor      // Lombok: generates a no-args constructor (JPA needs this)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto-increment ID
    private Long id;

    @Column(nullable = false)
    private String fileName;        // e.g. "admissions_guide.pdf"

    @Column(nullable = false)
    private String fileType;        // "PDF" or "DOCX"

    @Column(nullable = false)
    private String uploadedBy;      // admin username who uploaded it

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    // PENDING → PROCESSING → COMPLETED → FAILED
    @Column(nullable = false)
    private String status;

    @Column
    private Integer totalChunks;    // filled in after ingestion completes

    @PrePersist                     // called automatically before INSERT
    public void prePersist() {
        this.uploadedAt = LocalDateTime.now();
        this.status = "PENDING";
    }
}