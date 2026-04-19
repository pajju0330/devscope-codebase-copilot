package com.devscope.query;

import com.devscope.ingestion.EmbeddingService;
import com.devscope.model.dto.QueryRequest;
import com.devscope.model.dto.QueryResponse;
import com.devscope.model.entity.DependencyEdgeEntity;
import com.devscope.model.repository.DependencyEdgeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class QueryService {

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final LLMSynthesizer llmSynthesizer;
    private final DependencyEdgeRepository edgeRepo;

    @Value("${devscope.query.top-k:10}")
    private int topK;

    public QueryService(EmbeddingService embeddingService,
                        VectorSearchService vectorSearchService,
                        LLMSynthesizer llmSynthesizer,
                        DependencyEdgeRepository edgeRepo) {
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.llmSynthesizer = llmSynthesizer;
        this.edgeRepo = edgeRepo;
    }

    public QueryResponse query(QueryRequest request) {
        // 1. Embed the question
        float[] queryEmbedding = embeddingService.embedSingle(request.question());

        // 2. Vector similarity search
        List<VectorSearchService.ScoredChunk> chunks =
                vectorSearchService.findSimilar(request.repoId(), queryEmbedding, topK);

        if (chunks.isEmpty()) {
            return new QueryResponse(List.of(),
                    "No relevant code found. Make sure the repo has been fully ingested.",
                    null, List.of());
        }

        // 3. Enrich with graph context — 1-hop neighbors from matched classes
        chunks = enrichWithGraphContext(request.repoId(), chunks);

        // 4. LLM synthesis
        return llmSynthesizer.synthesize(request.question(), request.mode(), chunks);
    }

    /**
     * Appends call graph context to the chunks by finding 1-hop edges involving
     * the matched classes. This surfaces closely related code not directly matched.
     */
    private List<VectorSearchService.ScoredChunk> enrichWithGraphContext(
            UUID repoId, List<VectorSearchService.ScoredChunk> chunks) {

        List<String> matchedClasses = chunks.stream()
                .map(sc -> sc.chunk().getClassName())
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();

        if (matchedClasses.isEmpty()) return chunks;

        List<DependencyEdgeEntity> relatedEdges =
                edgeRepo.findEdgesInvolvingClasses(repoId, matchedClasses);

        if (relatedEdges.isEmpty()) return chunks;

        // Build a compact call-graph annotation (appended to last chunk's content later in LLM)
        // For now just return original chunks — the graph is used by LLMSynthesizer via the
        // /graph endpoint and the explanation prompt includes the call graph summary
        return chunks;
    }

    public List<DependencyEdgeEntity> getCallGraph(UUID repoId, String className) {
        List<DependencyEdgeEntity> outgoing =
                edgeRepo.findByRepoIdAndCallerClassAndCallerMethod(repoId, className, null)
                        .stream().filter(e -> e.getCallerClass() != null).toList();

        List<DependencyEdgeEntity> allEdges = edgeRepo.findEdgesInvolvingClasses(
                repoId, List.of(className));

        return Stream.concat(outgoing.stream(), allEdges.stream())
                .distinct().toList();
    }
}
