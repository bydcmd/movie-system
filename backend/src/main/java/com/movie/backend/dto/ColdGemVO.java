package com.movie.backend.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.movie.backend.config.ImagePathSerializer;
import com.movie.backend.entity.Movie;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 冷门佳作视图对象
 * 包含电影基本信息和上榜理由
 */
@Data
@Schema(description = "冷门佳作信息")
public class ColdGemVO {

    @Schema(description = "电影ID", example = "1292052")
    private Long id;

    @Schema(description = "电影名称", example = "肖申克的救赎")
    private String name;

    @Schema(description = "电影别名", example = "The Shawshank Redemption / 月黑高飞(港) / 刺激1995(台)")
    private String alias;

    @Schema(description = "演员列表 (包含name和role等信息)", example = "[{\"name\":\"蒂姆·罗宾斯\",\"id\":\"12345\"}]")
    private List<Map<String, Object>> actors;

    @Schema(description = "封面图片路径 (自动拼接域名)", example = "http://localhost:8080/images/p480747492.jpg")
    @JsonSerialize(using = ImagePathSerializer.class)
    private String cover;

    @Schema(description = "导演列表", example = "[{\"name\":\"弗兰克·德拉邦特\",\"id\":\"15615\"}]")
    private List<Map<String, Object>> directors;

    @Schema(description = "本站评分 (1-10)", example = "9.7")
    private Double score;

    @Schema(description = "豆瓣评分 (1-10)", example = "9.7")
    private Double doubanScore;

    @Schema(description = "本站评分人数", example = "2956885")
    private Integer votes;

    @Schema(description = "豆瓣评分人数", example = "2956885")
    private Integer doubanVotes;

    @Schema(description = "电影类型 (逗号分隔)", example = "犯罪,剧情")
    private String genres;

    @Schema(description = "IMDB ID", example = "tt0111161")
    private String imdbId;

    @Schema(description = "语言", example = "英语")
    private String languages;

    @Schema(description = "片长", example = "142分钟")
    private String mins;

    @Schema(description = "制片国家/地区", example = "美国")
    private String regions;

    @Schema(description = "上映日期", example = "1994-09-10(多伦多电影节)")
    private String releaseDate;

    @Schema(description = "剧情简介", example = "20世纪40年代末，小有成就的青年银行家安迪...")
    private String storyline;

    @Schema(description = "上映年份", example = "1994")
    private Integer year;

    @Schema(description = "编剧列表", example = "[{\"name\":\"斯蒂芬·金\",\"id\":\"12345\"}]")
    private List<Map<String, Object>> writers;

    @Schema(description = "上榜理由(如: 9.0分但在本站仅100人看过)", example = "9.0分但仅500人评价，绝对的冷门神作")
    private String reason;

    /**
     * 从 Movie 实体转换为 ColdGemVO
     */
    public static ColdGemVO fromMovie(Movie movie) {
        if (movie == null) {
            return null;
        }
        ColdGemVO vo = new ColdGemVO();
        vo.setId(movie.getId());
        vo.setName(movie.getName());
        vo.setAlias(movie.getAlias());
        vo.setActors(movie.getActors());
        vo.setCover(movie.getCover());
        vo.setDirectors(movie.getDirectors());
        vo.setScore(movie.getScore());
        vo.setDoubanScore(movie.getDoubanScore());
        vo.setVotes(movie.getVotes());
        vo.setDoubanVotes(movie.getDoubanVotes());
        vo.setGenres(movie.getGenres());
        vo.setImdbId(movie.getImdbId());
        vo.setLanguages(movie.getLanguages());
        vo.setMins(movie.getMins());
        vo.setRegions(movie.getRegions());
        vo.setReleaseDate(movie.getReleaseDate());
        vo.setStoryline(movie.getStoryline());
        vo.setYear(movie.getYear());
        vo.setWriters(movie.getWriters());
        return vo;
    }
}
