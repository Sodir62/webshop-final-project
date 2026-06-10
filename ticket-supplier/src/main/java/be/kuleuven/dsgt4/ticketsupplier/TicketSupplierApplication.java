package be.kuleuven.dsgt4.ticketsupplier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TicketSupplierApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketSupplierApplication.class, args);
    }
}
