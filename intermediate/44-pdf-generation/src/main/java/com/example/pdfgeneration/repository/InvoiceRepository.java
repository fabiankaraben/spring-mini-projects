package com.example.pdfgeneration.repository;

import com.example.pdfgeneration.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Invoice} entities.
 *
 * <p>Extending {@link JpaRepository} gives us CRUD operations, pagination,
 * and sorting for free — Spring Data generates the implementation at runtime.
 *
 * <p>Custom query methods declared here follow Spring Data's method naming
 * convention so that the framework generates the corresponding SQL query
 * automatically (no need to write {@code @Query} for simple lookups).
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Finds an invoice by its human-readable invoice number.
     *
     * <p>Spring Data translates {@code findByInvoiceNumber} into
     * {@code SELECT ... FROM invoices WHERE invoice_number = ?} automatically.
     *
     * @param invoiceNumber the unique invoice number to search for
     * @return an {@link Optional} containing the invoice if found, or empty if not
     */
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Checks whether an invoice with the given number already exists.
     *
     * <p>Used by the service to enforce uniqueness before persisting a new invoice,
     * returning a more informative error than a database constraint violation would.
     *
     * @param invoiceNumber the invoice number to check
     * @return {@code true} if an invoice with that number exists, {@code false} otherwise
     */
    boolean existsByInvoiceNumber(String invoiceNumber);
}
