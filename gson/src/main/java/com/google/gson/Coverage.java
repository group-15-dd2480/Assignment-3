package com.google.gson;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.Position;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

/**
 * Class for performing manual coverage testing.
 */
public final class Coverage {

    // Set of coverage points hit
    private static final Set<String> sampleCalls = new HashSet<>();

    /**
     * Call to create a sample point for coverage
     */
    public static void sample() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // We care about the function that called this function, hence the stack trace efter outselves.
        boolean next = false;
        for (StackTraceElement element : stackTrace) {
            if (next) {
                String[] split = element.getClassName().split("\\.");
                String className = split[split.length - 1];
                sampleCalls.add(className + "#" + element.getMethodName() + ":" + element.getLineNumber());
                break;
            } else if (element.getMethodName().equals("sample")) {
                next = true;
            }
        }
    }

    /**
     * Save collected coverage to a file.
     *
     * Prints a line for each unique coverage sample point hit, as well as where
     * it was hit.
     *
     * @param path filepath to save to
     */
    public static void saveCoverage(String path) {
        File file = new File(path);
        try {
            BufferedWriter writer = Files.newBufferedWriter(file.toPath(), Charset.defaultCharset());
            for (String str : sampleCalls) {
                writer.write(str + "\n");
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate a markdown file with all methods that have coverage sample
     * points.
     *
     * Highlights these points either green or red to indicate if it was hit or
     * not.
     *
     * @param outPath filepath to save to
     */
    public static void generateCoverageMarkdown(String outPath) {

        // Tree of classes with methods that have coverage sample calls.
        Map<String, Map<MethodDeclaration, List<MethodCallExpr>>> tree = new HashMap<>();

        try {
            // Write for writing out markdown
            BufferedWriter writer = new BufferedWriter(new FileWriter(outPath));

            /**
             * Walk all source files to find calls to Coverage.sample
             */
            try (Stream<Path> stream = Files.walk(Paths.get("src/main"))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))) {
                stream.forEach(path -> {
                    try {
                        // Parse file and extract AST information
                        CompilationUnit cu = StaticJavaParser.parse(new File(path.toString()));
                        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classOrInterface -> {
                            classOrInterface.findAll(MethodDeclaration.class).forEach(method -> {
                                method.findAll(MethodCallExpr.class).forEach(call -> {
                                    if (call.getNameAsString().equals("sample")) {
                                        Map<MethodDeclaration, List<MethodCallExpr>> map = tree.computeIfAbsent(classOrInterface.getNameAsString(), k -> new HashMap<>());
                                        List<MethodCallExpr> list = map.computeIfAbsent(method, k -> new ArrayList<>());
                                        list.add(call);
                                    }
                                });
                            });
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            // Write markdown header
            writer.write("```diff\n");
            // For each class with a method containing a call to Coverage.sample
            for (String clazz : tree.keySet()) {
                Set<MethodDeclaration> methods = tree.get(clazz).keySet();
                // For each method containing a call to Coverage.sample
                for (MethodDeclaration method : methods) {
                    List<MethodCallExpr> calls = tree.get(clazz).get(method);

                    // Line numbers containing calls to Coverage.sample in this method, not relative to method start.
                    List<Integer> callLines = calls.stream().map(call -> {
                        return call.getBegin().orElse(Position.HOME).line;
                    }).collect(Collectors.toList());

                    // Begin and end of method body, as line numbers
                    Optional<Position> begin = method.getBegin();
                    Optional<Position> end = method.getEnd();

                    /**
                     * Use a lexical preserving printer to extract the source
                     * code from the methods compilation unit (its class) We
                     * could use method.getBody(), but that ignores empty lines,
                     * leading to miss-alignment between reported call sites and
                     * the ones found in our AST.
                     *
                     * We extract the methods body from the entire classes body
                     * using its begin and end line indices.
                     */
                    String body = "";
                    if (begin.isPresent() && end.isPresent()) {
                        CompilationUnit lpp = LexicalPreservingPrinter.setup(method.findCompilationUnit().get());
                        String[] classBody = LexicalPreservingPrinter.print(lpp).split("\n");
                        int s = begin.get().line;
                        int e = end.get().line;
                        for (int i = s - 1; i < e; i++) {
                            body += classBody[i] + "\n";
                        }
                    }

                    // The key used to check if we have hit a specific Coverage.sample point, minus the line number.
                    String keyPrefix = clazz + "#" + method.getNameAsString() + ":";

                    if (begin.isPresent()) {
                        String[] lines = body.split("\n");
                        int lineNr = begin.get().line;
                        for (String line : lines) {
                            String prefix = " ";

                            // If true, the current line is one with a Coverage.sample call.
                            if (callLines.contains(lineNr)) {
                                // Check if we have actually hit this call point.
                                String key = keyPrefix + lineNr;
                                if (sampleCalls.contains(key)) {
                                    prefix = "+";
                                } else {
                                    prefix = "-";
                                }
                            }
                            writer.write(prefix + " " + lineNr + ":" + line + "\n");
                            lineNr++;
                        }
                    }
                }
                writer.write("\n\n");
            }
            writer.write("```\n");

            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
