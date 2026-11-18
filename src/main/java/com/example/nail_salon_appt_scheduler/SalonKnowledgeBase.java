package com.example.nail_salon_appt_scheduler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class SalonKnowledgeBase {
    private static final Logger log = LoggerFactory.getLogger(SalonKnowledgeBase.class);
    private final Optional<EmbeddingModel> embeddings;
    private final Resource source;
    private final Clock clock;
    private volatile SimpleVectorStore store;
    private Instant nextBuildAttempt = Instant.MIN;

    SalonKnowledgeBase(Optional<EmbeddingModel> embeddings, @Value("${salon.ai.knowledge-source}") Resource source,
                       Clock clock) {
        this.embeddings = embeddings;
        this.source = source;
        this.clock = clock;
    }

    List<Document> retrieve(String query) {
        try {
            var current = store;
            if (current == null) current = initializeIfDue();
            if (current == null) return List.of();
            return current.similaritySearch(SearchRequest.builder().query(query).topK(3)
                    .similarityThreshold(0.5).build());
        } catch (RuntimeException | IOException e) {
            log.warn("event=salon_knowledge_unavailable cause={}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    private synchronized SimpleVectorStore initializeIfDue() throws IOException {
        if (store == null && embeddings.isPresent() && !clock.instant().isBefore(nextBuildAttempt)) {
            try {
                reload();
            } catch (RuntimeException | IOException e) {
                nextBuildAttempt = clock.instant().plusSeconds(60);
                throw e;
            }
        }
        return store;
    }

    synchronized int reload() throws IOException {
        var embeddingModel = embeddings.orElseThrow();
        String markdown = source.getContentAsString(StandardCharsets.UTF_8);
        List<Document> documents = Arrays.stream(markdown.split("(?m)^## "))
                .skip(1).map(String::strip).map(this::toDocument).toList();
        if (documents.isEmpty()) throw new IOException("Knowledge source must contain level-two headings");
        var replacement = SimpleVectorStore.builder(embeddingModel).build();
        replacement.add(documents);
        store = replacement;
        return documents.size();
    }

    private Document toDocument(String section) {
        String[] parts = section.split("\\R", 2);
        if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
            throw new IllegalArgumentException("Knowledge sections require a heading and body");
        }
        return new Document(section, Map.of("source", parts[0].strip()));
    }
}
