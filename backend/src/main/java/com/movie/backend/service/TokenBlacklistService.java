package com.movie.backend.service;

/**
 * Token 黑名单服务
 * 用于管理被撤销的 Token（注销、修改密码等场景）
 */
public interface TokenBlacklistService {
    
    /**
     * 将 Token 加入黑名单
     * @param token JWT Token
     * @param expirationSeconds Token 剩余有效时间（秒）
     */
    void addToBlacklist(String token, long expirationSeconds);
    
    /**
     * 检查 Token 是否在黑名单中
     * @param token JWT Token
     * @return true: 在黑名单中（已失效），false: 不在黑名单中（有效）
     */
    boolean isBlacklisted(String token);
    
    /**
     * 将 Access Token 和 Refresh Token 都加入黑名单
     * @param accessToken Access Token
     * @param refreshToken Refresh Token
     */
    void blacklistBothTokens(String accessToken, String refreshToken);
}
