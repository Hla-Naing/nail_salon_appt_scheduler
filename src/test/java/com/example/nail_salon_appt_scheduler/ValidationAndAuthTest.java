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
    @Test void registrationTrimsFieldsHashesPasswordAndAuthenticates() {
        var repo = mock(UserRepository.class);
        when(repo.createCustomer(eq("Test Customer"), eq("testcustomer"), anyString())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(2);
            assertNotEquals("TestPassword123!", hash);
            assertTrue(hash.startsWith("$2"));
            assertTrue(new BCryptPasswordEncoder().matches("TestPassword123!", hash));
            var user = new UserAccount(42L, "Test Customer", "testcustomer", hash, "CUSTOMER");
            when(repo.findById(42L)).thenReturn(Optional.of(user));
            when(repo.findByUsername("testcustomer")).thenReturn(Optional.of(user));
            return 42L;
        });
        var auth = new AuthService(repo);
        var user = auth.registerCustomer(" Test Customer ", " testcustomer ", "TestPassword123!", "TestPassword123!");
        assertEquals("CUSTOMER", user.role());
        assertEquals(user, auth.authenticate("testcustomer", "TestPassword123!"));
        verify(repo).usernameExists("testcustomer");
    }

    @Test void registrationRejectsInvalidFieldsBeforeWriting() {
        var repo = mock(UserRepository.class);
        var auth = new AuthService(repo);
        String[][] cases = {
                {null, "newuser", "password123", "password123", "Name is required"},
                {"  ", "newuser", "password123", "password123", "Name is required"},
                {"Name", null, "password123", "password123", "Username is required"},
                {"Name", "  ", "password123", "password123", "Username is required"},
                {"Name", "newuser", null, null, "Password is required"},
                {"Name", "newuser", "        ", "        ", "Password is required"},
                {"Name", "newuser", "short12", "short12", "Password must be at least 8 characters"},
                {"Name", "newuser", "password123", "different123", "Passwords do not match"},
                {"Name", "newuser", "password123", null, "Passwords do not match"},
                {"N".repeat(101), "newuser", "password123", "password123", "Name must be at most 100 characters"},
                {"Name", "u".repeat(51), "password123", "password123", "Username must be at most 50 characters"},
                {"Name", "newuser", "é".repeat(37), "é".repeat(37), "Password must be at most 72 UTF-8 bytes"}
        };
        for (String[] input : cases) {
            var error = assertThrows(ResponseStatusException.class,
                    () -> auth.registerCustomer(input[0], input[1], input[2], input[3]));
            assertEquals(400, error.getStatusCode().value());
            assertEquals(input[4], error.getReason());
        }
        verifyNoInteractions(repo);
    }

    @Test void registrationRejectsDuplicateUsernameIncludingInsertRace() {
        var repo = mock(UserRepository.class);
        var auth = new AuthService(repo);
        when(repo.usernameExists("Taken")).thenReturn(true);
        var duplicate = assertThrows(ResponseStatusException.class,
                () -> auth.registerCustomer("Name", " Taken ", "password123", "password123"));
        assertEquals("Username is already taken", duplicate.getReason());
        verify(repo, never()).createCustomer(anyString(), anyString(), anyString());
        when(repo.createCustomer(eq("Name"), eq("racing"), anyString()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
        var race = assertThrows(ResponseStatusException.class,
                () -> auth.registerCustomer("Name", "racing", "password123", "password123"));
        assertEquals(400, race.getStatusCode().value());
        assertEquals("Username is already taken", race.getReason());
    }

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
