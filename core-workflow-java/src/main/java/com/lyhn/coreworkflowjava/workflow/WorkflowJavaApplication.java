package com.lyhn.coreworkflowjava.workflow;

import com.lyhn.coreworkflowjava.link.tools.config.LinkConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(LinkConfiguration.class) // 将不在主类扫描范围内的配置类导入到 Spring 容器
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
