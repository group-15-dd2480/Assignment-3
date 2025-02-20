package com.google.gson;

import com.github.javaparser.Position;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Class for performing manual coverage testing.
 */
public class Coverage {

    // Stack walker to extract location of coverage points
    private static final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    // Set of coverage points hit
    private static final Set<String> sampleCalls = new HashSet<>();

    /**
     * Call to create a sample point for coverage
     */
    public static void sample() {
        walker.walk(frames -> frames.skip(1)
                .findFirst()
                .map(frame -> {
                    sampleCalls.add(frame.getDeclaringClass().getSimpleName() + "#" + frame.getMethodName() + ":" + frame.getLineNumber());
                    return frame;
                }));
    }

    /**
     * Save collected coverage to a file.
     *
     * Prints a line for each unique coverage sample point hit, as well as where it was hit.
     *
     * @param path filepath to save to
     */
    public static void saveCoverage(String path) {
        File file = new File(path);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
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
     * Generate a markdown file with all methods that have coverage sample points.
     *
     * Highlights these points either green or red to indicate if it was hit or not.
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
            Files.walk(Paths.get("src/main"))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            // Parse file and extract AST information
                            CompilationUnit cu = StaticJavaParser.parse(new File(path.toString()));
                            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classOrInterface -> {
                                classOrInterface.findAll(MethodDeclaration.class).forEach(method -> {
                                    method.findAll(MethodCallExpr.class).forEach(call -> {
                                        if (call.getNameAsString().equals("sample")) {
                                            var map = tree.computeIfAbsent(classOrInterface.getNameAsString(), k -> new HashMap<>());
                                            var list = map.computeIfAbsent(method, k -> new ArrayList<>());
                                            list.add(call);
                                        }
                                    });
                                });
                            });
                        } catch (Exception e ){
                            e.printStackTrace();
                        }
                    });

            // Write markdown header
            writer.write("```diff\n");
            // For each class with a method containing a call to Coverage.sample
            for (var clazz : tree.keySet()) {
                var methods = tree.get(clazz).keySet();
                // For each method containing a call to Coverage.sample
                for (var method : methods) {
                    var calls = tree.get(clazz).get(method);

                    // Line numbers containing calls to Coverage.sample in this method, not relative to method start.
                    var callLines = calls.stream().map(call -> {
                        return call.getBegin().orElse(Position.HOME).line;
                    }).collect(Collectors.toList());

                    // Begin and end of method body, as line numbers
                    var begin = method.getBegin();
                    var end = method.getEnd();

                    /**
                     * Use a lexical preserving printer to extract the source code from the methods compilation unit (its class)
                     * We could use method.getBody(), but that ignores empty lines, leading to miss-alignment between reported call sites and the ones found in our AST.
                     *
                     * We extract the methods body from the entire classes body using its begin and end line indices.
                     */
                    var body = "";
                    if (begin.isPresent() && end.isPresent()) {
                        CompilationUnit lpp = LexicalPreservingPrinter.setup(method.findCompilationUnit().get());
                        var classBody = LexicalPreservingPrinter.print(lpp).lines();
                        var s = begin.get().line;
                        var e = end.get().line;
                        body = classBody.skip(s - 1).limit(e - s + 1).collect(Collectors.joining("\n"));
                    }

                    // The key used to check if we have hit a specific Coverage.sample point, minus the line number.
                    var keyPrefix = clazz + "#" + method.getNameAsString() + ":";

                    if (begin.isPresent()) {
                        var lines = body.lines().collect(Collectors.toList());
                        var lineNr = begin.get().line;
                        for (var line : lines) {
                            var prefix = " ";

                            // If true, the current line is one with a Coverage.sample call.
                            if (callLines.contains(lineNr)) {
                                // Check if we have actually hit this call point.
                                var key = keyPrefix + lineNr;
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
