package com.celebstash.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		// Every LocalDateTime.now() in the codebase is stored and serialised without a zone. Pinning
		// the JVM to UTC makes those values UTC wherever the server runs (a developer laptop in
		// Kigali, a cloud host), so clients can convert them to the viewer's local time correctly.
		// Hosted deployments already ran in UTC, so existing production data is unaffected.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(BackendApplication.class, args);
	}

}
