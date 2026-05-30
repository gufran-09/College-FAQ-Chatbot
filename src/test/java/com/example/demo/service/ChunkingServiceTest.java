package com.example.demo.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Unit tests for ChunkingService.
 *
 * Run with: mvn test
 * Or right-click the file in IntelliJ → Run
 *
 * These tests verify the chunking logic WITHOUT needing Spring Boot,
 * PostgreSQL, or any external API — they run instantly.
 */
class ChunkingServiceTest {

    private ChunkingService chunkingService;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingService();
        // Set values manually (normally Spring reads these from application.properties)
        // We use reflection-like approach — just set the fields directly for testing
    }

    @Test
    void emptyTextReturnsEmptyList() {
        // Arrange: a service with default settings
        ChunkingService service = buildService(400, 50);

        // Act
        List<String> result = service.chunk("");

        // Assert
        assertTrue(result.isEmpty(), "Empty text should produce zero chunks");
    }

    @Test
    void shortTextProducesOneChunk() {
        ChunkingService service = buildService(400, 50);

        // 10 words — much less than chunk size of 400
        String text = "The college was founded in 1985 in Hyderabad Telangana.";

        List<String> result = service.chunk(text);

        assertEquals(1, result.size(), "Short text should produce exactly one chunk");
        assertTrue(result.get(0).contains("1985"), "Chunk should contain the original words");
    }

    @Test
    void longTextProducesMultipleChunks() {
        // Use small chunk size to test with manageable text
        ChunkingService service = buildService(5, 2);   // chunkSize=5, overlap=2

        // 12 words: A B C D E F G H I J K L
        String text = "A B C D E F G H I J K L";

        List<String> result = service.chunk(text);

        // With chunkSize=5 and step=(5-2)=3:
        //   chunk 0: words 0..4  = "A B C D E"
        //   chunk 1: words 3..7  = "D E F G H"   (overlap: D, E)
        //   chunk 2: words 6..10 = "G H I J K"   (overlap: G, H)
        //   chunk 3: words 9..11 = "J K L"

        assertTrue(result.size() > 1, "Long text should produce multiple chunks");
    }

    @Test
    void overlapMeansLastWordsRepeatInNextChunk() {
        ChunkingService service = buildService(5, 2);   // step = 3

        String text = "A B C D E F G H I J";
        List<String> chunks = service.chunk(text);

        // Chunk 0 ends with "D E"
        // Chunk 1 should start with "D E" (overlap)
        if (chunks.size() >= 2) {
            assertTrue(chunks.get(0).contains("D"), "First chunk should contain D");
            assertTrue(chunks.get(1).contains("D"), "Second chunk should also contain D (overlap)");
        }
    }

    @Test
    void nullTextReturnsEmptyList() {
        ChunkingService service = buildService(400, 50);
        List<String> result = service.chunk(null);
        assertTrue(result.isEmpty());
    }

    // Helper: creates a ChunkingService with specific settings for testing
    // (bypasses Spring's @Value injection)
    private ChunkingService buildService(int chunkSize, int overlap) {
        ChunkingService service = new ChunkingService() {
            @Override
            public int getChunkSize()    { return chunkSize; }
            @Override
            public int getChunkOverlap() { return overlap; }
        };
        return service;
    }
}
