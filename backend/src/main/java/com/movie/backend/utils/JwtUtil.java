package com.movie.backend.utils;

import com.movie.backend.common.UserStatus;
import com.movie.backend.entity.User;
import com.movie.backend.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    // 从配置文件中读取 JWT 密钥
    @Value("${jwt.secret}")
    private String secretString;

    // 从配置文件中读取过期时间
    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // 密钥对象（在 PostConstruct 中初始化）
    private SecretKey key;

    // 静态实例，用于静态方法访问
    private static JwtUtil instance;

    @Autowired
    private UserMapper userMapper;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
        instance = this;
    }

    /**
     * 生成 Access Token (短效)
     */
    public static String generateAccessToken(String userId, String nickname, Integer role, Integer passwordVersion) {
        return instance.generateToken(userId, nickname, role, passwordVersion, instance.accessTokenExpiration, "access");
    }

    /**
     * 生成 Refresh Token (长效)
     */
    public static String generateRefreshToken(String userId, String nickname, Integer role, Integer passwordVersion) {
        return instance.generateToken(userId, nickname, role, passwordVersion, instance.refreshTokenExpiration, "refresh");
    }

    /**
     * 通用生成 Token 方法
     */
    private String generateToken(String userId, String nickname, Integer role, Integer passwordVersion, long expiration, String type) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userId);
        claims.put("nickname", nickname);
        claims.put("role", role);
        claims.put("passwordVersion", passwordVersion != null ? passwordVersion : 1);
        claims.put("type", type); // 区分 Token 类型

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 解析 Token 获取 Claims (私有方法，供内部调用)
     * 这里不再吞掉异常，而是抛出，让拦截器或全局异常处理器去处理
     */
    public static Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(instance.key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 验证 Token 是否有效
     * 只有在这里我们捕获异常，返回 boolean
     */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            // 这里可以打印日志，比如: "Token验证失败: " + e.getMessage()
            return false;
        }
    }

    public static String getUserId(String token) {
        // 如果 parseToken 抛出异常，这里也会抛出，交给上层处理
        return parseToken(token).getSubject();
    }

    // 获取其他字段...
    public static Integer getRole(String token) {
        return parseToken(token).get("role", Integer.class);
    }

    /**
     * 使用 Refresh Token 刷新 Access Token
     * 必须查库校验用户当前状态
     */
    public static String refreshAccessToken(String refreshToken) {
        Claims claims = parseToken(refreshToken);
        String type = claims.get("type", String.class);
        
        if (!"refresh".equals(type)) {
            throw new RuntimeException("无效的 Refresh Token 类型");
        }

        String userId = claims.getSubject();
        Integer tokenPasswordVersion = claims.get("passwordVersion", Integer.class);
        
        // 必须查库验证用户状态
        User user = instance.userMapper.selectById(userId);
        
        // 1. 检查用户是否存在
        if (user == null) {
            throw new RuntimeException("用户不存在，请重新登录");
        }
        
        // 2. 检查用户是否被冻结或注销
        if (UserStatus.isFrozen(user.getStatus())) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }
        if (UserStatus.isCancelled(user.getStatus())) {
            throw new RuntimeException("该账号已注销，无法登录");
        }
        
        // 3. 检查密码版本是否一致（修改密码后旧 Token 失效）
        Integer currentPasswordVersion = user.getPasswordVersion() != null ? user.getPasswordVersion() : 1;
        if (tokenPasswordVersion == null || !tokenPasswordVersion.equals(currentPasswordVersion)) {
            throw new RuntimeException("密码已修改，Token 已失效，请重新登录");
        }
        
        // 所有校验通过，生成新的 Access Token
        return generateAccessToken(user.getId(), user.getNickname(), user.getRole(), user.getPasswordVersion());
    }

    /**
     * 从请求头中提取 UserId
     */
    public static String getUserIdFromRequest(jakarta.servlet.http.HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return getUserId(token);
        }
        throw new RuntimeException("Authorization header missing or invalid");
    }

    /**
     * 从请求头中提取 Token
     */
    public static String extractTokenFromRequest(jakarta.servlet.http.HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
