package com.movie.backend.mapper;

import com.movie.backend.dto.AdminDashboardOverviewVO;
import com.movie.backend.dto.AdminTrendPointVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AdminDashboardMapper {

    AdminDashboardOverviewVO selectOverview();

    List<AdminTrendPointVO> selectUserRegistrationTrend();

    List<AdminTrendPointVO> selectPublishedCommentTrend();

    List<AdminTrendPointVO> selectFavoriteTrend();

    List<AdminTrendPointVO> selectRatingTrend();

    List<AdminTrendPointVO> selectViewTrend();

    List<AdminTrendPointVO> selectWatchedTrend();
}
