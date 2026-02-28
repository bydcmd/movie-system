package com.movie.backend.dto;

import lombok.Data;

/**
 * stats_user_recs 表查询结果
 */
@Data
public class UserRecDTO {
    private Long movieId;
    private Double score;
    private String algorithmType;
}
