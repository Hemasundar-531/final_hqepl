package com.company.flowmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FlowmanagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(FlowmanagementApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.boot.CommandLineRunner initData(
			com.company.flowmanagement.repository.FMSStepRepository repository) {
		return args -> {
			if (repository.count() == 0) {
				com.company.flowmanagement.model.FMSStep step = new com.company.flowmanagement.model.FMSStep();
				step.setStepName("Sample Step");
				step.setResponsibilityPerson("Admin");
				step.setTargetDate("2026-03-01");
				step.setStatus("Pending");
				step.setRemarks("Initial dummy record");

				// Added specific fields requested by user
				step.setCompletedDate("2026-02-28");
				step.setPdfLink("http://example.com/sample.pdf");
				step.setAtsScore("85/100");
				step.setExtraInfo("Pending Final Review");

				repository.save(step);
				System.out.println("Dummy FMSStep inserted to initialize collection with all fields.");
			}
		};
	}

}
