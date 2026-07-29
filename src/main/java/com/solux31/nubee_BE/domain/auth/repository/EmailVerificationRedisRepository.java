package com.solux31.nubee_BE.domain.auth.repository;

import com.solux31.nubee_BE.domain.auth.enums.EmailVerificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private static final long EMAIL_VERIFICATION_TTL = 300L; // 5분
    private static final String PREFIX = "EMAIL_VERIFICATION:";

    private String generateKey(EmailVerificationType type, String email) {
        return PREFIX + type.name() + ":" + email;
    }

    // 인증 코드 저장
    public void save(EmailVerificationType type, String email, String code) {
        String key = generateKey(type, email);
        // code:isVerified:failCount:sendCount
        String value = code + ":false:0:1";
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(EMAIL_VERIFICATION_TTL));
    }

    // 인증 코드 조회
    public String findCode(EmailVerificationType type, String email) {
        String value = redisTemplate.opsForValue().get(generateKey(type, email));
        if (value == null) return null;
        return value.split(":")[0];
    }

    // 인증 완료 여부 조회
    public boolean isVerified(EmailVerificationType type, String email) {
        String value = redisTemplate.opsForValue().get(generateKey(type, email));
        if (value == null) return false;
        return Boolean.parseBoolean(value.split(":")[1]);
    }

    // 실패 횟수 조회
    public int getFailCount(EmailVerificationType type, String email) {
        String value = redisTemplate.opsForValue().get(generateKey(type, email));
        if (value == null) return 0;
        return Integer.parseInt(value.split(":")[2]);
    }

    // 발송 횟수 조회
    public int getSendCount(EmailVerificationType type, String email) {
        String value = redisTemplate.opsForValue().get(generateKey(type, email));
        if (value == null) return 0;
        return Integer.parseInt(value.split(":")[3]);
    }

    // 실패 횟수 증가
    public void increaseFailCount(EmailVerificationType type, String email) {
        String key = generateKey(type, email);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return;
        String[] parts = value.split(":");
        parts[2] = String.valueOf(Integer.parseInt(parts[2]) + 1);
        Long ttl = redisTemplate.getExpire(key);
        redisTemplate.opsForValue().set(key, String.join(":", parts),
                Duration.ofSeconds(ttl != null && ttl > 0 ? ttl : EMAIL_VERIFICATION_TTL));
    }

    // 인증 완료 처리
    public void verify(EmailVerificationType type, String email) {
        String key = generateKey(type, email);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) return;
        String[] parts = value.split(":");
        parts[1] = "true";
        Long ttl = redisTemplate.getExpire(key);
        redisTemplate.opsForValue().set(key, String.join(":", parts),
                Duration.ofSeconds(ttl != null && ttl > 0 ? ttl : EMAIL_VERIFICATION_TTL));
    }

    // 삭제
    public void delete(EmailVerificationType type, String email) {
        redisTemplate.delete(generateKey(type, email));
    }

    // 존재 여부
    public boolean exists(EmailVerificationType type, String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(generateKey(type, email)));
    }

    // 발송 횟수 증가
    public void increaseSendCount(EmailVerificationType type, String email, String code) {
        String key = generateKey(type, email);
        String value = redisTemplate.opsForValue().get(key);
        int sendCount = value != null ? Integer.parseInt(value.split(":")[3]) + 1 : 1;
        String newValue = code + ":false:0:" + sendCount;
        redisTemplate.opsForValue().set(key, newValue, Duration.ofSeconds(EMAIL_VERIFICATION_TTL));
    }
}