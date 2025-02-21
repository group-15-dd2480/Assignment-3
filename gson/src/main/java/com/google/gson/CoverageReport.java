package com.google.gson;

import java.io.*;
import java.util.*;

public class CoverageReport {
    public static void generateReport(String outputFile, Map<String, List<Integer>> coveragePoints, Set<String> executedLines) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write("```diff\n");

            for (Map.Entry<String, List<Integer>> entry : coveragePoints.entrySet()) {
                String method = entry.getKey();
                for (int line : entry.getValue()) {
                    String key = method + ":" + line;
                    String prefix = executedLines.contains(key) ? "+ " : "- ";
                    writer.write(prefix + key + "\n");
                }
            }

            writer.write("```\n");
        } catch (IOException e) {
            throw new RuntimeException("Error writing coverage report", e);
        }
    }
}
