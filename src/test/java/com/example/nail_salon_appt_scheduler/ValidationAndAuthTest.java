package com.example.nail_salon_appt_scheduler;

import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValidationAndAuthTest {
    @Test void beanValidationRejectsBadRequests() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            assertFalse(validator.validate(new LoginController.LoginRequest(" "," ")).isEmpty());
            assertFalse(validator.validate(new CustomerAppointmentController.BookingRequest(null)).isEmpty());
            assertFalse(validator.validate(new CustomerAppointmentController.BookingRequest(-1L)).isEmpty());
            assertFalse(validator.validate(new ProviderController.SlotRequest(1L,OffsetDateTime.now().minusDays(1),null)).isEmpty());
        }
    }
    @Test void paginationAndFiltersAreValidatedInService() {
        var repository=mock(SalonRepository.class); var service=new SalonService(repository);
        for(int[] input:new int[][]{{-1,10},{0,0},{0,51}}) {
            assertEquals(400,assertThrows(ResponseStatusException.class,
                    ()->service.getAvailableSlots(null,null,null,input[0],input[1])).getStatusCode().value());
        }
        assertThrows(ResponseStatusException.class,()->service.getAvailableSlots(-1L,null,null,0,10));
        service.getAvailableSlots(null,null,null,Integer.MAX_VALUE,50);
        verify(repository).findAvailableSlots(null,null,null,50,107374182350L);
    }
    @Test void bcryptAuthenticatesAndRejectsBadPassword() {
        var repo=mock(UserRepository.class);
        var user=new UserAccount(1L,"Maya","maya",new BCryptPasswordEncoder().encode("TestPassword123!"),"CUSTOMER");
        when(repo.findByUsername("maya")).thenReturn(Optional.of(user));
        var auth=new AuthService(repo);
        assertEquals(user,auth.authenticate("maya","TestPassword123!"));
        assertEquals(401,assertThrows(ResponseStatusException.class,()->auth.authenticate("maya","wrong")).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->auth.authenticate("", "")).getStatusCode().value());
    }
    @Test void roleComesFromDatabaseAndLoginReplacesSession() {
        var repo=mock(UserRepository.class); var auth=new SessionAuthService(repo);
        var request=new MockHttpServletRequest();
        assertEquals(401,assertThrows(ResponseStatusException.class,()->auth.requireRole(request,"CUSTOMER")).getStatusCode().value());
        var user=new UserAccount(1L,"Maya","maya","hash","CUSTOMER");
        when(repo.findById(1L)).thenReturn(Optional.of(user));
        var old=request.getSession();
        auth.login(request,user);
        assertNotSame(old,request.getSession());
        request.getSession().setAttribute("role","PROVIDER");
        assertEquals(user,auth.requireRole(request,"CUSTOMER"));
        assertEquals(403,assertThrows(ResponseStatusException.class,()->auth.requireRole(request,"PROVIDER")).getStatusCode().value());
    }
    @Test void localTimesRejectDaylightSavingGapsAndAmbiguity() {
        var time=new SalonTime();
        assertThrows(ResponseStatusException.class,()->time.parse("2027-03-14T02:30"));
        assertThrows(ResponseStatusException.class,()->time.parse("2026-11-01T01:30"));
        assertEquals("-07:00",time.parse("2026-10-20T10:00").getOffset().toString());
    }
}
