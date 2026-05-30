package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * STEP 3b — Split a long document text into smaller overlapping chunks.
 *
 * WHY CHUNK?
 * LLMs have a context window limit. You can't paste a 50-page PDF into a prompt.
 * Instead, we split it into ~400-word pieces and only pass the RELEVANT ones.
 *
 * WHY OVERLAP?
 * If a sentence spans the boundary of two chunks, neither chunk alone contains
 * the full meaning. Overlap ensures every sentence appears fully in at least
 * one chunk. Example with chunk=5 words, overlap=2:
 *
 *   Full text: [A B C D E F G H I J]
 *   Chunk 0:   [A B C D E]
 *   Chunk 1:   [D E F G H]   ← D and E repeated from chunk 0
 *   Chunk 2:   [G H I J]     ← G and H repeated from chunk 1
 */
@Service
public class ChunkingService {

    // Read values from application.properties
    // If the property is missing, use the default value shown after the colon
    @Value("${ingestion.chunk.size:400}")
    private int chunkSize;          // target words per chunk

    @Value("${ingestion.chunk.overlap:50}")
    private int chunkOverlap;       // words to repeat at chunk boundaries

    /**
     * Split fullText into a list of chunk strings.
     *
     * Algorithm:
     *  1. Split the text into individual words by whitespace
     *  2. Use a sliding window of `chunkSize` words
     *  3. Advance the window by (chunkSize - chunkOverlap) words each step
     *  4. Join each window back into a string → one chunk
     */
    public List<String> chunk(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }

        // Split on any whitespace (spaces, newlines, tabs)
        String[] words = fullText.split("\\s+");

        List<String> chunks = new ArrayList<>();

        int configuredChunkSize = getChunkSize();
        int configuredChunkOverlap = getChunkOverlap();
        int stepSize = configuredChunkSize - configuredChunkOverlap;   // how far to advance each time
        if (stepSize <= 0) {
            // Safety check: overlap can't be >= chunkSize
            throw new IllegalStateException(
                    "chunkOverlap (" + configuredChunkOverlap + ") must be less than chunkSize (" + configuredChunkSize + ")"
            );
        }

        int start = 0;

        while (start < words.length) {

            // End index for this chunk (don't go past the array end)
            int end = Math.min(start + configuredChunkSize, words.length);

            // Join words[start..end] back into a sentence
            String chunk = String.join(" ", subArray(words, start, end));

            // Only add non-empty chunks
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            // Move the window forward
            start += stepSize;

            // If the remaining words are fewer than the overlap,
            // we've already captured them in the last chunk — stop.
            if (start >= words.length) break;
        }

        return chunks;
    }

    /**
     * Returns a portion of the words array from index `from` (inclusive)
     * to `to` (exclusive) and joins them into a String.
     *
     * Java doesn't have a built-in array slice for String[], so we do it manually.
     */
    private String[] subArray(String[] words, int from, int to) {
        String[] slice = new String[to - from];
        System.arraycopy(words, from, slice, 0, to - from);
        return slice;
    }

    // ── Getters for use in tests ──────────────────────────────────────────────

    public int getChunkSize()    { return chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
}
