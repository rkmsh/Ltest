package com.rkmsh.ltest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.shell.command.annotation.CommandScan;

@SpringBootApplication
@CommandScan
public class LtestApplication {

	public static void main(String[] args) {
		SpringApplication.run(LtestApplication.class, args);
	}

}
