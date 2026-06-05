package be.kuleuven.dsgt4.food_and_beverages.config;

import be.kuleuven.dsgt4.food_and_beverages.data.Product;
import be.kuleuven.dsgt4.food_and_beverages.data.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/*
   Seeds the food + drink catalog on first start (only when the table is empty, so existing
   stock survives restarts). These are exactly the products the broker's StubSupplierClient
   currently fakes for FOOD and DRINK, so this service is a drop-in replacement for both.
*/
@Component
public class CatalogSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final ProductRepository products;

    public CatalogSeeder(ProductRepository products) {
        this.products = products;
    }

    @Override
    public void run(String... args) {
        if (products.count() > 0) {
            return;
        }
        products.saveAll(List.of(
                new Product("F-001", "Nachos", "Sharing portion", new BigDecimal("6.50"), 200),
                new Product("F-002", "Loaded fries", "With cheese & bacon", new BigDecimal("7.00"), 180),
                new Product("F-003", "Veggie wrap", "Falafel & hummus", new BigDecimal("8.50"), 120),
                new Product("D-001", "Trappist beer", "33cl bottle", new BigDecimal("4.00"), 500),
                new Product("D-002", "Cola", "33cl can", new BigDecimal("2.50"), 800),
                new Product("D-003", "Sparkling water", "50cl bottle", new BigDecimal("3.00"), 600)
        ));
        log.info("seeded {} food/drink products", products.count());
    }
}