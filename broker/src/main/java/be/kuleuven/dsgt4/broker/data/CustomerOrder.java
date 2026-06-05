package be.kuleuven.dsgt4.broker.data;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
    One customer's order, stored in the broker's own database.

    Id is a broker-generated UUID (String) so the broker has an order reference
    Stored as a 36-char String
    An order is composed of one or more OrderItems.
    Entity = Because it goes inside of the DB
 */

@Entity
public class CustomerOrder {
    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String deliveryAddress;

    @Column(nullable = false)
    private String cardholderName;

    @Column(length = 4, nullable = false)
    private String cardLast4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /*
      The items that make up this order
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String deliveryAddress, String cardholderName, String cardLast4) {
        this.deliveryAddress = deliveryAddress;
        this.cardholderName = cardholderName;
        this.cardLast4 = cardLast4;
    }

    public String getId() {
        return id;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public BigDecimal total() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
