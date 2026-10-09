package com.example.nail_salon_appt_scheduler;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named="SALON_TEST_DB_URL", matches=".+")
class LocalPostgresTest extends PostgresWorkflowSuite {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("SALON_TEST_DB_URL");
        if(!url.matches("jdbc:postgresql://[^/]+/salon_m2_test")) {
            throw new IllegalArgumentException("Tests require a dedicated database named salon_m2_test");
        }
        registry.add("spring.datasource.url",()->url);
        registry.add("spring.datasource.username",()->System.getenv().getOrDefault("SALON_TEST_DB_USER","salon_test"));
        registry.add("spring.datasource.password",()->System.getenv().getOrDefault("SALON_TEST_DB_PASSWORD",""));
    }
}
