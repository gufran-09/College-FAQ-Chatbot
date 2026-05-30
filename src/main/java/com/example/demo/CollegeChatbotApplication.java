package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the College FAQ Chatbot backend.
 *
 * @SpringBootApplication  = @Configuration + @EnableAutoConfiguration + @ComponentScan
 *   It scans all classes in this package and sub-packages for @Service,
 *   @Repository, @Controller etc. and wires them together automatically.
 *
 * @EnableAsync  enables the @Async annotation used in IngestionService.
 *   Without this, @Async does nothing and ingestion blocks the HTTP thread.
 */
@SpringBootApplication
@EnableAsync
public class CollegeChatbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(CollegeChatbotApplication.class, args);
		System.out.println("\n=================================");
		System.out.println("  College FAQ Chatbot is running");
		System.out.println("  Admin API: http://localhost:8080/api/admin");
		System.out.println("=================================\n");
	}
}