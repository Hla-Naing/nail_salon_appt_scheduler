package com.example.nail_salon_appt_scheduler;

import java.time.Clock;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SalonKnowledgeBaseTest {
    @Test
    void ingestsFaqAndFindsRelevantCancellationPolicyWithVectorSearch() throws Exception {
        var model = mock(EmbeddingModel.class);
        when(model.embed(any(Document.class))).thenAnswer(invocation -> {
            var document = invocation.<Document>getArgument(0);
            return document.getText().startsWith("Cancellation policy") ? new float[]{1, 0} : new float[]{0, 1};
        });
        when(model.embed("What fee applies if I cancel?")).thenReturn(new float[]{1, 0});
        var knowledge = new SalonKnowledgeBase(Optional.of(model), new ClassPathResource("knowledge/salon-faq.md"),
                Clock.systemUTC());

        var results = knowledge.retrieve("What fee applies if I cancel?");
        assertEquals(1, results.size());
        assertEquals("Cancellation policy", results.getFirst().getMetadata().get("source"));
        assertTrue(results.getFirst().getText().contains("$10.00"));
        verify(model, times(7)).embed(any(Document.class));

        assertEquals(7, knowledge.reload());
        assertEquals(1, knowledge.retrieve("What fee applies if I cancel?").size());
        verify(model, times(14)).embed(any(Document.class));
    }

    @Test
    void failedReloadLeavesLastGoodIndexAvailable() throws Exception {
        var model = mock(EmbeddingModel.class);
        when(model.embed(any(Document.class))).thenReturn(new float[]{1, 0});
        when(model.embed("test")).thenReturn(new float[]{1, 0});
        var knowledge = new SalonKnowledgeBase(Optional.of(model), new ClassPathResource("knowledge/salon-faq.md"),
                Clock.systemUTC());
        assertEquals(7, knowledge.reload());
        when(model.embed(any(Document.class))).thenThrow(new IllegalStateException("embedding outage"));
        assertThrows(IllegalStateException.class, knowledge::reload);
        assertEquals(3, knowledge.retrieve("test").size());
    }
}
