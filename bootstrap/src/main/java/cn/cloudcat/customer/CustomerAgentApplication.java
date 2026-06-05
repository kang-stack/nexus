package cn.cloudcat.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cn.cloudcat")
public class CustomerAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerAgentApplication.class, args);
    }
}
