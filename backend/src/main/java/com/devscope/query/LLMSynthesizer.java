package com.devscope.query;

import com.devscope.model.dto.QueryRequest;
import com.devscope.model.dto.QueryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class LLMSynthesizer {

    private static final Logger log = LoggerFactory.getLogger(LLMSynthesizer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    @Value("${devscope.llm.model:gpt-4o}")
    private String model;

    public LLMSynthesizer(@Qualifier("openAiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public QueryResponse synthesize(String question, QueryRequest.QueryMode mode,
                                    List<VectorSearchService.ScoredChunk> chunks) {
        String chunksContext = buildChunkContext(chunks);
        String systemPrompt = buildSystemPrompt(mode);
        String userMessage = buildUserMessage(question, chunksContext, mode);

        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.2
        );

        try {
            String responseBody = restClient.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(responseBody);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode parsed = MAPPER.readTree(content);

            List<String> relevantFiles = MAPPER.convertValue(
                    parsed.path("relevant_files"), MAPPER.getTypeFactory()
                            .constructCollectionType(List.class, String.class));
            String explanation = parsed.path("explanation").asText("");
            String callGraph = parsed.path("call_graph_summary").asText(null);

            List<QueryResponse.ChunkResult> chunkResults = chunks.stream()
                    .map(sc -> new QueryResponse.ChunkResult(
                            sc.chunk().getFilePath(),
                            sc.chunk().getClassName(),
                            sc.chunk().getMethodName(),
                            sc.chunk().getStartLine() != null ? sc.chunk().getStartLine() : 0,
                            sc.chunk().getEndLine() != null ? sc.chunk().getEndLine() : 0,
                            sc.chunk().getContent(),
                            sc.similarity()
                    )).toList();

            return new QueryResponse(relevantFiles, explanation, callGraph, chunkResults);

        } catch (Exception e) {
            log.error("LLM synthesis failed: {}", e.getMessage());
            // Graceful fallback: return raw chunks without synthesis
            List<String> files = chunks.stream()
                    .map(sc -> sc.chunk().getFilePath())
                    .distinct().toList();
            List<QueryResponse.ChunkResult> chunkResults = chunks.stream()
                    .map(sc -> new QueryResponse.ChunkResult(
                            sc.chunk().getFilePath(),
                            sc.chunk().getClassName(),
                            sc.chunk().getMethodName(),
                            sc.chunk().getStartLine() != null ? sc.chunk().getStartLine() : 0,
                            sc.chunk().getEndLine() != null ? sc.chunk().getEndLine() : 0,
                            sc.chunk().getContent(),
                            sc.similarity()
                    )).toList();
            return new QueryResponse(files, "LLM synthesis unavailable. Showing raw results.", null, chunkResults);
        }
    }

    private String buildSystemPrompt(QueryRequest.QueryMode mode) {
        if (mode == QueryRequest.QueryMode.EXPLAIN_LIKE_NEW) {
            return """
                    You are DevScope, an expert engineering mentor helping new engineers understand a codebase.
                    Given relevant code snippets, explain what's happening in plain English — like you're onboarding a junior engineer.
                    Avoid jargon. Use analogies. Walk through the flow step by step.

                    Respond with valid JSON:
                    {
                      "relevant_files": ["path/to/File.java"],
                      "explanation": "Plain-English walkthrough...",
                      "call_graph_summary": "methodA() → methodB() → methodC()"
                    }
                    """;
        }
        return """
                You are DevScope, an AI assistant that helps engineers understand codebases.
                Given relevant code chunks, answer the user's question precisely.
                Identify the key files, explain the flow, and summarize the call chain if applicable.

                Respond with valid JSON:
                {
                  "relevant_files": ["path/to/File.java", "path/to/Other.java"],
                  "explanation": "Concise explanation of how the code works...",
                  "call_graph_summary": "ControllerA.handleRequest() → ServiceB.process() → RepositoryC.save()"
                }
                """;
    }

    private String buildUserMessage(String question, String chunksContext,
                                    QueryRequest.QueryMode mode) {
        String prefix = mode == QueryRequest.QueryMode.EXPLAIN_LIKE_NEW
                ? "Explain like I'm new to this codebase: "
                : "";
        return """
                Question: %s%s

                Relevant code from the codebase:
                ---
                %s
                ---

                Answer the question based on these code chunks.
                """.formatted(prefix, question, chunksContext);
    }

    private String buildChunkContext(List<VectorSearchService.ScoredChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            var sc = chunks.get(i);
            var c = sc.chunk();
            sb.append("### Chunk %d | %s".formatted(i + 1, c.getFilePath()));
            if (c.getClassName() != null) sb.append(" | class: ").append(c.getClassName());
            if (c.getMethodName() != null) sb.append(" | method: ").append(c.getMethodName());
            sb.append(" (similarity: %.3f)\n".formatted(sc.similarity()));
            sb.append(c.getContent(), 0, Math.min(c.getContent().length(), 1500));
            sb.append("\n\n");
        }
        return sb.toString();
    }
}
