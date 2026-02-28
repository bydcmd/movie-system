package com.movie.backend.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovieMapperXmlRegressionTest {

    @Test
    void shouldKeepSiteAndDoubanScoreMappingsIndependent() throws IOException {
        String compactXml = compact(readMovieMapperXml());

        assertAll(
                () -> assertTrue(compactXml.contains("<result column=\"douban_votes\" property=\"doubanVotes\" />"),
                        "douban_votes should map to doubanVotes"),
                () -> assertTrue(compactXml.contains("movie_id, name, alias, actors, cover, directors, douban_score, score, douban_votes, votes, genres,"),
                        "Base_Column_List should include both douban and site score columns"),
                () -> assertTrue(compactXml.contains("<select id=\"selectList\" resultMap=\"BaseResultMap\"> SELECT <include refid=\"Base_Column_List\"/> FROM movies"),
                        "selectList should reuse Base_Column_List to avoid missing score columns"),
                () -> assertTrue(compactXml.contains("#{doubanScore}, #{score}, #{doubanVotes}, #{votes}, #{genres}"),
                        "insert should bind doubanScore/score and doubanVotes/votes to separate columns"),
                () -> assertTrue(compactXml.contains("<if test=\"doubanScore != null\">douban_score = #{doubanScore},</if>"),
                        "update should write doubanScore to douban_score"),
                () -> assertTrue(compactXml.contains("<if test=\"score != null\">score = #{score},</if>"),
                        "update should write score to score"),
                () -> assertTrue(compactXml.contains("<if test=\"doubanVotes != null\">douban_votes = #{doubanVotes},</if>"),
                        "update should write doubanVotes to douban_votes"),
                () -> assertTrue(compactXml.contains("<if test=\"votes != null\">votes = #{votes},</if>"),
                        "update should write votes to votes"),
                () -> assertTrue(compactXml.contains("SET score = #{siteScore}, votes = #{siteVotes}"),
                        "updateMovieScore should update site score and site votes")
        );
    }

    private String readMovieMapperXml() throws IOException {
        ClassPathResource resource = new ClassPathResource("mapper/MovieMapper.xml");
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String compact(String source) {
        return source.replaceAll("\\s+", " ").trim();
    }
}
