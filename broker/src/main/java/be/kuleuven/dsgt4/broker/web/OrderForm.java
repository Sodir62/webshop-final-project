package be.kuleuven.dsgt4.broker.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/*
    The event package the customer is buying: exactly one concert (required) plus
    optional food and drinks. A plain form-backing object, NOT the entity --
    price and supplier are resolved server-side, never trusted from the form.
    Constraints are checked when the controller marks the argument @Valid.
*/
public class OrderForm {

    @NotBlank(message = "Please choose a concert.")
    private String ticketProductId;

    @Min(value = 1, message = "At least one ticket.")
    private int ticketQty = 1;

    private String foodProductId;   // blank = no food

    @Min(value = 0, message = "Quantity cannot be negative.")
    private int foodQty = 0;

    private String drinkProductId;  // blank = no drinks

    @Min(value = 0, message = "Quantity cannot be negative.")
    private int drinkQty = 0;

    @NotBlank(message = "A delivery address is required.")
    private String deliveryAddress;

    @NotBlank(message = "A cardholder name is required.")
    private String cardholderName;

    @NotBlank(message = "The last 4 digits of the card are required.")
    @Pattern(regexp = "\\d{4}", message = "Enter the last 4 digits of the card.")
    private String cardLast4;

    @AssertTrue(message = "Choose at least one portion, or select \"no food\".")
    public boolean isFoodLineConsistent() {
        return foodProductId == null || foodProductId.isBlank() || foodQty >= 1;
    }

    @AssertTrue(message = "Choose at least one portion, or select \"no drinks\".")
    public boolean isDrinkLineConsistent() {
        return drinkProductId == null || drinkProductId.isBlank() || drinkQty >= 1;
    }

    public String getTicketProductId() {
        return ticketProductId;
    }

    public void setTicketProductId(String ticketProductId) {
        this.ticketProductId = ticketProductId;
    }

    public int getTicketQty() {
        return ticketQty;
    }

    public void setTicketQty(int ticketQty) {
        this.ticketQty = ticketQty;
    }

    public String getFoodProductId() {
        return foodProductId;
    }

    public void setFoodProductId(String foodProductId) {
        this.foodProductId = foodProductId;
    }

    public int getFoodQty() {
        return foodQty;
    }

    public void setFoodQty(int foodQty) {
        this.foodQty = foodQty;
    }

    public String getDrinkProductId() {
        return drinkProductId;
    }

    public void setDrinkProductId(String drinkProductId) {
        this.drinkProductId = drinkProductId;
    }

    public int getDrinkQty() {
        return drinkQty;
    }

    public void setDrinkQty(int drinkQty) {
        this.drinkQty = drinkQty;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getCardholderName() {
        return cardholderName;
    }

    public void setCardholderName(String cardholderName) {
        this.cardholderName = cardholderName;
    }

    public String getCardLast4() {
        return cardLast4;
    }

    public void setCardLast4(String cardLast4) {
        this.cardLast4 = cardLast4;
    }
}
