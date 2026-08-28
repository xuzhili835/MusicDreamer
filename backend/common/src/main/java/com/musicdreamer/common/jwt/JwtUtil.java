package com.musicdreamer.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/** JWT 工具（HS256）。载荷：userId / username / role / exp。 */
public final class JwtUtil {

    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_ROLE = "role";

    private static final String DEFAULT_SECRET =
            "musicdreamer-jwt-secret-2026-please-change-in-prod";
    private static final Key KEY =
            Keys.hmacShaKeyFor(DEFAULT_SECRET.getBytes(StandardCharsets.UTF_8));

    public static final long TTL_DEFAULT_MS = 24 * 3600 * 1000L;      // 24 小时
    public static final long TTL_REMEMBER_MS = 7 * 24 * 3600 * 1000L; // 记住登录 7 天

    private JwtUtil() {}

    public static String generate(long userId, String username, int role, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttlMillis))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /** 解析并校验签名与过期；无效或过期返回 null（调用方转 2006）。 */
    public static Claims parse(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(KEY).build()
                    .parseClaimsJws(token).getBody();
        } catch (Exception e) {
            return null;
        }
    }
}
