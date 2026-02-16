package com.movie.backend.mapper;

import com.movie.backend.entity.Region;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * Region Mapper Interface
 */
@Mapper
public interface RegionMapper {
    /**
     * Get all regions
     */
    List<Region> selectAll();

    /**
     * Get region by ID
     */
    Region selectById(Integer id);

    /**
     * Get region by name
     */
    Region selectByName(String name);

    /**
     * Insert region
     */
    int insert(Region region);

    /**
     * Update region
     */
    int update(Region region);

    /**
     * Delete region by ID
     */
    int deleteById(Integer id);

    /**
     * Get regions by movie ID
     */
    List<Region> selectByMovieId(Long movieId);
}
