package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.application.port.CommandRunnerPort;
import io.vtz.apitest.application.service.DotenvParser;
import io.vtz.apitest.domain.process.CommandResult;
import io.vtz.apitest.domain.process.ExecCommandSpec;
import io.vtz.apitest.interfaces.cli.ApiLogFormatter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaScript-facing exec helper. Accepts an options map {@code {args, envFile,
 * env, logPrefix, timeoutSeconds}}: {@code args} is the command line, the optional
 * {@code envFile} is a dotenv file whose entries become defaults (parent env
 * wins), the optional {@code env} map is applied as overrides (always win), and
 * the optional {@code logPrefix} (default {@code "[batch] "}) labels the
 * streamed output. {@code timeoutSeconds} defaults to ten minutes. Each output
 * line is rendered through {@link ApiLogFormatter} just like the API process logs. Replaces the previous
 * {@code godotenv -f <file> <cmd>} wrapper.
 */
public class ProcessFacade {
    private static final String DEFAULT_LOG_PREFIX = "[batch] ";

    private final CommandRunnerPort commandRunner;
    private final DotenvParser dotenvParser;
    private final ApiLogFormatter logFormatter;

    public ProcessFacade(CommandRunnerPort commandRunner, DotenvParser dotenvParser, ApiLogFormatter logFormatter) {
        this.commandRunner = commandRunner;
        this.dotenvParser = dotenvParser;
        this.logFormatter = logFormatter;
    }

    public CommandResult execCmd(Map<String, Object> options) {
        if (options == null) {
            throw new IllegalArgumentException("execCmd requires options");
        }
        List<String> command = toStringList(options.get("args"));
        Map<String, String> envDefaults = new LinkedHashMap<>();
        String envFile = string(options.get("envFile"));
        if (envFile != null && !envFile.isBlank()) {
            envDefaults.putAll(dotenvParser.parse(Path.of(envFile)));
        }
        Map<String, String> envOverrides = toStringMap(options.get("env"));
        String configuredLogPrefix = string(options.get("logPrefix"));
        String logPrefix = configuredLogPrefix == null ? DEFAULT_LOG_PREFIX : configuredLogPrefix;
        Duration timeout = timeout(options.get("timeoutSeconds"));
        ExecCommandSpec spec = new ExecCommandSpec(command, null, envDefaults, envOverrides, timeout);
        return commandRunner.run(spec, event -> System.out.println(logFormatter.line(logPrefix, event)));
    }

    private static List<String> toStringList(Object value) {
        List<String> command;
        if (value instanceof List<?> list) {
            command = stringifyItems(list);
        } else if (value instanceof Object[] array) {
            command = stringifyItems(Arrays.asList(array));
        } else if (value instanceof Iterable<?> iterable) {
            command = stringifyItems(iterable);
        } else {
            throw new IllegalArgumentException("execCmd requires 'args' as a non-empty list");
        }
        if (command.isEmpty() || command.getFirst().isBlank()) {
            throw new IllegalArgumentException("execCmd requires 'args' as a non-empty command list");
        }
        return command;
    }

    private static List<String> stringifyItems(Iterable<?> values) {
        List<String> items = new ArrayList<>();
        for (Object item : values) {
            if (item == null) {
                throw new IllegalArgumentException("execCmd 'args' entries must not be null");
            }
            items.add(String.valueOf(item));
        }
        return items;
    }

    private static Map<String, String> toStringMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("execCmd 'env' must be a map when provided");
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, entryValue) -> {
            String envKey = key == null ? null : String.valueOf(key);
            if (envKey == null || envKey.isBlank()) {
                throw new IllegalArgumentException("execCmd 'env' keys must not be blank");
            }
            if (envKey.contains("=")) {
                throw new IllegalArgumentException("execCmd 'env' keys must not contain '='");
            }
            if (entryValue == null) {
                throw new IllegalArgumentException("execCmd 'env' values must not be null");
            }
            result.put(envKey, String.valueOf(entryValue));
        });
        return result;
    }

    private static Duration timeout(Object value) {
        if (value == null) {
            return ExecCommandSpec.DEFAULT_TIMEOUT;
        }
        if (value instanceof Duration duration) {
            return validateTimeoutDuration(duration);
        }
        long seconds = parseTimeoutSeconds(value);
        if (seconds <= 0) {
            throw new IllegalArgumentException("execCmd 'timeoutSeconds' must be positive");
        }
        return Duration.ofSeconds(seconds);
    }

    private static Duration validateTimeoutDuration(Duration duration) {
        if (duration.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException("execCmd 'timeoutSeconds' must be at least 1 millisecond");
        }
        return duration;
    }

    private static long parseTimeoutSeconds(Object value) {
        try {
            BigDecimal seconds = switch (value) {
                case BigDecimal decimal -> decimal;
                case BigInteger integer -> new BigDecimal(integer);
                case Number number -> new BigDecimal(number.toString());
                default -> new BigDecimal(String.valueOf(value).trim());
            };
            return seconds.toBigIntegerExact().longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("execCmd 'timeoutSeconds' must be a whole positive number", e);
        }
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
