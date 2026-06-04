package be.kuleuven.dsgt4.broker.supplier;

import be.kuleuven.dsgt4.broker.data.SupplierType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

/*
    Hardcoded examples of suppliers.
*/
@Configuration
public class SupplierConfig {

    @Bean
    public SupplierClient ticketSupplier() {
        return new StubSupplierClient(SupplierType.TICKET, List.of(
                new Product("T-001", "Coldplay @ Sportpaleis", "2026-08-15, main floor", new BigDecimal("85.00"), 100),
                new Product("T-002", "Metallica @ Werchter", "2026-07-05 - only 2 left!", new BigDecimal("120.00"), 2)
        ));
    }

    @Bean
    public SupplierClient foodSupplier() {
        return new StubSupplierClient(SupplierType.FOOD, List.of(
                new Product("F-001", "Nachos", "Sharing portion", new BigDecimal("6.50"), 200)
        ));
    }

    @Bean
    public SupplierClient drinkSupplier() {
        return new StubSupplierClient(SupplierType.DRINK, List.of(
                new Product("D-001", "Trappist beer", "33cl bottle", new BigDecimal("4.00"), 500)
        ));
    }
}
