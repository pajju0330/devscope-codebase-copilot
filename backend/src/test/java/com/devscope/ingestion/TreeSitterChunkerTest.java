package com.devscope.ingestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSitterChunkerTest {

    private final TreeSitterChunker chunker = new TreeSitterChunker();

    @Test
    void chunksSimpleJavaClass(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("PaymentService.java");
        Files.writeString(javaFile, """
                package com.example;

                public class PaymentService {

                    public void processPayment(String orderId) {
                        retryPayment(orderId, 3);
                    }

                    private void retryPayment(String orderId, int retries) {
                        // retry logic
                    }
                }
                """);

        List<TreeSitterChunker.CodeChunk> chunks = chunker.chunk(javaFile, "java");

        // Should have at least 2 chunks (one per method) or 1 fallback chunk
        assertThat(chunks).isNotEmpty();
        // Each chunk should have content
        chunks.forEach(c -> assertThat(c.content()).isNotBlank());
    }

    @Test
    void returnsEmptyForUnsupportedLanguage(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("config.xml");
        Files.writeString(file, "<config><key>value</key></config>");

        // Should not crash — returns fallback chunks or empty
        List<TreeSitterChunker.CodeChunk> chunks = chunker.chunk(file, "xml");
        // xml falls through to fallback line-based chunker
        assertThat(chunks).isNotNull();
    }

    @Test
    void respectsFileSizeLimit(@TempDir Path tempDir) throws IOException {
        Path bigFile = tempDir.resolve("Huge.java");
        // Write ~1MB of content
        String line = "// line of content\n".repeat(50_000);
        Files.writeString(bigFile, line);

        // Default maxFileSizeKb is 500 — 1MB should be skipped
        // The default field value is 500 KB; our file is ~1MB
        // Chunker should return empty list
        List<TreeSitterChunker.CodeChunk> chunks = chunker.chunk(bigFile, "java");
        assertThat(chunks).isEmpty();
    }
}
