package com.devscope.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class TreeSitterChunker {

    private static final Logger log = LoggerFactory.getLogger(TreeSitterChunker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${devscope.ingestion.max-file-size-kb:500}")
    private long maxFileSizeKb;

    private final Path chunkerScript;

    public TreeSitterChunker() {
        // Resolve chunker.py relative to the project root
        this.chunkerScript = Path.of("scripts/chunker.py").toAbsolutePath();
    }

    public record CodeChunk(
            String content,
            String className,
            String methodName,
            int startLine,
            int endLine,
            String language
    ) {}

    public List<CodeChunk> chunk(Path filePath, String language) {
        long fileSizeKb = filePath.toFile().length() / 1024;
        if (fileSizeKb > maxFileSizeKb) {
            log.warn("Skipping large file ({} KB): {}", fileSizeKb, filePath);
            return List.of();
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python3", chunkerScript.toString(),
                    filePath.toAbsolutePath().toString(),
                    language
            );
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Chunker timed out for: {}", filePath);
                return List.of();
            }

            if (!stderr.isBlank()) {
                log.debug("Chunker stderr for {}: {}", filePath.getFileName(), stderr.trim());
            }

            List<Map<String, Object>> raw = MAPPER.readValue(stdout,
                    new TypeReference<>() {});
            return raw.stream().map(m -> new CodeChunk(
                    str(m, "content"),
                    str(m, "class_name"),
                    str(m, "method_name"),
                    intVal(m, "start_line"),
                    intVal(m, "end_line"),
                    language
            )).toList();

        } catch (IOException | InterruptedException e) {
            log.error("Chunker failed for {}: {}", filePath, e.getMessage());
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof String s ? s : null;
    }

    private static int intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }
}
