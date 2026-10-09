package com.example.nail_salon_appt_scheduler;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class PostgresWorkflowSuite {
    @Autowired JdbcTemplate jdbc;
    @Autowired AppointmentService booking;
    @Autowired ProviderService providers;
    @Autowired MockMvc mvc;
    long customer1, customer2, providerUser, otherProviderUser, providerId, otherProviderId, serviceId, slotId;
    String suffix;
    OffsetDateTime start;
    static final String PASSWORD = "TestPassword123!";
    static final String HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    @BeforeEach void fixtures() {
        suffix = UUID.randomUUID().toString().substring(0,8);
        customer1 = user("customer1", "CUSTOMER"); customer2 = user("customer2", "CUSTOMER");
        providerUser = user("provider", "PROVIDER"); otherProviderUser = user("other", "PROVIDER");
        providerId = jdbc.queryForObject("INSERT INTO providers(user_id) VALUES (?) RETURNING provider_id",Long.class,providerUser);
        otherProviderId = jdbc.queryForObject("INSERT INTO providers(user_id) VALUES (?) RETURNING provider_id",Long.class,otherProviderUser);
        serviceId = jdbc.queryForObject("INSERT INTO services(name,duration_minutes,price) VALUES (?,60,45) RETURNING service_id",Long.class,"Test service " + suffix);
        start = OffsetDateTime.now().plusDays(3).withNano(0);
        slotId = slot(providerId,start);
    }
    long user(String name,String role) {
        return jdbc.queryForObject("INSERT INTO users(name,username,password_hash,role) VALUES (?,?,?,?) RETURNING user_id",
                Long.class,name,name+suffix,HASH,role);
    }
    long slot(long owner,OffsetDateTime time) {
        return jdbc.queryForObject("INSERT INTO availability_slots(provider_id,service_id,start_at,end_at) VALUES (?,?,?,?) RETURNING slot_id",
                Long.class,owner,serviceId,time,time.plusHours(1));
    }
    @AfterEach void cleanupOnlyOurFixtures() {
        jdbc.update("DELETE FROM appointments WHERE slot_id IN (SELECT slot_id FROM availability_slots WHERE service_id=?)",serviceId);
        jdbc.update("DELETE FROM availability_slots WHERE service_id=?",serviceId);
        jdbc.update("DELETE FROM services WHERE service_id=?",serviceId);
        jdbc.update("DELETE FROM providers WHERE provider_id IN (?,?)",providerId,otherProviderId);
        jdbc.update("DELETE FROM users WHERE user_id IN (?,?,?,?)",customer1,customer2,providerUser,otherProviderUser);
    }
    MockHttpSession login(String name) throws Exception {
        return (MockHttpSession)mvc.perform(post("/auth/login").contentType("application/json")
                .content("{\"username\":\""+name+suffix+"\",\"password\":\""+PASSWORD+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getRequest().getSession(false);
    }

    @Test void exactlyOneOfTwoConcurrentCustomersBooksTheSlot() throws Exception {
        assertTrue(AopUtils.isAopProxy(booking),"Must call the real transactional Spring proxy");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<String>> results = new ArrayList<>();
            for(long customer : new long[]{customer1,customer2}) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if(!go.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                    try {
                        booking.bookAppointment(customer,slotId);
                        return "SUCCESS";
                    } catch(ResponseStatusException e) {
                        if(e.getStatusCode().value() != 409) throw e;
                        return "CONFLICT";
                    }
                }));
            }
            assertTrue(ready.await(10,TimeUnit.SECONDS));
            go.countDown();
            List<String> outcomes = new ArrayList<>();
            for(Future<String> result:results) outcomes.add(result.get(15,TimeUnit.SECONDS));
            assertEquals(1,Collections.frequency(outcomes,"SUCCESS"));
            assertEquals(1,Collections.frequency(outcomes,"CONFLICT"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id=? AND status='BOOKED'",Integer.class,slotId));
        } finally {
            go.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));
        }
    }

    @Test void apiAuthenticationRbacAndValidation() throws Exception {
        mvc.perform(get("/customer/appointments")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/auth/login").contentType("application/json").content("{\"username\":\"customer1"+suffix+"\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.path").value("/auth/login"));
        mvc.perform(post("/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        var customer = login("customer1"); var provider = login("provider");
        mvc.perform(get("/provider/appointments").session(customer)).andExpect(status().isForbidden());
        mvc.perform(get("/customer/appointments").session(provider)).andExpect(status().isForbidden());
        for(String body : List.of("{}","{\"slotId\":0}","{broken")) {
            mvc.perform(post("/customer/appointments").session(customer).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.timestamp").exists());
        }
        mvc.perform(post("/provider/slots").session(provider).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/slots").param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/slots").param("size","51")).andExpect(status().isBadRequest());
        mvc.perform(get("/slots").param("date","invalid")).andExpect(status().isBadRequest());
        mvc.perform(post("/auth/logout").session(customer)).andExpect(status().isOk());
        mvc.perform(get("/auth/me").session(customer)).andExpect(status().isUnauthorized());
    }

    @Test void bookingOwnershipProviderRemovalAndFilters() throws Exception {
        var customer=login("customer1"); var other=login("customer2"); var provider=login("provider");
        mvc.perform(get("/slots").param("providerId", ""+providerId).param("serviceId",""+serviceId)
                .param("date",start.atZoneSameInstant(SalonTime.ZONE).toLocalDate().toString()).param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].slotId").value(slotId));
        mvc.perform(get("/slots").param("providerId",""+providerId).param("page","1").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/customer/appointments").session(customer).contentType("application/json").content("{\"slotId\":"+slotId+"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/customer/appointments").session(other).contentType("application/json").content("{\"slotId\":"+slotId+"}"))
                .andExpect(status().isConflict());
        long appointment=jdbc.queryForObject("SELECT appointment_id FROM appointments WHERE slot_id=?",Long.class,slotId);
        mvc.perform(get("/customer/appointments").session(other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/customer/appointments/"+appointment).session(other)).andExpect(status().isNotFound());
        mvc.perform(get("/provider/appointments").session(provider)).andExpect(status().isOk()).andExpect(jsonPath("$[0].appointmentId").value(appointment));
        mvc.perform(get("/provider/appointments").session(login("other"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/provider/slots/"+slotId).session(login("other"))).andExpect(status().isNotFound());
        mvc.perform(delete("/provider/slots/"+slotId).session(provider)).andExpect(status().isConflict());
        mvc.perform(delete("/customer/appointments/"+appointment).session(customer)).andExpect(status().isOk()).andExpect(jsonPath("$.feeCharged").value(0));
        mvc.perform(delete("/provider/slots/"+slotId).session(provider)).andExpect(status().isOk());
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM appointments WHERE appointment_id=?",String.class,appointment));
        assertNotNull(jdbc.queryForObject("SELECT removed_at FROM availability_slots WHERE slot_id=?",OffsetDateTime.class,slotId));
        mvc.perform(post("/customer/appointments").session(other).contentType("application/json").content("{\"slotId\":"+slotId+"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/provider/slots").session(provider).contentType("application/json").content(
                "{\"serviceId\":"+serviceId+",\"startAt\":\""+start.plusDays(1)+"\",\"endAt\":\""+start.plusDays(1).plusHours(1)+"\"}"))
                .andExpect(status().isCreated());
    }

    @Test void completionIsPersistedWithoutChangingCancelledOrFutureBookings() {
        long past = slot(providerId,start.minusDays(5));
        long cancelled = slot(providerId,start.minusDays(6));
        jdbc.update("INSERT INTO appointments(customer_id,slot_id,status) VALUES (?,?,'BOOKED'),(?,?,'CANCELLED'),(?,?,'BOOKED')",
                customer1,past,customer1,cancelled,customer1,slotId);
        booking.getCustomerAppointments(customer1);
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM appointments WHERE slot_id=?",String.class,past));
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM appointments WHERE slot_id=?",String.class,cancelled));
        assertEquals("BOOKED",jdbc.queryForObject("SELECT status FROM appointments WHERE slot_id=?",String.class,slotId));
    }

    @Test void uniqueIndexBackstopRejectsDirectDuplicate() {
        booking.bookAppointment(customer1,slotId);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->jdbc.update(
                "INSERT INTO appointments(customer_id,slot_id) VALUES (?,?)",customer2,slotId));
    }

    @Test void webPagesRenderAndFormsUseSessionAndCsrf() throws Exception {
        mvc.perform(get("/web")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Nail Salon")));
        mvc.perform(get("/web/slots")).andExpect(status().isOk());
        var loginPage=mvc.perform(get("/web/login")).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)loginPage.getRequest().getSession();
        mvc.perform(post("/web/login").session(session).param("username","customer1"+suffix).param("password",PASSWORD))
                .andExpect(status().isForbidden());
        mvc.perform(post("/web/login").session(session).param("csrfToken",(String)session.getAttribute("csrfToken"))
                .param("username","customer1"+suffix).param("password","wrong"))
                .andExpect(status().isUnauthorized()).andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid username or password")));
        var logged=mvc.perform(post("/web/login").session(session).param("csrfToken",(String)session.getAttribute("csrfToken"))
                .param("username","customer1"+suffix).param("password",PASSWORD)).andExpect(redirectedUrl("/web/appointments")).andReturn();
        var customer=(MockHttpSession)logged.getRequest().getSession();
        mvc.perform(get("/web/slots").session(customer)).andExpect(status().isOk());
        var result=mvc.perform(post("/web/book").session(customer).param("csrfToken",(String)customer.getAttribute("csrfToken"))
                .param("slotId",""+slotId)).andExpect(status().is3xxRedirection()).andReturn();
        String confirmation=result.getResponse().getRedirectedUrl();
        mvc.perform(get(confirmation).session(customer)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("BOOKED")));
        mvc.perform(get(confirmation).session(login("customer2"))).andExpect(status().isNotFound());
        mvc.perform(get("/web/appointments").session(customer)).andExpect(status().isOk());
        mvc.perform(get("/web/provider").session(login("provider"))).andExpect(status().isOk());
        mvc.perform(get("/web/provider").session(customer)).andExpect(status().isForbidden());
    }
}
