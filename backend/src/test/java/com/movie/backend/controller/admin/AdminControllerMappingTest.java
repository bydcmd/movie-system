package com.movie.backend.controller.admin;

import com.movie.backend.dto.AdminMovieDTO;
import com.movie.backend.entity.Movie;
import com.movie.backend.service.AdminService;
import com.movie.backend.service.AnalyticsService;
import com.movie.backend.service.GenreService;
import com.movie.backend.service.RegionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminControllerMappingTest {

    @Mock
    private AdminService adminService;

    @Mock
    private GenreService genreService;

    @Mock
    private RegionService regionService;

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private AdminController adminController;

    @Test
    void updateMovieShouldMapScoreFieldsSeparately() {
        AdminMovieDTO dto = new AdminMovieDTO();
        dto.setName("Updated Movie");
        dto.setScore(8.1);
        dto.setDoubanScore(9.3);
        dto.setVotes(321);
        dto.setDoubanVotes(654321);

        adminController.updateMovie(8888L, dto);

        ArgumentCaptor<Movie> movieCaptor = ArgumentCaptor.forClass(Movie.class);
        verify(adminService).updateMovie(movieCaptor.capture());
        Movie captured = movieCaptor.getValue();

        assertAll(
                () -> assertEquals(Long.valueOf(8888L), captured.getId()),
                () -> assertEquals(8.1, captured.getScore(), 0.0001),
                () -> assertEquals(9.3, captured.getDoubanScore(), 0.0001),
                () -> assertEquals(Integer.valueOf(321), captured.getVotes()),
                () -> assertEquals(Integer.valueOf(654321), captured.getDoubanVotes())
        );
    }
}
