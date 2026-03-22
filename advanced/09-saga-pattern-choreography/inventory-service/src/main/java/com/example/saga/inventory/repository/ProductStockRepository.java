package com.example.saga.inventory.repository;

import com.example.saga.inventory.domain.ProductStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ProductStock} entities.
 *
 * <p>The custom {@link #findByIdForUpdate(String)} method uses a pessimistic
 * write lock ({@code SELECT ... FOR UPDATE}) to prevent concurrent threads or
 * service instances from reserving the same stock simultaneously (preventing
 * overselling under concurrent load).
 */
@Repository
public interface ProductStockRepository extends JpaRepository<ProductStock, String> {

    /**
     * Finds a product stock record and acquires a pessimistic write lock.
     *
     * <p>The lock ensures that only one transaction can modify this row at a time.
     * If two service instances try to reserve stock for the same product concurrently,
     * one will block until the other commits or rolls back.
     *
     * @param productId the product identifier
     * @return an Optional containing the locked stock record if found
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM ProductStock ps WHERE ps.productId = :productId")
    Optional<ProductStock> findByIdForUpdate(String productId);
}
