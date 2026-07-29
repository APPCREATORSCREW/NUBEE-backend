package com.solux31.nubee_BE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication
@EnableAsync
public class NubeeBeApplication {

	public static void main(String[] args) {
		// SpringApplication이 실행되기 전에 JVM 타임존을 최우선으로 설정
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));

		SpringApplication.run(NubeeBeApplication.class, args);
	}
}
