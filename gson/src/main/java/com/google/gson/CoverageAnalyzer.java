package com.google.gson;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
@SuppressWarnings("unchecked")
public class CoverageAnalyzer {
    private final Map<String, List<Integer>> coveragePoints = new HashMap<>();

    public void analyzeSource(String srcPath) throws IOException {
        Files.walk(Paths.get(srcPath))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> parseFile(path.toFile()));
    }

    private void parseFile(File file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            cu.findAll(MethodDeclaration.class).forEach(method -> {
                List<Integer> lineNumbers = new ArrayList<>();
                method.findAll(MethodCallExpr.class).forEach(call -> {
                    if (call.getNameAsString().equals("sample")) {
                        call.getBegin().ifPresent(pos -> lineNumbers.add(pos.line));
                    }
                });
                if (!lineNumbers.isEmpty()) {
                    coveragePoints.put(method.getNameAsString(), lineNumbers);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<String, List<Integer>> getCoveragePoints() {
        return coveragePoints;
    }
}

