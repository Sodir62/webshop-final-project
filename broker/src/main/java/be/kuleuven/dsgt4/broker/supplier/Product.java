package be.kuleuven.dsgt4.broker.supplier;
import java.math.BigDecimal;

/*
 One product offered by a supplier.
 Java Record automatically creates constructor and getters.
 Big decimal to counter rounding errors.
*/

public record Product(
    String id,
    String name,
    String description,
    BigDecimal price,
    int stock
) {}
