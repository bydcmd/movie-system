package com.movie.backend.mapper;

import com.movie.backend.dto.CatalogQueryDTO;
import com.movie.backend.dto.MovieSearchDTO;
import com.movie.backend.dto.TrendingMovieDTO;
import com.movie.backend.entity.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

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
     * @param period 周期类型: DAILY, WEEKLY, MONTHLY, TOTAL
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
     * 根据影人ID查询其参与的电影（演员或导演）
     */
    List<Movie> selectByPersonId(@Param("personId") String personId);

    /**
     * 根据ID列表批量查询电影（保持传入顺序）
     */
    List<Movie> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 获取所有不重复的类型
     */
    List<String> selectAllGenres();

    /**
     * 获取所有不重复的地区
     */
    List<String> selectAllRegions();

    /**
     * 获取所有不重复的语言
     */
    List<String> selectAllLanguages();

    /**
     * 获取所有不重复的年份
     */
    List<Integer> selectAllYears();

    /**
     * 更新电影的本站评分和评分人数
     */
    int updateMovieScore(@Param("movieId") Long movieId,
                         @Param("siteScore") Double siteScore,
                         @Param("siteVotes") Integer siteVotes);

}
