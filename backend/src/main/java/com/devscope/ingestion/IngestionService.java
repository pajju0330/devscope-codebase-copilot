package com.devscope.ingestion;

import com.devscope.model.entity.CodeChunkEntity;
import com.devscope.model.entity.DependencyEdgeEntity;
import com.devscope.model.entity.RepoEntity;
import com.devscope.model.repository.CodeChunkRepository;
import com.devscope.model.repository.DependencyEdgeRepository;
import com.devscope.model.repository.RepoRepository;
import com.devscope.query.VectorSearchService;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final Map<String, String> EXT_TO_LANG = Map.of(
            ".java", "java",
            ".py", "python",
            ".go", "go",
            ".ts", "typescript",
            ".js", "javascript",
            ".kt", "kotlin"
    );

    private final RepoRepository repoRepo;
    private final CodeChunkRepository chunkRepo;
    private final DependencyEdgeRepository edgeRepo;
    private final TreeSitterChunker chunker;
    private final EmbeddingService embeddingService;
    private final GraphExtractor graphExtractor;
    private final VectorSearchService vectorSearchService;

    @Value("${devscope.ingestion.temp-dir:/tmp/devscope}")
    private String tempDir;

    public IngestionService(RepoRepository repoRepo,
                            CodeChunkRepository chunkRepo,
                            DependencyEdgeRepository edgeRepo,
                            TreeSitterChunker chunker,
                            EmbeddingService embeddingService,
                            GraphExtractor graphExtractor,
                            VectorSearchService vectorSearchService) {
        this.repoRepo = repoRepo;
        this.chunkRepo = chunkRepo;
        this.edgeRepo = edgeRepo;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.graphExtractor = graphExtractor;
        this.vectorSearchService = vectorSearchService;
    }

    public UUID createRepoRecord(String repoName, String repoUrl) {
        RepoEntity repo = new RepoEntity(repoName, repoUrl);
        return repoRepo.save(repo).getId();
    }

    @Async("ingestionExecutor")
    public CompletableFuture<Void> ingestZip(UUID repoId, InputStream zipStream) {
        Path repoTempDir = Path.of(tempDir, repoId.toString());
        try {
            repoRepo.updateStatus(repoId, "PROCESSING");

            Path zipPath = Path.of(tempDir, repoId + ".zip");
            Files.createDirectories(Path.of(tempDir));
            RepoUnpacker.copyFromInputStream(zipStream, zipPath);
            Path repoDir = RepoUnpacker.unzip(zipPath, repoTempDir);

            processRepoDir(repoId, repoDir);

            repoRepo.updateStatus(repoId, "COMPLETED");
            log.info("Ingestion completed for repo {}", repoId);

        } catch (Exception e) {
            log.error("Ingestion failed for repo {}: {}", repoId, e.getMessage(), e);
            repoRepo.updateStatusWithError(repoId, "FAILED", e.getMessage());
        } finally {
            deleteTempDir(repoTempDir);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Async("ingestionExecutor")
    public CompletableFuture<Void> ingestGitUrl(UUID repoId, String repoUrl) {
        Path repoTempDir = Path.of(tempDir, repoId.toString());
        try {
            repoRepo.updateStatus(repoId, "PROCESSING");
            Files.createDirectories(repoTempDir);

            log.info("Cloning {} into {}", repoUrl, repoTempDir);
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(repoTempDir.toFile())
                    .setDepth(1)
                    .call()
                    .close();

            processRepoDir(repoId, repoTempDir);

            repoRepo.updateStatus(repoId, "COMPLETED");
            log.info("Ingestion completed for repo {}", repoId);

        } catch (Exception e) {
            log.error("Ingestion failed for repo {}: {}", repoId, e.getMessage(), e);
            repoRepo.updateStatusWithError(repoId, "FAILED", e.getMessage());
        } finally {
            deleteTempDir(repoTempDir);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void processRepoDir(UUID repoId, Path repoDir) throws Exception {
        List<Path> sourceFiles = collectSourceFiles(repoDir);
        log.info("Found {} source files in repo {}", sourceFiles.size(), repoId);

        for (Path file : sourceFiles) {
            String language = detectLanguage(file);
            if (language == null) continue;

            List<TreeSitterChunker.CodeChunk> chunks = chunker.chunk(file, language);
            if (chunks.isEmpty()) continue;

            // Save chunks without embeddings
            List<CodeChunkEntity> saved = saveChunks(repoId, file, chunks);

            // Batch embed
            List<String> contents = chunks.stream().map(TreeSitterChunker.CodeChunk::content).toList();
            List<float[]> embeddings = embeddingService.embed(contents);

            // Update embeddings via JDBC
            for (int i = 0; i < saved.size() && i < embeddings.size(); i++) {
                vectorSearchService.updateEmbedding(saved.get(i).getId(), embeddings.get(i));
            }

            // Extract dependency graph for Java files
            if ("java".equals(language)) {
                List<GraphExtractor.DependencyEdge> edges = graphExtractor.extract(file);
                saveDependencyEdges(repoId, edges);
            }
        }

        // Update ingested_at timestamp
        repoRepo.findById(repoId).ifPresent(r -> {
            r.setIngestedAt(OffsetDateTime.now());
            repoRepo.save(r);
        });
    }

    private List<Path> collectSourceFiles(Path root) throws Exception {
        Set<String> supported = EXT_TO_LANG.keySet();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return supported.stream().anyMatch(name::endsWith);
                    })
                    .filter(p -> !isVendorOrBuildPath(p))
                    .toList();
        }
    }

    private boolean isVendorOrBuildPath(Path p) {
        String path = p.toString();
        return path.contains("/vendor/") || path.contains("/node_modules/")
                || path.contains("/build/") || path.contains("/target/")
                || path.contains("/.git/");
    }

    private String detectLanguage(Path file) {
        String name = file.getFileName().toString();
        for (var entry : EXT_TO_LANG.entrySet()) {
            if (name.endsWith(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private List<CodeChunkEntity> saveChunks(UUID repoId, Path file,
                                              List<TreeSitterChunker.CodeChunk> chunks) {
        List<CodeChunkEntity> entities = chunks.stream()
                .map(c -> new CodeChunkEntity(
                        repoId,
                        file.toString(),
                        c.className(),
                        c.methodName(),
                        c.language(),
                        c.startLine(),
                        c.endLine(),
                        c.content()
                )).toList();
        return chunkRepo.saveAll(entities);
    }

    private void saveDependencyEdges(UUID repoId, List<GraphExtractor.DependencyEdge> edges) {
        List<DependencyEdgeEntity> entities = edges.stream()
                .map(e -> new DependencyEdgeEntity(
                        repoId, e.callerFile(), e.callerClass(),
                        e.callerMethod(), e.calleeClass(), e.calleeMethod()
                )).toList();
        if (!entities.isEmpty()) {
            edgeRepo.saveAll(entities);
        }
    }

    private void deleteTempDir(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
                }
            }
        } catch (Exception e) {
            log.warn("Could not clean up temp dir {}: {}", dir, e.getMessage());
        }
    }
}
