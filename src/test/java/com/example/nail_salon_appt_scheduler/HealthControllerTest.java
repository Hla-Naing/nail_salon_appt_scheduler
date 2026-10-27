package com.example.nail_salon_appt_scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthControllerTest {
    @Test void reportsHealthyAndUnavailableDatabaseWithoutLeakingConnectionDetails() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var mvc = MockMvcBuilders.standaloneSetup(new HealthController(jdbc)).build();
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.database").value("UP"));
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new DataAccessResourceFailureException("secret connection string"));
        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.application").value("UP")).andExpect(jsonPath("$.database").value("DOWN"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
