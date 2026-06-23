package com.casper.docrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

/** RAG 文件問答系統 — 方案 A 後端入口。 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
public class DocRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocRagApplication.class, args);
    }
}
