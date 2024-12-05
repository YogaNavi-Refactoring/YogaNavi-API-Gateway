package com.yoganavi.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    public enum TokenStatus {
        VALID,
        INVALID,
        NOT_FOUND,
        EXPIRED
    }

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            throw new IllegalStateException("JWT secret이 설정되지 않음");
        }
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT 액세스 토큰 만료 시간: {}ms, 리프레시 토큰 만료 시간: {}ms",
            this.accessTokenExpiration, this.refreshTokenExpiration);
    }

    public TokenStatus validateTokenStatus(String token) {
        if (token == null) {
            return TokenStatus.NOT_FOUND;
        }

        try {
            Claims claims = validateToken(token);
            return isTokenExpired(claims) ? TokenStatus.EXPIRED : TokenStatus.VALID;
        } catch (ExpiredJwtException e) {
            log.warn("gateway - 토큰 검증 실패 - 토큰 만료: {}", e.getMessage());
            return TokenStatus.EXPIRED;
        } catch (JwtException e) {
            log.error("gateway - 토큰 검증 실패 - JWT error: {}", e.getMessage());
            return TokenStatus.INVALID;
        } catch (Exception e) {
            log.error("gateway - 토큰 검증 실패 - exception", e);
            return TokenStatus.INVALID;
        }
    }

    // 토큰 검증
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException e) {
            log.error("토큰 검증 실패: {}", e.getMessage());
            throw e;
        }
    }

    public String extractToken(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken;
    }

    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    public Integer getUserIdFromToken(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.get("id", Integer.class);
        } catch (Exception e) {
            log.error("토큰에서 userid 추출 실패", e);
            throw e;
        }
    }

    public String getRoleFromToken(String token) {
        try {
            Claims claims = validateToken(token);
            return claims.get("role", String.class);
        } catch (Exception e) {
            log.error("토큰에서 role 추출 실패", e);
            throw e;
        }
    }

}
