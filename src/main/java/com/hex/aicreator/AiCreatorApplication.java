package com.hex.aicreator;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.hex.aicreator.mapper")
@EnableAsync
@SpringBootApplication
public class AiCreatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCreatorApplication.class, args);
    }
}
