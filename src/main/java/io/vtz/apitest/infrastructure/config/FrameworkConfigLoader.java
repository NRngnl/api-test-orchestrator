package io.vtz.apitest.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.moandjiezana.toml.Toml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FrameworkConfigLoader {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, String> variables;

    public FrameworkConfigLoader() {
        this(defaultVariables());
    }

    public FrameworkConfigLoader(Map<String, String> variables) {
        this.variables = Map.copyOf(variables == null ? Map.of() : variables);
    }

    public FrameworkConfig load(Path path) {
        if (path == null) {
            return new FrameworkConfig();
        }
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Config file does not exist: " + path);
        }
        try {
            String fileName = path.getFileName().toString().toLowerCase();
            String content = substituteVariables(Files.readString(path));
            if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
                return yamlMapper.readValue(content, FrameworkConfig.class);
            }
            if (fileName.endsWith(".json")) {
                return jsonMapper.readValue(content, FrameworkConfig.class);
            }
            if (fileName.endsWith(".toml")) {
                return new Toml().read(content).to(FrameworkConfig.class);
            }
            throw new IllegalArgumentException("Unsupported config file format: " + path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load config: " + path, e);
        }
    }

    private String substituteVariables(String content) {
        Matcher matcher = VARIABLE.matcher(content);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Missing config variable: " + key);
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static Map<String, String> defaultVariables() {
        Map<String, String> values = new LinkedHashMap<>(System.getenv());
        System.getProperties().forEach((key, value) -> values.put(String.valueOf(key), String.valueOf(value)));
        return values;
    }
}
