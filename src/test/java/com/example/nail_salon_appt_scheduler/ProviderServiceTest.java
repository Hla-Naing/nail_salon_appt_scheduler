package com.example.nail_salon_appt_scheduler;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderServiceTest {
    ProviderRepository repository;
    AppointmentRepository appointments;
    ProviderService service;
    OffsetDateTime start = OffsetDateTime.now().plusDays(1);
    @BeforeEach void setup() {
        repository=mock(ProviderRepository.class); appointments=mock(AppointmentRepository.class);
        service=new ProviderService(repository,appointments);
        when(repository.findProviderId(2L)).thenReturn(Optional.of(7L));
    }
    void status(int code,Runnable action) {
        assertEquals(code,assertThrows(ResponseStatusException.class,action::run).getStatusCode().value());
    }
    @Test void createsForSessionUserProfile() {
        when(repository.serviceExists(3L)).thenReturn(true);
        when(repository.createSlot(7L,3L,start,start.plusHours(1))).thenReturn(20L);
        assertEquals(20L,service.createSlot(2L,3L,start,start.plusHours(1)));
        verify(repository).createSlot(7L,3L,start,start.plusHours(1));
    }
    @Test void requiresProfile() { status(404,()->service.createSlot(99L,3L,start,start.plusHours(1))); }
    @Test void requiresService() { status(404,()->service.createSlot(2L,3L,start,start.plusHours(1))); }
    @Test void requiresFields() {
        status(400,()->service.createSlot(2L,null,start,start.plusHours(1)));
        status(400,()->service.createSlot(2L,3L,null,start));
        status(400,()->service.createSlot(2L,3L,start,null));
        status(400,()->service.createSlot(2L,-1L,start,start.plusHours(1)));
    }
    @Test void rejectsPastAndReversedTimes() {
        status(400,()->service.createSlot(2L,3L,start.minusDays(2),start));
        status(400,()->service.createSlot(2L,3L,start,start.minusMinutes(1)));
        status(400,()->service.createSlot(2L,3L,start,start));
        verify(repository,never()).createSlot(any(),any(),any(),any());
    }
    @Test void cannotRemoveAnotherProvidersSlot() {
        status(404,()->service.removeSlot(2L,20L));
        verify(repository).lockOwnedSlot(20L,7L);
        verify(repository,never()).removeSlot(any());
    }
    @Test void cannotRemoveBookedSlot() {
        when(repository.lockOwnedSlot(20L,7L)).thenReturn(true);
        when(appointments.isSlotBooked(20L)).thenReturn(true);
        status(409,()->service.removeSlot(2L,20L));
        verify(repository,never()).removeSlot(any());
    }
    @Test void removesFreeOwnedSlotAfterLock() {
        when(repository.lockOwnedSlot(20L,7L)).thenReturn(true);
        service.removeSlot(2L,20L);
        var order=inOrder(repository,appointments);
        order.verify(repository).findProviderId(2L);
        order.verify(repository).lockOwnedSlot(20L,7L);
        order.verify(appointments).isSlotBooked(20L);
        order.verify(repository).removeSlot(20L);
    }
}
