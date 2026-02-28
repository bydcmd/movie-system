package com.movie.backend.mapper;

import com.movie.backend.dto.MovieItemVO;
import com.movie.backend.entity.Watched;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WatchedMapper {
    int insert(Watched watched);

    int deleteByUserAndMovie(@Param("userId") String userId, @Param("movieId") Long movieId);

    int deleteBatchByUserAndMovies(@Param("userId") String userId, @Param("movieIds") List<Long> movieIds);

    int deleteAllByUserId(@Param("userId") String userId);

    Watched selectByUserAndMovie(@Param("userId") String userId, @Param("movieId") Long movieId);

    List<Watched> selectBatchByUserAndMovies(@Param("userId") String userId, @Param("movieIds") List<Long> movieIds);

    List<MovieItemVO> selectMyWatchedByUserId(@Param("userId") String userId);

    int countByUserId(@Param("userId") String userId);
}
