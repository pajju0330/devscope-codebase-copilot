package com.devscope.api;

import com.devscope.ingestion.IngestionService;
import com.devscope.model.dto.IngestResponse;
import com.devscope.model.repository.RepoRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/repos")
public class IngestController {

    private final IngestionService ingestionService;
    private final RepoRepository repoRepository;

    public IngestController(IngestionService ingestionService, RepoRepository repoRepository) {
        this.ingestionService = ingestionService;
        this.repoRepository = repoRepository;
    }

    /**
     * List all repositories.
     * GET /repos
     */
    @GetMapping
    public ResponseEntity<List<IngestResponse>> listRepos() {
        List<IngestResponse> repos = repoRepository.findAll().stream()
                .map(r -> new IngestResponse(r.getId(), r.getStatus(),
                        r.getError() != null ? r.getError() : "Ready",
                        r.getName(),
                        r.getUrl()))
                .toList();
        return ResponseEntity.ok(repos);
    }

    /**
     * Upload a zip file for ingestion.
     * POST /repos/ingest/zip
     * multipart: file (zip), repoName (string)
     */
    @PostMapping(value = "/ingest/zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestResponse> ingestZip(
            @RequestPart("file") MultipartFile file,
            @RequestPart("repoName") @NotBlank String repoName) {

        if (file.isEmpty()) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Zip file is empty");
            return ResponseEntity.of(pd).build();
        }

        UUID repoId = ingestionService.createRepoRecord(repoName, null);

        try {
            ingestionService.ingestZip(repoId, file.getInputStream());
        } catch (Exception e) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to start ingestion: " + e.getMessage());
            return ResponseEntity.of(pd).build();
        }

        return ResponseEntity.accepted()
                .body(new IngestResponse(repoId, "PROCESSING",
                        "Ingestion started. Poll GET /repos/" + repoId + "/status for updates.",
                        repoName,
                        null));
    }

    /**
     * Ingest from a public Git URL.
     * POST /repos/ingest/git
     * Body: { "repoName": "my-repo", "repoUrl": "https://github.com/..." }
     */
    @PostMapping("/ingest/git")
    public ResponseEntity<IngestResponse> ingestGit(@RequestBody GitIngestRequest request) {
        if (request.repoUrl() == null || request.repoUrl().isBlank()) {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "repoUrl is required");
            return ResponseEntity.of(pd).build();
        }

        String repoUrl = request.repoUrl().trim();

        return repoRepository.findFirstByUrl(repoUrl)
                .map(existing -> ResponseEntity.ok(
                        new IngestResponse(existing.getId(), existing.getStatus(), "Already ingested",
                                existing.getName(), existing.getUrl())))
                .orElseGet(() -> {
                    UUID repoId;
                    try {
                        repoId = ingestionService.createRepoRecord(request.repoName(), repoUrl);
                    } catch (DataIntegrityViolationException e) {
                        return repoRepository.findFirstByUrl(repoUrl)
                                .map(existing -> ResponseEntity.ok(
                                        new IngestResponse(existing.getId(), existing.getStatus(), "Already ingested",
                                                existing.getName(), existing.getUrl())))
                                .orElseGet(() -> {
                                    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                                            "Failed to create repo record: " + e.getMostSpecificCause().getMessage());
                                    return ResponseEntity.of(pd).build();
                                });
                    }

                    ingestionService.ingestGitUrl(repoId, repoUrl);

                    return ResponseEntity.accepted()
                            .body(new IngestResponse(repoId, "PROCESSING",
                                    "Ingestion started. Poll GET /repos/" + repoId + "/status for updates.",
                                    request.repoName(),
                                    repoUrl));
                });
    }

    /**
     * Check ingestion status.
     * GET /repos/{repoId}/status
     */
    @GetMapping("/{repoId}/status")
    public ResponseEntity<IngestResponse> getStatus(@PathVariable UUID repoId) {
        return repoRepository.findById(repoId)
                .map(r -> ResponseEntity.ok(new IngestResponse(r.getId(), r.getStatus(),
                        r.getError() != null ? r.getError() : "Chunks indexed: check /query to search",
                        r.getName(),
                        r.getUrl())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record GitIngestRequest(String repoName, String repoUrl) {}
}
