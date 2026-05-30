package com.example.demo.repository;

import com.example.demo.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Spring Data JPA gives you free implementations of save(), findById(),
 * findAll(), delete() etc. — you don't write any SQL.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    // Find all documents with a given status (e.g. "COMPLETED")
    List<Document> findByStatus(String status);

    // Check if a file with this name was already uploaded
    boolean existsByFileName(String fileName);
}