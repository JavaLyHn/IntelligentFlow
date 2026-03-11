package com.lyhn.coreworkflowjava.workflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WorkflowJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowJavaApplication.class, args);
        System.out.println("""
            
            ========================================
              Java Workflow Engine Started!
            ========================================
              Version: 1.0.0-SNAPSHOT
              Port: 7880
              Health: http://localhost:7880/actuator/health
            ========================================
            
            """);
    }
}
