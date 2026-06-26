package com.kva.document_service.search;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticRetrievalRepositoryTests {

    @Test
    @SuppressWarnings("unchecked")
    void findTopSimilarChunksMapsRankedChunkResults() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SemanticRetrievalRepository repository = new SemanticRetrievalRepository(jdbcTemplate);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), anyInt(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    RowMapper<SemanticRetrievalResult> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getLong("chunk_id")).thenReturn(11L);
                    when(rs.getLong("document_id")).thenReturn(22L);
                    when(rs.getLong("version_id")).thenReturn(33L);
                    when(rs.getInt("version_number")).thenReturn(4);
                    when(rs.getInt("chunk_index")).thenReturn(2);
                    when(rs.getString("content")).thenReturn("retrieved chunk");
                    when(rs.getInt("page_number")).thenReturn(7);
                    when(rs.getInt("source_page_from")).thenReturn(7);
                    when(rs.getInt("source_page_to")).thenReturn(8);
                    when(rs.getString("section_name")).thenReturn("Section A");
                    when(rs.getInt("token_count")).thenReturn(123);
                    when(rs.getDouble("similarity_score")).thenReturn(0.91d);
                    when(rs.wasNull()).thenReturn(false);
                    return List.of(mapper.mapRow(rs, 0));
                });

        List<SemanticRetrievalResult> results = repository.findTopSimilarChunks(
                List.of(0.1d, 0.2d, 0.3d),
                "nomic-embed-text",
                3,
                5
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(), any(), anyInt(), any(), anyInt());

        assertEquals(1, results.size());
        assertEquals(11L, results.get(0).getChunkId());
        assertEquals(22L, results.get(0).getDocumentId());
        assertEquals(33L, results.get(0).getVersionId());
        assertEquals(4, results.get(0).getVersionNumber());
        assertEquals(7, results.get(0).getPageNumber());
        assertEquals(7, results.get(0).getSourcePageFrom());
        assertEquals(8, results.get(0).getSourcePageTo());
        assertEquals(0.91d, results.get(0).getSimilarityScore());
        assertEquals("retrieved chunk", results.get(0).getContent());
        assertEquals("Section A", results.get(0).getSectionName());
        assertEquals(123, results.get(0).getTokenCount());
        assertEquals(true, sqlCaptor.getValue().contains("embedding <=> CAST(? AS vector)"));
    }
}
