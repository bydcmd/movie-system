package com.movie.backend.mapper;

import com.movie.backend.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface OutboxEventMapper {
    int insert(OutboxEvent event);

    int markProcessing(@Param("id") Long id, @Param("lockUntil") Date lockUntil);

    int markSent(@Param("id") Long id);

    int markFailed(@Param("id") Long id,
                   @Param("status") int status,
                   @Param("retryCount") int retryCount,
                   @Param("nextRetryTime") Date nextRetryTime,
                   @Param("lastError") String lastError);

    List<OutboxEvent> selectPending(@Param("now") Date now, @Param("limit") int limit);

    int deleteSentBefore(@Param("cutoff") Date cutoff, @Param("limit") int limit);
}
