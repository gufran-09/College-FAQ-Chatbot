package com.example.demo.service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.Loader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * STEP 3a — Extract raw text from uploaded files.
 *
 * Supports:
 *   - PDF  → Apache PDFBox
 *   - DOCX → Apache POI
 *
 * Returns a single String of the full document text.
 * The ChunkingService will then split this into smaller pieces.
 */
@Service
public class FileReaderService {

    /**
     * Main entry point. Detect file type from the filename
     * and delegate to the right reader.
     */
    public String extractText(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("File has no name");
        }

        return extractText(fileName, file.getInputStream());
    }

    public String extractText(String fileName, byte[] contents) throws IOException {
        return extractText(fileName, new ByteArrayInputStream(contents));
    }

    private String extractText(String fileName, InputStream inputStream) throws IOException {
        String lower = fileName.toLowerCase();

        if (lower.endsWith(".pdf")) {
            return extractFromPdf(inputStream);
        } else if (lower.endsWith(".docx")) {
            return extractFromDocx(inputStream);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + fileName +
                            ". Only PDF and DOCX are supported."
            );
        }
    }

    // ── PDF reader ────────────────────────────────────────────────────────────

    /**
     * PDFBox opens the PDF and strips out all visible text, page by page.
     *
     * PDDocument implements AutoCloseable, so the try-with-resources block
     * automatically closes it when done — no memory leaks.
     */
    private String extractFromPdf(InputStream inputStream) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();

            // Sort text by position on the page (top to bottom, left to right)
            // This matters for multi-column PDFs
            stripper.setSortByPosition(true);

            String text = stripper.getText(pdf);

            // Clean up excessive whitespace produced by PDFBox
            return cleanText(text);
        }
    }

    // ── DOCX reader ───────────────────────────────────────────────────────────

    /**
     * Apache POI reads DOCX files as a list of XWPFParagraph objects.
     * We join them with newlines to preserve paragraph structure.
     *
     * Note: This reads body text only. Headers, footers, and tables
     * are handled separately if needed later.
     */
    private String extractFromDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument docx = new XWPFDocument(inputStream)) {

            List<XWPFParagraph> paragraphs = docx.getParagraphs();

            String text = paragraphs.stream()
                    .map(XWPFParagraph::getText)     // get text from each paragraph
                    .filter(t -> !t.isBlank())       // skip empty paragraphs
                    .collect(Collectors.joining("\n"));

            return cleanText(text);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Removes extra whitespace, multiple blank lines, and non-printable chars.
     * Clean text = better chunks = better embeddings = better answers.
     */
    private String cleanText(String raw) {
        if (raw == null) return "";

        return raw
                // Replace tabs and non-breaking spaces with regular space
                .replaceAll("[\t\u00A0]", " ")
                // Collapse 3+ blank lines into 2 (preserve paragraph breaks)
                .replaceAll("\n{3,}", "\n\n")
                // Collapse multiple spaces into one
                .replaceAll(" {2,}", " ")
                // Remove non-printable characters
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .trim();
    }
}
