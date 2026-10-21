package com.campusmart.user.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 密码摘要工具。项目定位为演示项目，使用 SHA-256 + 固定盐，避免明文落库。
 * 生产环境应替换为 BCrypt / Argon2 等自适应哈希算法。
 */
public final class PasswordUtil {

    private static final String SALT = "campusmart@2026";

    private PasswordUtil() {
    }

    public static String encrypt(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((rawPassword + SALT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    public static boolean matches(String rawPassword, String encryptedPassword) {
        return encrypt(rawPassword).equals(encryptedPassword);
    }
}
