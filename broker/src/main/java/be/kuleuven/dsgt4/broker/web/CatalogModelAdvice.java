package be.kuleuven.dsgt4.broker.web;

import be.kuleuven.dsgt4.broker.data.SupplierType;
import be.kuleuven.dsgt4.broker.supplier.Product;
import be.kuleuven.dsgt4.broker.supplier.SupplierException;
import be.kuleuven.dsgt4.broker.supplier.SupplierRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/*
   Puts the three catalog sections (tickets/food/drinks) into the model of every
   page, so any view -- including the order form re-rendered with validation errors --
   has them without each controller re-fetching.

   Read-path fault tolerance lives here: if a supplier is down, that section shows
   empty (logged) instead of breaking the page. The ordering path never degrades
   silently -- there a supplier failure rolls the order back.
*/
@ControllerAdvice
public class CatalogModelAdvice {

    private static final Logger log = LoggerFactory.getLogger(CatalogModelAdvice.class);

    private final SupplierRegistry suppliers;

    public CatalogModelAdvice(SupplierRegistry suppliers) {
        this.suppliers = suppliers;
    }

    @ModelAttribute("tickets")
    public List<Product> tickets() {
        return safeList(SupplierType.TICKET);
    }

    @ModelAttribute("food")
    public List<Product> food() {
        return safeList(SupplierType.FOOD);
    }

    @ModelAttribute("drinks")
    public List<Product> drinks() {
        return safeList(SupplierType.DRINK);
    }

    private List<Product> safeList(SupplierType type) {
        try {
            return suppliers.get(type).list();
        } catch (SupplierException e) {
            log.warn("catalog for {} unavailable, showing empty section: {}", type, e.getMessage());
            return List.of();
        }
    }
}
