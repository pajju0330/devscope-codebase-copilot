package com.devscope.query;

import com.devscope.model.dto.GeminiContent;
import com.devscope.model.dto.GeminiGenerateContentRequest;
import com.devscope.model.dto.GeminiGenerationConfig;
import com.devscope.model.dto.GeminiPart;
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

import java.util.ArrayList;
import java.util.List;

@Service
public class LLMSynthesizer {

    private static final Logger log = LoggerFactory.getLogger(LLMSynthesizer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    @Value("${devscope.llm.model:models/gemini-3-flash}")
    private String model;

    @Value("${devscope.llm.temperature:0.0}")
    private double temperature;

    @Value("${devscope.llm.max-output-tokens:1000}")
    private int maxOutputTokens;

    public LLMSynthesizer(@Qualifier("llmRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public QueryResponse synthesize(String question, QueryRequest.QueryMode mode,
                                    List<VectorSearchService.ScoredChunk> chunks) {
        String chunksContext = buildChunkContext(chunks);
        String systemPrompt = buildSystemPrompt(mode);
        String userMessage = buildUserMessage(question, chunksContext, mode);

        GeminiGenerateContentRequest request = GeminiGenerateContentRequest.builder()
                .systemInstruction(GeminiContent.builder()
                        .parts(List.of(GeminiPart.builder().text(systemPrompt).build()))
                        .build())
                .contents(List.of(GeminiContent.builder()
                        .role("user")
                        .parts(List.of(GeminiPart.builder().text(userMessage).build()))
                        .build()))
                .generationConfig(GeminiGenerationConfig.builder()
                        .temperature(temperature)
                        .maxOutputTokens(maxOutputTokens)
                        .build())
                .build();

        StringBuilder sb = new StringBuilder();
        sb.append("/v1beta/");
        sb.append(model);
        sb.append(":generateContent");

        try {
            String responseBody = restClient.post()
                    .uri(sb.toString())
                    .body(request)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(responseBody);
            String content = extractGeminiText(root);
            JsonNode parsed = MAPPER.readTree(stripJsonFence(content));

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
                    You are a strict RAG assistant. Only use provided context.
                    You are DevScope, an expert engineering mentor helping new engineers understand a codebase.
                    Given relevant code snippets, explain what's happening in plain English - like you're onboarding a junior engineer.
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
                You are a strict RAG assistant. Only use provided context.
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
                CONTEXT:
                %s

                USER QUESTION: %s%s
                """.formatted(chunksContext, prefix, question);
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

    private String extractGeminiText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IllegalStateException("Gemini response did not contain generated text");
        }

        List<String> texts = new ArrayList<>();
        for (JsonNode part : parts) {
            String text = part.path("text").asText("");
            if (!text.isBlank()) {
                texts.add(text);
            }
        }

        if (texts.isEmpty()) {
            throw new IllegalStateException("Gemini response text was empty");
        }
        return String.join("\n", texts);
    }

    private String stripJsonFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstNewLine = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewLine < 0 || lastFence <= firstNewLine) {
            return trimmed;
        }
        return trimmed.substring(firstNewLine + 1, lastFence).trim();
    }
}
