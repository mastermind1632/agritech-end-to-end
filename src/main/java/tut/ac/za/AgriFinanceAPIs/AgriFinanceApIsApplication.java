package tut.ac.za.AgriFinanceAPIs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgriFinanceApIsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgriFinanceApIsApplication.class, args);
    }
}
