package com.pela.roadmap.application.port.out;

import com.pela.roadmap.application.port.in.VocabularyCatalogUseCase.CatalogEntry;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Bảng vocabulary. */
public interface VocabularyPort {

    Map<String, CatalogEntry> findCurated(Collection<String> lowercaseWords);

    UUID insertPrivateWordIfAbsent(UUID ownerId, String word, String meaningVi, String exampleSentence, String cefrLevel);
}
