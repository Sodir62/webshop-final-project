package be.kuleuven.dsgt4.ticketsupplier.data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/*
   MongoDB version of TicketProduct. Stored in the "ticket_products" collection.
   No @Entity or JPA annotations — uses Spring Data MongoDB instead.
*/
@Document(collection = "ticket_products")
public class MongoTicketProduct {

    @Id
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private int stock;

    public MongoTicketProduct() {
    }

    public MongoTicketProduct(String id, String name, String description, BigDecimal price, int stock) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
}
