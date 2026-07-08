package io.vtz.apitest.application.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a dotenv-format file (KEY=VALUE) into an ordered map, mirroring the
 * subset of godotenv behaviour the test harness relies on: blank lines and
 * {@code #} comments are skipped, an optional {@code export } prefix is
 * dropped, the split is on the first {@code =}, and a single matching pair of
 * surrounding quotes is stripped from the value while inner characters (e.g.
 * {@code tcp(mysql:3306)}) are preserved verbatim.
 */
public class DotenvParser {
    public Map<String, String> parse(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read env file: " + file, e);
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).strip();
            String value = stripSurroundingQuotes(line.substring(separator + 1).strip());
            values.put(key, value);
        }
        return values;
    }

    private static String stripSurroundingQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
