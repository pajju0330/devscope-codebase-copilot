package com.devscope.ingestion;

import com.devscope.model.dto.GeminiEmbeddingRequest;
import com.devscope.model.dto.GeminiContent;
import com.devscope.model.dto.GeminiPart;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_INPUT_CHARS = 30_000; // ~8k tokens safety limit

    private final RestClient restClient;

    @Value("${devscope.embedding.model:text-embedding-3-small}")
    private String model;

    @Value("${devscope.llm.api-key}")           // Changed property name (recommended)
    private String llmApiKey;

    @Value("${devscope.embedding.batch-size:100}")
    private int batchSize;

    @Value("${devscope.embedding.max-retries:3}")
    private int maxRetries;

    @Value("${devscope.embedding.initial-backoff-ms:1000}")
    private long initialBackoffMs;

    @Value("${devscope.embedding.max-backoff-ms:8000}")
    private long maxBackoffMs;

    public EmbeddingService(@Qualifier("llmRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Embeds a list of texts in batches. Returns embeddings in the same order.
     */
    public List<float[]> embed(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            results.addAll(embedBatch(batch));
        }
        return results;
    }

    public float[] embedSingle(String text) {
        List<float[]> result = embedBatch(List.of(truncate(text)));
        return result.isEmpty() ? new float[0] : result.get(0);
    }

    private List<float[]> embedBatch(List<String> texts) {
        List<String> truncated = texts.stream().map(this::truncate).toList();
        List<GeminiEmbeddingRequest> requestData = new ArrayList<>();
        truncated.forEach(
                text -> {
                    GeminiEmbeddingRequest request = GeminiEmbeddingRequest.builder().content(
                            GeminiContent.builder().parts(
                                    List.of(GeminiPart.builder().text(text).build())
                            ).build()
                    ).model(model).build();
                    requestData.add(request);
                }
        );
        Map<String, Object> request = Map.of("requests", requestData);
        StringBuilder sb = new StringBuilder();
        sb.append("/v1beta/");
        sb.append(model);
        sb.append(":batchEmbedContents");
        try {
            String responseBody = executeEmbeddingRequestWithRetry(sb.toString(), request);

            JsonNode root = MAPPER.readTree(responseBody);
            List<float[]> result = new ArrayList<>();

            for (int i = 0; i < root.get("embeddings").size(); ++i){
                ArrayNode values = (ArrayNode) root.get("embeddings").get(i).get("values");
                float[] arr = new float[values.size()];
                for(int j = 0; j < values.size(); ++j){
                    arr[j] = (float) values.get(j).asDouble();
                }
                result.add(arr);
            }
            return result;

        } catch (Exception e) {
            log.error("Embedding batch failed: {}", e.getMessage());
            // Return zero vectors so the pipeline doesn't abort entirely
            return texts.stream().map(t -> new float[1536]).toList();
        }
    }

    private String executeEmbeddingRequestWithRetry(String uri, Map<String, Object> request) {
        int attempt = 0;
        while (true) {
            try {
                return restClient.post()
                        .uri(uri)
                        .body(request)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException e) {
                if (!isTooManyRequests(e) || attempt >= maxRetries) {
                    throw e;
                }

                long delayMs = backoffDelayMs(attempt);
                attempt++;
                log.warn("Embedding batch rate limited. Retrying attempt {}/{} after {} ms",
                        attempt, maxRetries, delayMs);
                sleep(delayMs);
            }
        }
    }

    private boolean isTooManyRequests(RestClientResponseException e) {
        return e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
    }

    private long backoffDelayMs(int retryIndex) {
        long exponentialDelay = initialBackoffMs * (1L << Math.min(retryIndex, 30));
        long cappedDelay = Math.min(exponentialDelay, maxBackoffMs);
        long jitterMs = ThreadLocalRandom.current().nextLong(0, 251);
        return cappedDelay + jitterMs;
    }

    private void sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off embedding retry", e);
        }
    }

    private String truncate(String text) {
        return text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;
    }
}
