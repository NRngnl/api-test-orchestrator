package io.vtz.apitest.interfaces.facade;

import io.vtz.apitest.application.port.CommandRunnerPort;
import io.vtz.apitest.application.service.DotenvParser;
import io.vtz.apitest.domain.process.CommandResult;
import io.vtz.apitest.domain.process.ExecCommandSpec;
import io.vtz.apitest.interfaces.cli.ApiLogFormatter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaScript-facing exec helper. Accepts an options map {@code {args, envFile,
 * env, logPrefix}}: {@code args} is the command line, the optional
 * {@code envFile} is a dotenv file whose entries become defaults (parent env
 * wins), the optional {@code env} map is applied as overrides (always win), and
 * the optional {@code logPrefix} (default {@code "[batch] "}) labels the
 * streamed output. Each output line is rendered through {@link ApiLogFormatter}
 * just like the API process logs. Replaces the previous
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
        List<String> command = toStringList(options.get("args"));
        Map<String, String> envDefaults = new LinkedHashMap<>();
        String envFile = string(options.get("envFile"));
        if (envFile != null && !envFile.isBlank()) {
            envDefaults.putAll(dotenvParser.parse(Path.of(envFile)));
        }
        Map<String, String> envOverrides = toStringMap(options.get("env"));
        String logPrefix = string(options.getOrDefault("logPrefix", DEFAULT_LOG_PREFIX));
        ExecCommandSpec spec = new ExecCommandSpec(command, null, envDefaults, envOverrides);
        return commandRunner.run(spec, event -> System.out.println(logFormatter.line(logPrefix, event)));
    }

    private static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof Object[] array) {
            return Arrays.stream(array).map(String::valueOf).toList();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> items = new ArrayList<>();
            iterable.forEach(item -> items.add(String.valueOf(item)));
            return items;
        }
        throw new IllegalArgumentException("execCmd requires 'args' as a list");
    }

    private static Map<String, String> toStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, entryValue) -> result.put(String.valueOf(key), String.valueOf(entryValue)));
        return result;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
