package com.movie.backend.messaging.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.movie.backend.entity.ViewHistory;
import com.movie.backend.mapper.ViewHistoryMapper;
import com.movie.backend.messaging.event.EventEnvelope;
import com.movie.backend.messaging.event.ViewHistoryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViewHistoryEventConsumer {

    private final ObjectMapper objectMapper;
    private final ViewHistoryMapper viewHistoryMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.view-history}",
            groupId = "${app.kafka.consumer.view-history-group:movie-backend-view-history}"
    )
    public void onMessage(String message) {
        EventEnvelope<ViewHistoryEvent> envelope;
        try {
            envelope = objectMapper.readValue(
                    message, new TypeReference<EventEnvelope<ViewHistoryEvent>>() {}
            );
        } catch (Exception e) {
            log.warn("Ignore invalid view history message: {}", message, e);
            return;
        }

        ViewHistoryEvent event = envelope.getData();
        if (event == null || event.getUserId() == null || event.getMovieId() == null) {
            log.warn("Ignore invalid view history event: {}", message);
            return;
        }

        try {
            int updated = viewHistoryMapper.updateViewTime(event.getUserId(), event.getMovieId());
            if (updated > 0) {
                return;
            }

            ViewHistory viewHistory = new ViewHistory();
            viewHistory.setUserId(event.getUserId());
            viewHistory.setMovieId(event.getMovieId());
            viewHistory.setViewTime(new Date(event.getViewTime()));
            try {
                viewHistoryMapper.insert(viewHistory);
            } catch (DataIntegrityViolationException ex) {
                viewHistoryMapper.updateViewTime(event.getUserId(), event.getMovieId());
            }
        } catch (RuntimeException e) {
            log.error("Failed to consume view history event: {}", message, e);
            throw e;
        }
    }
}
