package kr.ac.dankook.campuson;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampusonApplication {

	public static void main(String[] args) {
		SpringApplication.run(CampusonApplication.class, args);
	}

}