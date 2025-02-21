package com.google.gson;

import java.io.*;
import java.util.*;

public class Coverage {
    private static final Set<String> executedLines = new HashSet<>();

    public static void sample() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        // The stack trace contains:
        // [0] java.lang.Thread.getStackTrace
        // [1] com.google.gson.Coverage.sample (this method)
        // [2] The method that called sample() -> we need this!
        if (stackTrace.length > 2) {
            StackTraceElement caller = stackTrace[2]; // The calling method
            executedLines.add(caller.getClassName() + "#" + caller.getMethodName() + ":" + caller.getLineNumber());
        }
    }

    public static void saveCoverage(String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (String line : executedLines) {
                writer.write(line + "\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("Error writing coverage data", e);
        }
    }

    public static Set<String> getExecutedLines() {
        return executedLines;
    }
}


