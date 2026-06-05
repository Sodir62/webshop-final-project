package be.kuleuven.dsgt4.food_and_beverages.data;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

/*
    Data access for products.
*/
public interface ProductRepository extends JpaRepository<Product, String> {

    // Reserve/cancel take a write lock on the product row so concurrent orders can't
    // oversell or double-restore the same stock (SELECT ... FOR UPDATE on MySQL/PostgreSQL).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findWithLockById(String id);
}