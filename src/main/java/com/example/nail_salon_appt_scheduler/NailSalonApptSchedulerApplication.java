package com.example.nail_salon_appt_scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NailSalonApptSchedulerApplication {

	public static void main(String[] args) {
		SpringApplication.run(NailSalonApptSchedulerApplication.class, args);
	}

}
