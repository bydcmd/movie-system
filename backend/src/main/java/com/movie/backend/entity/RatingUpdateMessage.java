package com.movie.backend.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 评分更新消息实体
 * 用于 Redis Stream 消息队列
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingUpdateMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 电影ID
     */
    private Long movieId;
    
    /**
     * 操作类型：CREATE, UPDATE, DELETE
     */
    private String operationType;
    
    /**
     * 消息时间戳
     */
    private Long timestamp;
    
    /**
     * 用户ID（用于日志追踪）
     */
    private String userId;
}
