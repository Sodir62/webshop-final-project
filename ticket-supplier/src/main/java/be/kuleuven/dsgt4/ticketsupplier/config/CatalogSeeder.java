package be.kuleuven.dsgt4.ticketsupplier.config;

import be.kuleuven.dsgt4.ticketsupplier.data.TicketProduct;
import be.kuleuven.dsgt4.ticketsupplier.data.TicketProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CatalogSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final TicketProductRepository products;

    public CatalogSeeder(TicketProductRepository products) {
        this.products = products;
    }

    @Override
    public void run(String... args) {
        if (products.count() > 0) {
            return;
        }
        products.saveAll(List.of(
                new TicketProduct("T-001", "Coldplay @ Sportpaleis",    "2026-08-15, main floor",    new BigDecimal("85.00"),  100),
                new TicketProduct("T-002", "Metallica @ Werchter",      "2026-07-05 - only 2 left!", new BigDecimal("120.00"),   2),
                new TicketProduct("T-003", "Daft Punk Reunion @ AB",    "2026-09-20, standing",      new BigDecimal("95.00"),   40),
                new TicketProduct("T-004", "Stromae @ Forest National", "2026-10-02, seated",        new BigDecimal("75.00"),  150)
        ));
        log.info("seeded {} ticket products", products.count());
    }
}
