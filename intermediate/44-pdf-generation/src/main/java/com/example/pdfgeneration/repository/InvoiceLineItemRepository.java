package com.example.pdfgeneration.repository;

import com.example.pdfgeneration.domain.InvoiceLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link InvoiceLineItem} entities.
 *
 * <p>Provides standard CRUD operations plus a custom finder method that
 * retrieves all line items belonging to a specific invoice.
 */
@Repository
public interface InvoiceLineItemRepository extends JpaRepository<InvoiceLineItem, Long> {

    /**
     * Returns all line items whose {@code invoice.id} matches the given value.
     *
     * <p>Spring Data translates the method name into:
     * {@code SELECT * FROM invoice_line_items WHERE invoice_id = ?}
     *
     * @param invoiceId the surrogate key of the parent invoice
     * @return list of line items for that invoice (may be empty, never null)
     */
    List<InvoiceLineItem> findByInvoiceId(Long invoiceId);
}
