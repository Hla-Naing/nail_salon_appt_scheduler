package com.example.nail_salon_appt_scheduler;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppointmentServiceTest {
    AppointmentRepository repository;
    AppointmentService service;
    @BeforeEach void setup() {
        repository = mock(AppointmentRepository.class);
        service = new AppointmentService(repository);
    }
    void status(int code, Runnable action) {
        assertEquals(code, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }
    @Test void booksFutureFreeSlotAfterLockAndCheck() {
        when(repository.lockSlot(10L)).thenReturn(Optional.of(OffsetDateTime.now().plusDays(1)));
        when(repository.createAppointment(1L,10L)).thenReturn(25L);
        assertEquals(25L, service.bookAppointment(1L,10L));
        var order = inOrder(repository);
        order.verify(repository).lockSlot(10L);
        order.verify(repository).isSlotBooked(10L);
        order.verify(repository).createAppointment(1L,10L);
    }
    @Test void missingSlot() { status(404, () -> service.bookAppointment(1L,10L)); }
    @Test void pastSlot() {
        when(repository.lockSlot(10L)).thenReturn(Optional.of(OffsetDateTime.now().minusMinutes(1)));
        status(400, () -> service.bookAppointment(1L,10L));
        verify(repository, never()).createAppointment(any(),any());
    }
    @Test void bookedSlot() {
        when(repository.lockSlot(10L)).thenReturn(Optional.of(OffsetDateTime.now().plusDays(1)));
        when(repository.isSlotBooked(10L)).thenReturn(true);
        status(409, () -> service.bookAppointment(1L,10L));
        verify(repository, never()).createAppointment(any(),any());
    }
    @Test void uniqueIndexConflictDoesNotRetry() {
        when(repository.lockSlot(10L)).thenReturn(Optional.of(OffsetDateTime.now().plusDays(1)));
        when(repository.createAppointment(1L,10L)).thenThrow(new DuplicateKeyException("internal SQL"));
        status(409, () -> service.bookAppointment(1L,10L));
        verify(repository, times(1)).createAppointment(1L,10L);
    }
    @ParameterizedTest @NullSource @ValueSource(longs={0,-1})
    void invalidSlot(Long id) { status(400, () -> service.bookAppointment(1L,id)); verifyNoInteractions(repository); }
    @Test void ownerCancelsWithZeroEarlyFee() {
        when(repository.lockCustomerAppointment(25L,1L)).thenReturn(Optional.of(
                new AppointmentCancelInfo(25L,1L,OffsetDateTime.now().plusHours(6),"BOOKED")));
        assertEquals(BigDecimal.ZERO, service.cancelAppointment(1L,25L));
        verify(repository).cancelAppointment(25L,BigDecimal.ZERO);
    }
    @Test void anotherCustomerCannotCancel() {
        status(404, () -> service.cancelAppointment(2L,25L));
        verify(repository).lockCustomerAppointment(25L,2L);
        verify(repository,never()).cancelAppointment(any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"CANCELLED","COMPLETED"})
    void cannotCancelNonBooked(String state) {
        when(repository.lockCustomerAppointment(25L,1L)).thenReturn(Optional.of(
                new AppointmentCancelInfo(25L,1L,OffsetDateTime.now().plusDays(1),state)));
        status(409, () -> service.cancelAppointment(1L,25L));
    }
    @Test void cannotCancelStartedAppointment() {
        when(repository.lockCustomerAppointment(25L,1L)).thenReturn(Optional.of(
                new AppointmentCancelInfo(25L,1L,OffsetDateTime.now().minusMinutes(1),"BOOKED")));
        status(400, () -> service.cancelAppointment(1L,25L));
    }
    @Test void lateFee() {
        when(repository.lockCustomerAppointment(25L,1L)).thenReturn(Optional.of(
                new AppointmentCancelInfo(25L,1L,OffsetDateTime.now().plusHours(4),"BOOKED")));
        assertEquals(new BigDecimal("10.00"),service.cancelAppointment(1L,25L));
        verify(repository).cancelAppointment(25L,new BigDecimal("10.00"));
    }
    @Test void historyRefreshesStoredCompletionFirst() {
        service.getCustomerAppointments(1L);
        var order=inOrder(repository);
        order.verify(repository).completePastAppointments();
        order.verify(repository).findByCustomerId(1L);
    }
}
