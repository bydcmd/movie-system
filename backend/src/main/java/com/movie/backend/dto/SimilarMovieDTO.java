package com.movie.backend.dto;

import lombok.Data;

/**
 * stats_similar_movies 表查询结果
 */
@Data
public class SimilarMovieDTO {
    private Long similarMovieId;
    private Double similarityScore;
    private Integer similarityType;
}
