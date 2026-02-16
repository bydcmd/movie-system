package com.movie.backend.service.impl;

import com.movie.backend.service.TokenBlacklistService;
import com.movie.backend.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务实现
 * 使用 Redis 存储被撤销的 Token
 */
@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addToBlacklist(String token, long expirationSeconds) {
        if (token == null || token.isEmpty()) {
            return;
        }
        
        // 只需要存储到过期时间即可，过期后自动从 Redis 删除
        String key = BLACKLIST_PREFIX + token;
        stringRedisTemplate.opsForValue().set(key, "blacklisted", expirationSeconds, TimeUnit.SECONDS);
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        String key = BLACKLIST_PREFIX + token;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    @Override
    public void blacklistBothTokens(String accessToken, String refreshToken) {
        // 将 Access Token 加入黑名单
        if (accessToken != null && !accessToken.isEmpty()) {
            try {
                Claims claims = JwtUtil.parseToken(accessToken);
                long exp = claims.getExpiration().getTime();
                long now = System.currentTimeMillis();
                long ttl = (exp - now) / 1000; // 转换为秒
                
                if (ttl > 0) {
                    addToBlacklist(accessToken, ttl);
                }
            } catch (Exception e) {
                // Token 已过期或无效，无需加入黑名单
            }
        }
        
        // 将 Refresh Token 加入黑名单
        if (refreshToken != null && !refreshToken.isEmpty()) {
            try {
                Claims claims = JwtUtil.parseToken(refreshToken);
                long exp = claims.getExpiration().getTime();
                long now = System.currentTimeMillis();
                long ttl = (exp - now) / 1000; // 转换为秒
                
                if (ttl > 0) {
                    addToBlacklist(refreshToken, ttl);
                }
            } catch (Exception e) {
                // Token 已过期或无效，无需加入黑名单
            }
        }
    }
}
