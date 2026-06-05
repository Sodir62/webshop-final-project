package be.kuleuven.dsgt4.food_and_beverages.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.math.BigDecimal;

/*
   One food or drink product this supplier sells, stored in the supplier's own database.

   The id (F-001, D-001, ...) and the fields mirror the broker's
   be.kuleuven.dsgt4.broker.supplier.Product record, so GET /products serialises 1:1
   into what the broker deserialises.
*/
@Entity
public class Product {

    @Id
    @Column(length = 16)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    // Units available to reserve right now. Held units are subtracted at reserve time.
    @Column(nullable = false)
    private int stock;

    // FOOD or DRINK, so the broker (which treats them as two suppliers) can list one kind.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Category category;

    protected Product() {
    }

    public Product(String id, String name, String description, BigDecimal price, int stock, Category category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public Category getCategory() {
        return category;
    }
}