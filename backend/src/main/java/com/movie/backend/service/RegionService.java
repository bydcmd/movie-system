package com.movie.backend.service;

import com.movie.backend.entity.Region;

import java.util.List;

/**
 * Region Service Interface
 */
public interface RegionService {
    /**
     * Get all regions
     */
    List<Region> getAllRegions();

    /**
     * Get region by ID
     */
    Region getRegionById(Integer id);

    /**
     * Add new region
     */
    void addRegion(Region region);

    /**
     * Update region
     */
    void updateRegion(Region region);

    /**
     * Delete region by ID
     */
    void deleteRegion(Integer id);
}
