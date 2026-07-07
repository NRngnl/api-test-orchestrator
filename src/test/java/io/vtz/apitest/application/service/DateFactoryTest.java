package io.vtz.apitest.application.service;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateFactoryTest {
    private final DateFactory dateFactory = new DateFactory();

    @Test
    void createsSqlDateAndTimestampFromPartsOrStrings() {
        assertEquals(Date.valueOf("2026-07-07"), dateFactory.sqlDate(2026, 7, 7));
        assertEquals(Date.valueOf("2026-07-07"), dateFactory.sqlDateFromString("2026-07-07"));
        assertEquals(
                Timestamp.valueOf("2026-07-07 10:11:12"),
                dateFactory.sqlTimestamp(2026, 7, 7, 10, 11, 12));
        assertEquals(
                Timestamp.valueOf("2026-07-07 10:11:12"),
                dateFactory.sqlTimestampFromString("2026-07-07 10:11:12"));
    }

    @Test
    void adjustsTimestampsAndFormatsDates() {
        Timestamp base = Timestamp.valueOf("2026-07-07 10:00:00");

        assertEquals(
                Timestamp.valueOf("2027-09-10 14:05:06"),
                dateFactory.sqlTimestampAdd(base, 1, 2, 3, 4, 5, 6));
        assertEquals("2026/07/07", dateFactory.format(base, "yyyy/MM/dd"));
        assertEquals("2026-07-07T10:00:00.000Z", dateFactory.format(base, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
        assertEquals(
                "2026-07-07 10:00:00",
                dateFactory.format(LocalDateTime.of(2026, 7, 7, 10, 0), "yyyy-MM-dd HH:mm:ss"));
        assertEquals("2026/07/07", dateFactory.format("2026-07-07", "yyyy/MM/dd"));
        assertEquals(
                "2026-07-07T10:00:00.000Z",
                dateFactory.format("2026-07-07T10:00:00.000Z", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));
    }
}
