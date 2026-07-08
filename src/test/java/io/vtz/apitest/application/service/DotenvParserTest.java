package io.vtz.apitest.application.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DotenvParserTest {
    @Test
    void parsesEntriesSkippingCommentsAndStrippingMatchingQuotes() throws Exception {
        Path envFile = Files.createTempFile("dotenv", ".env");
        Files.writeString(envFile, """
                # comment line
                ENV=mock

                RECEIPT_MYSQL_PROTOCOL=tcp(mysql:3306)
                KARTE_TS_HOST="http://localhost:3000"
                SINGLE='single-quoted'
                WITH_EQUALS=a=b=c
                  SPACED_KEY = spaced value
                export EXPORTED=exported-value
                NO_SEPARATOR_LINE
                """);

        Map<String, String> values = new DotenvParser().parse(envFile);

        assertEquals("mock", values.get("ENV"));
        assertEquals("tcp(mysql:3306)", values.get("RECEIPT_MYSQL_PROTOCOL"));
        assertEquals("http://localhost:3000", values.get("KARTE_TS_HOST"));
        assertEquals("single-quoted", values.get("SINGLE"));
        assertEquals("a=b=c", values.get("WITH_EQUALS"));
        assertEquals("spaced value", values.get("SPACED_KEY"));
        assertEquals("exported-value", values.get("EXPORTED"));
        assertFalse(values.containsKey("NO_SEPARATOR_LINE"));
    }
}
