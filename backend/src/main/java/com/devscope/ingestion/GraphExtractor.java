package com.devscope.ingestion;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class GraphExtractor {

    private static final Logger log = LoggerFactory.getLogger(GraphExtractor.class);

    public record DependencyEdge(
            String callerFile,
            String callerClass,
            String callerMethod,
            String calleeClass,  // null if unresolvable
            String calleeMethod
    ) {}

    public List<DependencyEdge> extract(Path javaFile) {
        List<DependencyEdge> edges = new ArrayList<>();
        JavaParser parser = new JavaParser();

        try {
            ParseResult<CompilationUnit> result = parser.parse(javaFile);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                log.debug("Could not parse {}: {}", javaFile.getFileName(), result.getProblems());
                return edges;
            }

            CompilationUnit cu = result.getResult().get();
            String fileName = javaFile.getFileName().toString();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String callerClass = classDecl.getNameAsString();

                classDecl.findAll(MethodDeclaration.class).forEach(method -> {
                    String callerMethod = method.getNameAsString();

                    method.findAll(MethodCallExpr.class).forEach(call -> {
                        String calleeMethod = call.getNameAsString();

                        // Best-effort: try to get the scope (e.g. "service.processPayment()")
                        String calleeClass = call.getScope()
                                .map(scope -> scope.toString())
                                .orElse(null);

                        // Skip trivial self-calls (no scope)
                        edges.add(new DependencyEdge(
                                fileName, callerClass, callerMethod,
                                calleeClass, calleeMethod
                        ));
                    });
                });
            });

        } catch (IOException e) {
            log.error("Graph extraction failed for {}: {}", javaFile, e.getMessage());
        }

        return edges;
    }
}
