package com.example.nail_salon_appt_scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @RestController
    static class FailingController {
        @GetMapping("/unexpected") public void unexpected() { throw new IllegalStateException("secret SQL and password"); }
        @GetMapping("/conflict") public void conflict() { throw new DataIntegrityViolationException("secret constraint"); }
    }
    @Test void unexpectedErrorIsGenericAndDoesNotLeakInternals() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred"))
                .andExpect(jsonPath("$.path").value("/unexpected"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        mvc.perform(get("/conflict")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Request conflicts with an existing record"));
    }
}
