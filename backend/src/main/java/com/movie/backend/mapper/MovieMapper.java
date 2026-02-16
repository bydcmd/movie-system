package com.movie.backend.mapper;

import com.movie.backend.dto.CatalogQueryDTO;
import com.movie.backend.dto.MovieSearchDTO;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.entity.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MovieMapper {
    /**
     * Select by ID
     */
    Movie selectById(@Param("id") Long id);

    /**
     * Search with filters
     */
    List<Movie> search(MovieSearchDTO searchDTO);

    /**
     * Select by Catalog
     */
    List<Movie> selectByCatalog(CatalogQueryDTO queryDTO);

    /**
     * Get Hot Movies (Sorted by view count)
     */
    List<Movie> selectHot(@Param("limit") int limit);

    /**
     * 获取趋势榜单
     * @param period 周期类型: DAILY, WEEKLY, MONTHLY
     * @param limit 返回数量
     */
    List<TrendingMovieDTO> selectTrendingWithScore(@Param("period") String period,
                                                   @Param("limit") int limit);

    /**
     * Insert Movie
     */
    int insert(Movie movie);

    /**
     * Update Movie
     */
    int update(Movie movie);

    /**
     * Delete Movie
     */
    int deleteById(@Param("id") Long id);

    /**
     * Select List with keyword
     */
    List<Movie> selectList(@Param("keyword") String keyword);

    /**
     * Get Recommended Movies (Sorted by score)
     */
    List<Movie> selectRecommended(@Param("limit") int limit);

    /**
     * Get Movies by Genre
     */
    List<Movie> selectByGenre(@Param("genre") String genre);

    /**
     * Get Movies by Year
     */
    List<Movie> selectByYear(@Param("year") Integer year);

    /**
     * Get Latest Movies (Sorted by year desc)
     */
    List<Movie> selectLatest();

    /**
     * Count all movies (physical delete only).
     */
    int countAll();
    
    /**
     * 根据影人名称查询其参与的电影
     */
    List<Movie> selectByPersonName(@Param("personName") String personName);

    /**
     * 获取所有不重复的类型
     */
    List<String> selectAllGenres();

    /**
     * 获取所有不重复的地区
     */
    List<String> selectAllRegions();

    /**
     * 获取所有不重复的年份
     */
    List<Integer> selectAllYears();

    /**
     * 更新电影的豆瓣评分和评分人数
     */
    int updateMovieScore(@Param("movieId") Long movieId, @Param("score") Double score, @Param("votes") Integer votes);

    /**
     * 查询冷门佳作
     * @param minScore 最低评分 (例如 8.0)
     * @param maxVotes 最大投票数 (例如 50000，定义什么是"冷门")
     * @param limit 返回数量
     */
    List<Movie> selectColdGems(@Param("minScore") Double minScore,
                               @Param("maxVotes") Integer maxVotes,
                               @Param("limit") int limit);
}
