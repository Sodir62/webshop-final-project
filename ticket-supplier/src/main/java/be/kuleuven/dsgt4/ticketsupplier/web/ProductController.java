package be.kuleuven.dsgt4.ticketsupplier.web;

import be.kuleuven.dsgt4.ticketsupplier.service.TicketSupplierService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProductController {

    private final TicketSupplierService supplier;

    public ProductController(TicketSupplierService supplier) {
        this.supplier = supplier;
    }

    @GetMapping("/products")
    public List<ProductResponse> list(@RequestParam(required = false) String type) {
        return supplier.listProducts().stream()
                .map(p -> new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getStock()))
                .toList();
    }
}
