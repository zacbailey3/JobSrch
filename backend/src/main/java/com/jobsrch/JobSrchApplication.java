package com.jobsrch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobSrchApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobSrchApplication.class, args);
	}

}
