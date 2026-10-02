package wot.thermostat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Thermostat (port 8081). */
@SpringBootApplication
@EnableScheduling
public class ThermostatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThermostatApplication.class, args);
    }
}
