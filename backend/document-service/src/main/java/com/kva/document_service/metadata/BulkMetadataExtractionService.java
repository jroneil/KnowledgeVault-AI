package com.kva.document_service.metadata;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BulkMetadataExtractionService {

    private static final Pattern MODEL_PATTERN = Pattern.compile("(?im)\\bmodel\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9._/-]{1,63})\\b");
    private static final Pattern DOCUMENT_NUMBER_PATTERN = Pattern.compile(
            "(?im)\\b(?:document|doc|manual)\\s*(?:no|number|#)?\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9._/-]{2,63})\\b"
    );
    private static final Pattern REVISION_PATTERN = Pattern.compile("(?im)\\b(?:revision|rev|version|ver)\\s*[:#-]?\\s*([A-Z0-9._/-]{1,20})\\b");
    private static final Pattern MANUFACTURER_PATTERN = Pattern.compile("(?im)\\bmanufacturer\\s*[:#-]?\\s*([^\\r\\n]{2,120})");
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("(?im)\\b(?:language|lang)\\s*[:#-]?\\s*([A-Za-z]{2,40})\\b");
    private static final List<String> DOCUMENT_TYPE_KEYWORDS = List.of(
            "manual", "datasheet", "specification", "procedure", "guide", "work instruction", "report"
    );
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/").appendValue(ChronoField.YEAR, 4).toFormatter(Locale.US),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMM d uuuu").toFormatter(Locale.US),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMMM d uuuu").toFormatter(Locale.US)
    );

    public DocumentMetadataExtractionResult extract(MultipartFile file) {
        String originalFilename = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String normalizedName = stripExtension(originalFilename);
        String titleFromFilename = toTitle(normalizedName);
        ExtractionSource source = readSources(file);

        Map<String, Double> confidence = new LinkedHashMap<>();
        String extractedTitle = firstNonBlank(source.pdfTitle(), firstNonBlankLine(source.firstPageText()), titleFromFilename);
        confidence.put("title", source.pdfTitle() != null ? 0.95 : firstNonBlankLine(source.firstPageText()) != null ? 0.75 : 0.45);

        String manufacturer = firstPatternGroup(MANUFACTURER_PATTERN, source.searchableText())
                .or(() -> inferManufacturerFromFilename(normalizedName))
                .orElse(null);
        if (manufacturer != null) {
            confidence.put("manufacturer", source.searchableText().toLowerCase(Locale.ROOT).contains("manufacturer") ? 0.85 : 0.4);
        }

        String model = firstPatternGroup(MODEL_PATTERN, source.searchableText())
                .or(() -> inferModelFromFilename(normalizedName))
                .orElse(null);
        if (model != null) {
            confidence.put("model", source.searchableText().toLowerCase(Locale.ROOT).contains("model") ? 0.85 : 0.45);
        }

        String documentType = inferDocumentType(normalizedName, source.searchableText()).orElse(null);
        if (documentType != null) {
            confidence.put("documentType", 0.7);
        }

        String documentNumber = firstPatternGroup(DOCUMENT_NUMBER_PATTERN, source.searchableText())
                .or(() -> inferDocumentNumberFromFilename(normalizedName))
                .orElse(null);
        if (documentNumber != null) {
            confidence.put("documentNumber", source.searchableText().toLowerCase(Locale.ROOT).contains("document") ? 0.8 : 0.5);
        }

        String revision = firstPatternGroup(REVISION_PATTERN, source.searchableText())
                .or(() -> inferRevisionFromFilename(normalizedName))
                .orElse(null);
        if (revision != null) {
            confidence.put("revision", 0.7);
        }

        String language = firstPatternGroup(LANGUAGE_PATTERN, source.searchableText())
                .or(() -> inferLanguageFromFilename(normalizedName))
                .orElse(null);
        if (language != null) {
            confidence.put("language", source.searchableText().toLowerCase(Locale.ROOT).contains("language") ? 0.8 : 0.45);
        }

        LocalDate publicationDate = inferDate(source.searchableText()).orElse(null);
        if (publicationDate != null) {
            confidence.put("publicationDate", 0.55);
        }

        Integer pageCount = source.pageCount();
        if (pageCount != null) {
            confidence.put("pageCount", 1.0);
        }

        List<String> tags = inferTags(documentType, language, manufacturer);
        if (!tags.isEmpty()) {
            confidence.put("tags", 0.5);
        }

        boolean needsReview = confidence.values().stream().anyMatch(value -> value < 0.65)
                || documentType == null
                || documentNumber == null;

        return DocumentMetadataExtractionResult.builder()
                .extractedTitle(extractedTitle)
                .extractedManufacturer(manufacturer)
                .extractedModel(model)
                .extractedDocumentType(documentType)
                .extractedDocumentNumber(documentNumber)
                .extractedRevision(revision)
                .extractedLanguage(language)
                .extractedPublicationDate(publicationDate)
                .extractedPageCount(pageCount)
                .extractedTags(tags.isEmpty() ? null : tags)
                .confidenceByField(confidence)
                .sourceSummary(source.summary())
                .needsReview(needsReview)
                .build();
    }

    private ExtractionSource readSources(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (filename.endsWith(".pdf")) {
                return readPdf(file);
            }
            return readText(file);
        } catch (IOException ex) {
            log.warn("Metadata extraction failed for {}", file.getOriginalFilename(), ex);
            return new ExtractionSource(null, null, "", "", null, "filename-only");
        }
    }

    private ExtractionSource readPdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDDocumentInformation information = document.getDocumentInformation();
            PDFTextStripper firstPageStripper = new PDFTextStripper();
            firstPageStripper.setStartPage(1);
            firstPageStripper.setEndPage(Math.min(1, document.getNumberOfPages()));
            String firstPageText = normalizeWhitespace(firstPageStripper.getText(document));

            PDFTextStripper fullStripper = new PDFTextStripper();
            fullStripper.setStartPage(1);
            fullStripper.setEndPage(Math.min(document.getNumberOfPages(), 3));
            String searchableText = normalizeWhitespace(fullStripper.getText(document));

            return new ExtractionSource(
                    blankToNull(information.getTitle()),
                    blankToNull(information.getAuthor()),
                    firstPageText,
                    searchableText,
                    document.getNumberOfPages(),
                    "pdf-properties,filename,first-page-text"
            );
        }
    }

    private ExtractionSource readText(MultipartFile file) throws IOException {
        String text = normalizeWhitespace(new String(file.getBytes(), StandardCharsets.UTF_8));
        return new ExtractionSource(
                null,
                null,
                firstPageEquivalent(text),
                text,
                text.isBlank() ? 0 : 1,
                "filename,text-content"
        );
    }

    private String firstPageEquivalent(String text) {
        if (text == null) {
            return "";
        }
        int limit = Math.min(text.length(), 2000);
        return text.substring(0, limit);
    }

    private Optional<String> inferManufacturerFromFilename(String normalizedName) {
        String[] parts = normalizedName.split("[-_ ]+");
        if (parts.length == 0) {
            return Optional.empty();
        }
        String candidate = parts[0];
        if (candidate.length() < 3 || candidate.matches("(?i)manual|guide|spec|datasheet")) {
            return Optional.empty();
        }
        return Optional.of(toTitle(candidate));
    }

    private Optional<String> inferModelFromFilename(String normalizedName) {
        return Arrays.stream(normalizedName.split("[-_ ]+"))
                .filter(this::looksLikeModelToken)
                .findFirst();
    }

    private Optional<String> inferDocumentNumberFromFilename(String normalizedName) {
        return Arrays.stream(normalizedName.split("[-_ ]+"))
                .filter(token -> token.matches("[A-Z0-9]{3,}(?:[._/-][A-Z0-9]{2,})*"))
                .findFirst();
    }

    private Optional<String> inferRevisionFromFilename(String normalizedName) {
        Matcher matcher = Pattern.compile("(?i)(?:^|[-_ ])(?:rev|revision|v)([A-Z0-9._-]{1,10})(?:$|[-_ ])").matcher(normalizedName);
        if (matcher.find()) {
            return Optional.ofNullable(blankToNull(matcher.group(1)));
        }
        return Optional.empty();
    }

    private Optional<String> inferLanguageFromFilename(String normalizedName) {
        String lower = normalizedName.toLowerCase(Locale.ROOT);
        if (lower.contains("english") || lower.matches(".*(?:^|[-_ ])en(?:$|[-_ ]).*")) {
            return Optional.of("English");
        }
        if (lower.contains("spanish") || lower.matches(".*(?:^|[-_ ])es(?:$|[-_ ]).*")) {
            return Optional.of("Spanish");
        }
        return Optional.empty();
    }

    private Optional<String> inferDocumentType(String normalizedName, String searchableText) {
        String lower = (normalizedName + " " + searchableText).toLowerCase(Locale.ROOT);
        return DOCUMENT_TYPE_KEYWORDS.stream()
                .filter(lower::contains)
                .map(this::toTitle)
                .findFirst();
    }

    private List<String> inferTags(String documentType, String language, String manufacturer) {
        Set<String> tags = new LinkedHashSet<>();
        if (documentType != null) {
            tags.add(documentType.toLowerCase(Locale.ROOT).replace(' ', '-'));
        }
        if (language != null) {
            tags.add(language.toLowerCase(Locale.ROOT));
        }
        if (manufacturer != null) {
            tags.add(manufacturer.toLowerCase(Locale.ROOT).replace(' ', '-'));
        }
        return new ArrayList<>(tags);
    }

    private Optional<LocalDate> inferDate(String searchableText) {
        List<Pattern> patterns = List.of(
                Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b"),
                Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b"),
                Pattern.compile("\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{4}\\b", Pattern.CASE_INSENSITIVE)
        );

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(searchableText);
            if (matcher.find()) {
                String value = matcher.group().replace(",", "");
                for (DateTimeFormatter formatter : DATE_FORMATTERS) {
                    try {
                        return Optional.of(LocalDate.parse(value, formatter));
                    } catch (DateTimeParseException ignored) {
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstPatternGroup(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return Optional.ofNullable(blankToNull(normalizeWhitespace(matcher.group(1))));
        }
        return Optional.empty();
    }

    private String firstNonBlankLine(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .map(line -> line.length() > 500 ? line.substring(0, 500) : line)
                .orElse(null);
    }

    private String normalizeWhitespace(String text) {
        return text == null ? "" : text.replace('\u0000', ' ').replaceAll("[ \\t\\x0B\\f\\r]+", " ").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean looksLikeModelToken(String token) {
        return token != null
                && token.matches("[A-Z0-9]{2,}(?:[-_][A-Z0-9]{1,})+")
                && !token.matches("(?i)rev.*|manual|guide|spec|datasheet");
    }

    private String toTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = Normalizer.normalize(raw.replaceAll("[-_]+", " "), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return Arrays.stream(normalized.split(" "))
                .filter(token -> !token.isBlank())
                .map(token -> token.substring(0, 1).toUpperCase(Locale.ROOT) + token.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    private String stripExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ExtractionSource(
            String pdfTitle,
            String pdfAuthor,
            String firstPageText,
            String searchableText,
            Integer pageCount,
            String summary
    ) {
    }
}
