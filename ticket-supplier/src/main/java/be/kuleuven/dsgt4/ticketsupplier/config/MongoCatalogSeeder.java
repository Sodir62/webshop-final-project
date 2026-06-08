package be.kuleuven.dsgt4.ticketsupplier.config;

import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketProduct;
import be.kuleuven.dsgt4.ticketsupplier.data.MongoTicketProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/*
   Seeds the ticket catalog into MongoDB on first start (only when the
   collection is empty, so existing stock survives restarts).
   Only active when running with the "mongo" profile.
*/
@Component
@Profile("mongo")
public class MongoCatalogSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoCatalogSeeder.class);

    private final MongoTicketProductRepository products;

    public MongoCatalogSeeder(MongoTicketProductRepository products) {
        this.products = products;
    }

    @Override
    public void run(String... args) {
        if (products.count() > 0) {
            return;
        }
        products.saveAll(List.of(
                new MongoTicketProduct("T-001", "Coldplay @ Sportpaleis",    "2026-08-15, main floor",    new BigDecimal("85.00"),  100),
                new MongoTicketProduct("T-002", "Metallica @ Werchter",      "2026-07-05 - only 2 left!", new BigDecimal("120.00"),   2),
                new MongoTicketProduct("T-003", "Daft Punk Reunion @ AB",    "2026-09-20, standing",      new BigDecimal("95.00"),   40),
                new MongoTicketProduct("T-004", "Stromae @ Forest National", "2026-10-02, seated",        new BigDecimal("75.00"),  150)
        ));
        log.info("seeded {} ticket products into MongoDB", products.count());
    }
}
