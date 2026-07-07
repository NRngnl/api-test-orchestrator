package io.vtz.apitest.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class ChecksumService {
    private final HexFormat hexFormat = HexFormat.of();

    public String md5(String value) {
        return digest("MD5", value);
    }

    public String sha256(String value) {
        return digest("SHA-256", value);
    }

    private String digest(String algorithm, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] bytes = digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return hexFormat.formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Digest algorithm unavailable: " + algorithm, e);
        }
    }
}
