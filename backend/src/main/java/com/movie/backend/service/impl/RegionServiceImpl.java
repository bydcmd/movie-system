package com.movie.backend.service.impl;

import com.movie.backend.entity.Region;
import com.movie.backend.mapper.RegionMapper;
import com.movie.backend.service.RegionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Region Service Implementation with Redis Cache Management
 */
@Service
public class RegionServiceImpl implements RegionService {

    @Autowired
    private RegionMapper regionMapper;

    @Override
    @Cacheable(value = "movieMetadata", key = "'allRegionsFull'")
    public List<Region> getAllRegions() {
        return regionMapper.selectAll();
    }

    @Override
    public Region getRegionById(Integer id) {
        return regionMapper.selectById(id);
    }

    @Override
    @Transactional
    @CacheEvict(value = "movieMetadata", allEntries = true)
    public void addRegion(Region region) {
        // Check if region already exists
        Region existing = regionMapper.selectByName(region.getName());
        if (existing != null) {
            throw new RuntimeException("Region already exists: " + region.getName());
        }
        regionMapper.insert(region);
        // Cache eviction: Clears all movieMetadata cache including 'allRegions'
    }

    @Override
    @Transactional
    @CacheEvict(value = "movieMetadata", allEntries = true)
    public void updateRegion(Region region) {
        Region existing = regionMapper.selectById(region.getId());
        if (existing == null) {
            throw new RuntimeException("Region not found: " + region.getId());
        }
        regionMapper.update(region);
        // Cache eviction: Clears all movieMetadata cache including 'allRegions'
    }

    @Override
    @Transactional
    @CacheEvict(value = "movieMetadata", allEntries = true)
    public void deleteRegion(Integer id) {
        Region existing = regionMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Region not found: " + id);
        }
        regionMapper.deleteById(id);
        // Cache eviction: Clears all movieMetadata cache including 'allRegions'
    }
}
