package cn.cloudcat.common.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌提供者，负责生成、解析和验证 JWT 令牌。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
@Component
public class JwtTokenProvider {

    /** HMAC 签名密钥 */
    private final SecretKey secretKey;
    /** 用户令牌有效期（毫秒），默认 24 小时 */
    private final long accessTokenValidityMs;
    /** 管理员令牌有效期（毫秒），默认为用户令牌的 1/3 */
    private final long adminTokenValidityMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-ms:86400000}") long accessTokenValidityMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.adminTokenValidityMs = accessTokenValidityMs / 3;
    }

    /**
     * 生成 JWT 令牌，管理员角色使用较短的有效期。
     */
    public String generateToken(String userId, String role) {
        Date now = new Date();
        long validity = "USER".equals(role) ? accessTokenValidityMs : adminTokenValidityMs;
        Date expiration = new Date(now.getTime() + validity);

        return Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从令牌中提取用户 ID。
     */
    public String getUserId(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * 从令牌中提取角色信息。
     */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * 验证令牌是否有效。
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析令牌并返回声明信息。
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
