package be.kuleuven.dsgt4.broker.supplier;

import be.kuleuven.dsgt4.broker.data.SupplierType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.List;

/*
    Wires one SupplierClient per supplier type. Each type has exactly one active client.

    FOOD and DRINK are served by the real food-and-beverages service over HTTP (its base URL
    is configured per environment). TICKET has no real service yet, so it stays on the
    in-process stub.

    The "stub" profile swaps FOOD and DRINK back to in-process stubs, so the two-phase-commit
    tests can drive a controllable supplier (e.g. simulate an outage) without a running service.
*/
@Configuration
public class SupplierConfig {

    @Bean
    public SupplierClient ticketSupplier() {
        return new StubSupplierClient(SupplierType.TICKET, List.of(
                new Product("T-001", "Coldplay @ Sportpaleis", "2026-08-15, main floor", new BigDecimal("85.00"), 100),
                new Product("T-002", "Metallica @ Werchter", "2026-07-05 - only 2 left!", new BigDecimal("120.00"), 2),
                new Product("T-003", "Daft Punk Reunion @ AB", "2026-09-20, standing", new BigDecimal("95.00"), 40),
                new Product("T-004", "Stromae @ Forest National", "2026-10-02, seated", new BigDecimal("75.00"), 150)
        ));
    }

    @Bean
    @Profile("!stub")
    public SupplierClient foodSupplier(@Value("${suppliers.food.base-url}") String baseUrl) {
        return new HttpSupplierClient(SupplierType.FOOD, baseUrl);
    }

    @Bean
    @Profile("!stub")
    public SupplierClient drinkSupplier(@Value("${suppliers.drink.base-url}") String baseUrl) {
        return new HttpSupplierClient(SupplierType.DRINK, baseUrl);
    }

    @Bean
    @Profile("stub")
    public SupplierClient foodSupplierStub() {
        return new StubSupplierClient(SupplierType.FOOD, List.of(
                new Product("F-001", "Nachos", "Sharing portion", new BigDecimal("6.50"), 200),
                new Product("F-002", "Loaded fries", "With cheese & bacon", new BigDecimal("7.00"), 180),
                new Product("F-003", "Veggie wrap", "Falafel & hummus", new BigDecimal("8.50"), 120)
        ));
    }

    @Bean
    @Profile("stub")
    public SupplierClient drinkSupplierStub() {
        return new StubSupplierClient(SupplierType.DRINK, List.of(
                new Product("D-001", "Trappist beer", "33cl bottle", new BigDecimal("4.00"), 500),
                new Product("D-002", "Cola", "33cl can", new BigDecimal("2.50"), 800),
                new Product("D-003", "Sparkling water", "50cl bottle", new BigDecimal("3.00"), 600)
        ));
    }
}