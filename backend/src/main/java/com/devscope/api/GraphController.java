package com.devscope.api;

import com.devscope.model.entity.DependencyEdgeEntity;
import com.devscope.query.QueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/graph")
public class GraphController {

    private final QueryService queryService;

    public GraphController(QueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * Get the call graph (edges) for a given class.
     * GET /graph/{repoId}/{className}
     *
     * Returns all edges where the class is a caller or callee.
     */
    @GetMapping("/{repoId}/{className}")
    public ResponseEntity<GraphResponse> getCallGraph(
            @PathVariable UUID repoId,
            @PathVariable String className) {

        List<DependencyEdgeEntity> edges = queryService.getCallGraph(repoId, className);

        List<GraphResponse.Edge> edgeDtos = edges.stream()
                .map(e -> new GraphResponse.Edge(
                        e.getCallerClass(), e.getCallerMethod(),
                        e.getCalleeClass(), e.getCalleeMethod()
                )).toList();

        return ResponseEntity.ok(new GraphResponse(className, edgeDtos));
    }

    public record GraphResponse(
            String className,
            List<Edge> edges
    ) {
        public record Edge(
                String callerClass,
                String callerMethod,
                String calleeClass,
                String calleeMethod
        ) {}
    }
}
