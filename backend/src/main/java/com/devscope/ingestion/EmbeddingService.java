package com.devscope.ingestion;

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
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_INPUT_CHARS = 30_000; // ~8k tokens safety limit

    private final RestClient restClient;

    @Value("${devscope.embedding.model:text-embedding-3-small}")
    private String model;

    @Value("${devscope.embedding.batch-size:100}")
    private int batchSize;

    public EmbeddingService(@Qualifier("openAiRestClient") RestClient restClient) {
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
        Map<String, Object> request = Map.of("model", model, "input", truncated);

        try {
            String responseBody = restClient.post()
                    .uri("/embeddings")
                    .body(request)
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(responseBody);
            JsonNode data = root.get("data");

            float[][] ordered = new float[texts.size()][];
            for (JsonNode item : data) {
                int index = item.get("index").asInt();
                JsonNode embeddingNode = item.get("embedding");
                float[] vec = new float[embeddingNode.size()];
                for (int j = 0; j < embeddingNode.size(); j++) {
                    vec[j] = (float) embeddingNode.get(j).asDouble();
                }
                ordered[index] = vec;
            }

            List<float[]> result = new ArrayList<>();
            for (float[] v : ordered) {
                if (v != null) result.add(v);
            }
            return result;

        } catch (Exception e) {
            log.error("Embedding batch failed: {}", e.getMessage());
            // Return zero vectors so the pipeline doesn't abort entirely
            return texts.stream().map(t -> new float[1536]).toList();
        }
    }

    private String truncate(String text) {
        return text.length() > MAX_INPUT_CHARS ? text.substring(0, MAX_INPUT_CHARS) : text;
    }
}
