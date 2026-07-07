package io.vtz.apitest.application.service;

import java.util.UUID;

public class IdentifierFactory {
    private static final int DEFAULT_LENGTH = 16;
    private static final int UUID_WITHOUT_DASHES_LENGTH = 32;

    public String randomString() {
        return randomString(DEFAULT_LENGTH);
    }

    public String randomString(int length) {
        if (length < 1 || length > UUID_WITHOUT_DASHES_LENGTH) {
            throw new IllegalArgumentException("length must be between 1 and 32");
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, length);
    }
}
