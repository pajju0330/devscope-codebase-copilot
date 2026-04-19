package com.devscope.api;

import com.devscope.model.dto.QueryRequest;
import com.devscope.model.dto.QueryResponse;
import com.devscope.query.QueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/query")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Ask a question about the codebase.
     *
     * POST /query
     * {
     *   "repoId": "uuid",
     *   "question": "Where is payment retry handled?",
     *   "mode": "STANDARD" | "EXPLAIN_LIKE_NEW"
     * }
     */
    @PostMapping
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        QueryResponse response = queryService.query(request);
        return ResponseEntity.ok(response);
    }
}
