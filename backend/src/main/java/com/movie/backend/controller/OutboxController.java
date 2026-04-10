package com.movie.backend.controller;

import com.movie.backend.mapper.OutboxEventMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Outbox", description = "Outbox internal endpoints for Spark job")
@RestController
@RequestMapping("/internal/outbox")
public class OutboxController {

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @Operation(summary = "Mark outbox events as sent", description = "Called by Spark job after events are written to Hive ODS")
    @PostMapping("/mark-sent")
    public Map<String, Object> markSent(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of("marked", 0);
        }
        int marked = outboxEventMapper.markSentBatch(ids);
        return Map.of("marked", marked);
    }
}
