package com.solux31.nubee_BE.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final long REFRESH_TOKEN_TTL = 60 * 60 * 24 * 30L; // 30일
    private static final String PREFIX = "REFRESH_TOKEN:";
    private static final String HASH_PREFIX = "REFRESH_TOKEN_HASH:";

    // userId로 key 생성
    private String generateKey(Long userId) {
        return PREFIX + userId;
    }

    // tokenHash로 key 생성
    private String generateHashKey(String tokenHash) {
        return HASH_PREFIX + tokenHash;
    }

    // RefreshToken 저장
    public void save(Long userId, String tokenHash) {
        // userId → tokenHash
        redisTemplate.opsForValue().set(
                generateKey(userId), tokenHash, Duration.ofSeconds(REFRESH_TOKEN_TTL));
        // tokenHash → userId
        redisTemplate.opsForValue().set(
                generateHashKey(tokenHash), String.valueOf(userId), Duration.ofSeconds(REFRESH_TOKEN_TTL));
    }

    // tokenHash로 userId 조회
    public Optional<String> findUserIdByTokenHash(String tokenHash) {
        String userId = redisTemplate.opsForValue().get(generateHashKey(tokenHash));
        return Optional.ofNullable(userId);
    }

    // userId로 RefreshToken 삭제
    public void deleteByUserId(Long userId) {
        String tokenHash = redisTemplate.opsForValue().get(generateKey(userId));
        if (tokenHash != null) {
            redisTemplate.delete(generateHashKey(tokenHash));
        }
        redisTemplate.delete(generateKey(userId));
    }
}