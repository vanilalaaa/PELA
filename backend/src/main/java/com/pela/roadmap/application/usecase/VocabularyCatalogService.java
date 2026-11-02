package com.pela.roadmap.application.usecase;

import com.pela.roadmap.application.port.in.VocabularyCatalogUseCase;
import com.pela.roadmap.application.port.out.VocabularyPort;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class VocabularyCatalogService implements VocabularyCatalogUseCase {

    private final VocabularyPort vocabulary;

    public VocabularyCatalogService(VocabularyPort vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Override
    public Map<String, CatalogEntry> findCurated(Collection<String> words) {
        Set<String> lowercase = words.stream()
                .filter(w -> w != null && !w.isBlank())
                .map(w -> w.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return lowercase.isEmpty() ? Map.of() : vocabulary.findCurated(lowercase);
    }

    @Override
    public UUID addPrivateWord(UUID ownerId, String word, String meaningVi, String exampleSentence, String cefrLevel) {
        if (word == null || word.isBlank() || meaningVi == null || meaningVi.isBlank()) {
            throw new IllegalArgumentException("Word and meaning are required");
        }
        return vocabulary.insertPrivateWordIfAbsent(ownerId, word.trim(), meaningVi.trim(), exampleSentence, cefrLevel);
    }
}
