package com.kva.document_service.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentChunkServiceTests {

    @Test
    void replaceChunksDeletesOnlyCurrentVersionBeforeSavingNewChunks() {
        DocumentChunkRepository repository = mock(DocumentChunkRepository.class);
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        DocumentChunkService service = new DocumentChunkService(repository);

        service.replaceChunks(
                55L,
                42L,
                100L,
                List.of(DocumentChunkDraft.builder()
                        .chunkIndex(0)
                        .content("chunk")
                        .sourcePageFrom(1)
                        .sourcePageTo(1)
                        .tokenCount(1)
                        .build())
        );

        verify(repository).deleteByVersionId(100L);
        verify(repository).saveAll(any());
    }
}
