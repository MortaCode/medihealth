package com.myy.medihealth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan(basePackages = {"com.myy.medihealth.**.mapper"})
public class MediHealthApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediHealthApplication.class, args);
    }
}
