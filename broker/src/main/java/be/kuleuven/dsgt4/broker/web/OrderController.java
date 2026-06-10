package be.kuleuven.dsgt4.broker.web;

import be.kuleuven.dsgt4.broker.data.CustomerOrder;
import be.kuleuven.dsgt4.broker.data.CustomerOrderRepository;
import be.kuleuven.dsgt4.broker.data.OrderItem;
import be.kuleuven.dsgt4.broker.data.SupplierType;
import be.kuleuven.dsgt4.broker.supplier.Product;
import be.kuleuven.dsgt4.broker.supplier.SupplierException;
import be.kuleuven.dsgt4.broker.supplier.SupplierRegistry;
import be.kuleuven.dsgt4.broker.transaction.OrderProcessor;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

/*
   Places an order and shows one. On submit the order is built server-side, saved as
   CREATED, then handed to the OrderProcessor: the default profile runs the two-phase
   reserve->confirm across suppliers in this request; the 'async' profile enqueues it
   and the order page shows it progressing. Either way it ends SUCCEEDED, or FAILED
   with any held items released.
*/
@Controller
public class OrderController {

    private final CustomerOrderRepository orders;
    private final SupplierRegistry suppliers;
    private final OrderProcessor orderProcessor;

    public OrderController(CustomerOrderRepository orders, SupplierRegistry suppliers, OrderProcessor orderProcessor) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.orderProcessor = orderProcessor;
    }

    // The order page for one concert: the concert is fixed by the URL, the form binds the rest.
    @GetMapping("/concerts/{id}")
    public String concert(@PathVariable String id, @ModelAttribute("orderForm") OrderForm form, Model model) {
        Product concert = suppliers.get(SupplierType.TICKET).find(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such concert " + id));
        form.setTicketProductId(id);          // preset the hidden field to this concert
        model.addAttribute("concert", concert);
        return "concert";
    }

    @PostMapping("/orders")
    public String place(@Valid @ModelAttribute("orderForm") OrderForm form, BindingResult binding, Model model) {
        // Invalid input: re-render the concert's order page with the field errors preserved.
        if (binding.hasErrors()) {
            return reshowConcert(form, model);
        }

        CustomerOrder order = new CustomerOrder(form.getDeliveryAddress(), form.getCardholderName(), form.getCardLast4());

        addLine(order, SupplierType.TICKET, form.getTicketProductId(), form.getTicketQty());
        if (StringUtils.hasText(form.getFoodProductId())) {
            addLine(order, SupplierType.FOOD, form.getFoodProductId(), form.getFoodQty());
        }
        if (StringUtils.hasText(form.getDrinkProductId())) {
            addLine(order, SupplierType.DRINK, form.getDrinkProductId(), form.getDrinkQty());
        }

        orders.save(order);
        orderProcessor.process(order.getId());
        return "redirect:/orders/" + order.getId();
    }

    @GetMapping("/orders/{id}")
    public String detail(@PathVariable String id, Model model) {
        CustomerOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such order " + id));
        model.addAttribute("order", order);
        return "order";
    }

    // On a validation error we re-render the chosen concert's order page (not the landing
    // grid), reloading it from the submitted ticketProductId. A blank/unknown id (or a
    // supplier that's down) has no concert to show -> fall back to the landing page.
    private String reshowConcert(OrderForm form, Model model) {
        String ticketId = form.getTicketProductId();
        if (!StringUtils.hasText(ticketId)) {
            return "redirect:/";
        }
        Optional<Product> concert;
        try {
            concert = suppliers.get(SupplierType.TICKET).find(ticketId);
        } catch (SupplierException e) {
            return "redirect:/";
        }
        if (concert.isEmpty()) {
            return "redirect:/";
        }
        model.addAttribute("concert", concert.get());
        return "concert";
    }

    // Looks the product up at its supplier to snapshot the real price, then adds the line.
    private void addLine(CustomerOrder order, SupplierType type, String productId, int quantity) {
        Product product = suppliers.get(type).find(productId)
                .orElseThrow(() -> new IllegalArgumentException("unknown product " + productId + " at " + type));
        order.addItem(new OrderItem(type, productId, product.name(), product.price(), quantity));
    }
}
