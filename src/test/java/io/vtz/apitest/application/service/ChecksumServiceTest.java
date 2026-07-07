package io.vtz.apitest.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChecksumServiceTest {
    private final ChecksumService checksumService = new ChecksumService();

    @Test
    void md5MatchesAwsSqsMessageChecksum() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", checksumService.md5("hello"));
        assertEquals("c59b879de192eae4320a260a504ce0be", checksumService.md5("{\"batchID\":2}"));
    }

    @Test
    void sha256UsesUtf8Bytes() {
        assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                checksumService.sha256("hello"));
    }
}
