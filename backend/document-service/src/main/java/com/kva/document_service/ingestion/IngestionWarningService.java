package com.kva.document_service.ingestion;

import com.kva.document_service.ingestion.dto.IngestionWarningResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IngestionWarningService {

    private final IngestionWarningRepository ingestionWarningRepository;

    @Transactional(readOnly = true)
    public List<IngestionWarningResponse> listWarningsForJob(Long ingestionJobId) {
        return ingestionWarningRepository.findByIngestionJobIdOrderByCreatedAtAscIdAsc(ingestionJobId)
                .stream()
                .map(IngestionWarningResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IngestionWarningResponse> listWarningsForVersion(Long versionId) {
        return ingestionWarningRepository.findByVersionIdOrderByCreatedAtAscIdAsc(versionId)
                .stream()
                .map(IngestionWarningResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<IngestionWarningResponse>> listWarningsByJobIds(Collection<Long> ingestionJobIds) {
        if (ingestionJobIds == null || ingestionJobIds.isEmpty()) {
            return Map.of();
        }

        return ingestionWarningRepository
                .findByIngestionJobIdInOrderByIngestionJobIdAscCreatedAtAscIdAsc(ingestionJobIds)
                .stream()
                .map(IngestionWarningResponse::fromEntity)
                .collect(Collectors.groupingBy(IngestionWarningResponse::getIngestionJobId));
    }

    @Transactional
    public void replaceWarnings(Long ingestionJobId,
                                Long documentId,
                                Long versionId,
                                List<String> warningMessages) {
        clearWarningsForJob(ingestionJobId);
        addWarnings(ingestionJobId, documentId, versionId, warningMessages);
    }

    @Transactional
    public void clearWarningsForJob(Long ingestionJobId) {
        ingestionWarningRepository.deleteByIngestionJobId(ingestionJobId);
    }

    @Transactional
    public void addWarnings(Long ingestionJobId,
                            Long documentId,
                            Long versionId,
                            List<String> warningMessages) {
        if (warningMessages == null || warningMessages.isEmpty()) {
            return;
        }

        List<IngestionWarning> warnings = warningMessages.stream()
                .filter(message -> message != null && !message.isBlank())
                .map(message -> IngestionWarning.builder()
                        .ingestionJobId(ingestionJobId)
                        .documentId(documentId)
                        .versionId(versionId)
                        .warningCode(null)
                        .warningMessage(message)
                        .severity(IngestionWarningSeverity.WARNING)
                        .build())
                .toList();

        if (!warnings.isEmpty()) {
            ingestionWarningRepository.saveAll(warnings);
        }
    }

    @Transactional
    public void addError(Long ingestionJobId,
                         Long documentId,
                         Long versionId,
                         String warningCode,
                         String warningMessage) {
        if (warningMessage == null || warningMessage.isBlank()) {
            return;
        }

        ingestionWarningRepository.save(IngestionWarning.builder()
                .ingestionJobId(ingestionJobId)
                .documentId(documentId)
                .versionId(versionId)
                .warningCode(warningCode)
                .warningMessage(warningMessage)
                .severity(IngestionWarningSeverity.ERROR)
                .build());
    }
}
