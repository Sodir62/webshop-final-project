package be.kuleuven.dsgt4.broker.web;

import be.kuleuven.dsgt4.broker.data.CustomerOrderRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/*
   Manager dashboard: every customer's order, newest first. Behind the MANAGER role (see
   SecurityConfig); a read-only view over the broker's own order table.
*/
@Controller
public class ManagerController {

    private final CustomerOrderRepository orders;

    public ManagerController(CustomerOrderRepository orders) {
        this.orders = orders;
    }

    @GetMapping("/manager/orders")
    public String orders(Model model) {
        model.addAttribute("orders", orders.findAllByOrderByCreatedAtDesc());
        return "manager";
    }
}
