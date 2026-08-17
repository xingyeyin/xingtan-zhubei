package com.xingtan.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 杏坛智备后端启动类
 */
@MapperScan({
        "com.xingtan.system.mapper",
        "com.xingtan.kb.mapper",
        "com.xingtan.ai.mapper",
        "com.xingtan.lesson.mapper",
        "com.xingtan.stats.mapper"
})
@SpringBootApplication(scanBasePackages = "com.xingtan")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
