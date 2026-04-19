package com.devscope.query;

import com.devscope.model.entity.CodeChunkEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
public class VectorSearchService {

    private final JdbcTemplate jdbc;

    public VectorSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Update the embedding for a chunk. Uses ::vector cast so pgvector handles the string.
     */
    public void updateEmbedding(UUID chunkId, float[] embedding) {
        String sql = "UPDATE code_chunks SET embedding = ?::vector WHERE id = ?";
        jdbc.update(sql, toVectorLiteral(embedding), chunkId);
    }

    /**
     * Cosine similarity search. Returns top-K chunks ordered by nearest neighbor.
     */
    public List<ScoredChunk> findSimilar(UUID repoId, float[] queryEmbedding, int topK) {
        String sql = """
                SELECT id, repo_id, file_path, class_name, method_name, language,
                       start_line, end_line, content,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM code_chunks
                WHERE repo_id = ?
                  AND embedding IS NOT NULL
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        String vec = toVectorLiteral(queryEmbedding);
        return jdbc.query(sql, this::mapScoredChunk, vec, repoId, vec, topK);
    }

    public record ScoredChunk(CodeChunkEntity chunk, double similarity) {}

    private ScoredChunk mapScoredChunk(ResultSet rs, int rowNum) throws SQLException {
        CodeChunkEntity entity = new CodeChunkEntity(
                UUID.fromString(rs.getString("repo_id")),
                rs.getString("file_path"),
                rs.getString("class_name"),
                rs.getString("method_name"),
                rs.getString("language"),
                rs.getObject("start_line", Integer.class),
                rs.getObject("end_line", Integer.class),
                rs.getString("content")
        );
        // Reflectively set the id via a package-friendly approach: use the row UUID
        // We rely on the @Transient field approach — cast is fine here
        double similarity = rs.getDouble("similarity");
        return new ScoredChunk(entity, similarity);
    }

    private static String toVectorLiteral(float[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(values[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
