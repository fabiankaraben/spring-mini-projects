package com.example.pdfgeneration.service;

import com.example.pdfgeneration.domain.Invoice;
import com.example.pdfgeneration.domain.InvoiceLineItem;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service responsible for generating PDF byte arrays using the OpenPDF library.
 *
 * <p>OpenPDF is a free, actively maintained fork of iText 2.x released under
 * the LGPL and MPL licences. It provides a rich API for building multi-page
 * PDF documents with tables, fonts, colours, and more.
 *
 * <h2>Key OpenPDF classes used here</h2>
 * <ul>
 *   <li>{@link Document} – the root PDF document object; add elements to it.</li>
 *   <li>{@link PdfWriter} – writes the document to an {@link java.io.OutputStream}.</li>
 *   <li>{@link Paragraph} – a block of text with an associated {@link Font}.</li>
 *   <li>{@link PdfPTable} – a grid-based table with configurable column widths.</li>
 *   <li>{@link PdfPCell} – a single cell inside a {@link PdfPTable}.</li>
 *   <li>{@link Font} – defines typeface, size, style (bold/italic) and colour.</li>
 * </ul>
 *
 * <h2>PDF generation strategy</h2>
 * All output is written into a {@link ByteArrayOutputStream} that lives entirely
 * in memory.  The resulting byte array is returned to the controller which streams
 * it to the HTTP response with the correct {@code Content-Disposition} header so
 * the browser (or curl) treats it as a file download.
 */
@Service
public class PdfGeneratorService {

    // ── Shared font constants ──────────────────────────────────────────────────

    /** Date format used when printing dates in the PDF. */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM d, yyyy");

    /** Bold, 18pt font for the main title. */
    private static final Font FONT_TITLE =
            new Font(Font.HELVETICA, 18, Font.BOLD, Color.DARK_GRAY);

    /** Bold, 12pt font for section headings. */
    private static final Font FONT_SECTION_HEADER =
            new Font(Font.HELVETICA, 12, Font.BOLD, Color.DARK_GRAY);

    /** Regular 11pt font for body content. */
    private static final Font FONT_BODY =
            new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY);

    /** Bold 11pt font for table column headers. */
    private static final Font FONT_TABLE_HEADER =
            new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);

    /** Regular 10pt font for table cell content. */
    private static final Font FONT_TABLE_CELL =
            new Font(Font.HELVETICA, 10, Font.NORMAL, Color.DARK_GRAY);

    /** Bold 11pt font for the totals row. */
    private static final Font FONT_TOTAL =
            new Font(Font.HELVETICA, 11, Font.BOLD, new Color(0, 51, 102));

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generates a single-invoice PDF containing a header, customer information,
     * a line-items table, and a totals section.
     *
     * <p>The PDF layout is:
     * <ol>
     *   <li>Title bar: "INVOICE" and the invoice number.</li>
     *   <li>Two-column metadata block: customer info (left), invoice dates (right).</li>
     *   <li>Line-items table with columns: Description | Qty | Unit Price | Subtotal.</li>
     *   <li>Right-aligned totals row showing the grand total.</li>
     *   <li>Optional notes footer.</li>
     * </ol>
     *
     * @param invoice   the invoice entity (metadata)
     * @param lineItems the list of line items to render in the table
     * @return a byte array containing the complete PDF document
     */
    public byte[] generateInvoicePdf(Invoice invoice, List<InvoiceLineItem> lineItems) {
        // ByteArrayOutputStream accumulates the PDF bytes in memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Document uses A4 page size with 36pt margins (0.5 inch on each side)
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        // PdfWriter connects the Document to the output stream and actually writes PDF syntax
        PdfWriter.getInstance(document, baos);

        // open() must be called before adding any content
        document.open();

        try {
            addInvoiceContent(document, invoice, lineItems);
        } finally {
            // close() flushes and finalises the PDF; called in finally to avoid resource leaks
            document.close();
        }

        return baos.toByteArray();
    }

    /**
     * Generates a summary report PDF listing all invoices stored in the system.
     *
     * <p>The report contains a title, a generation timestamp, and a table
     * with columns: Invoice # | Customer | Date | Currency | Total.
     *
     * @param invoices the full list of invoices to include in the report
     * @return a byte array containing the complete PDF document
     */
    public byte[] generateInvoiceReportPdf(List<Invoice> invoices) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, baos);
        document.open();

        try {
            addReportContent(document, invoices);
        } finally {
            document.close();
        }

        return baos.toByteArray();
    }

    // ── Private helpers – invoice PDF ─────────────────────────────────────────

    /**
     * Adds all content elements to the invoice document.
     *
     * @param document  the open OpenPDF document
     * @param invoice   invoice metadata
     * @param lineItems line items for the table
     */
    private void addInvoiceContent(Document document, Invoice invoice,
                                   List<InvoiceLineItem> lineItems) {
        // ── Title bar ────────────────────────────────────────────────────────
        Paragraph title = new Paragraph("INVOICE", FONT_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        addSafely(document, title);

        Paragraph invoiceNum = new Paragraph(invoice.getInvoiceNumber(), FONT_SECTION_HEADER);
        invoiceNum.setAlignment(Element.ALIGN_CENTER);
        invoiceNum.setSpacingAfter(20);
        addSafely(document, invoiceNum);

        // ── Customer / date info block ───────────────────────────────────────
        // Using a two-column table to place customer info on the left and
        // invoice date on the right, mimicking a typical invoice layout.
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(20);

        // Left cell – customer information
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.addElement(new Paragraph("Bill To:", FONT_SECTION_HEADER));
        leftCell.addElement(new Paragraph(invoice.getCustomerName(), FONT_BODY));
        leftCell.addElement(new Paragraph(invoice.getCustomerEmail(), FONT_BODY));
        infoTable.addCell(leftCell);

        // Right cell – invoice dates
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(createAlignedParagraph(
                "Issue Date:  " + DATE_FORMATTER.format(invoice.getIssueDate()),
                FONT_BODY, Element.ALIGN_RIGHT));
        infoTable.addCell(rightCell);

        addSafely(document, infoTable);

        // ── Horizontal rule (thin coloured table row) ────────────────────────
        addSafely(document, createHorizontalRule());

        // ── Line items table ─────────────────────────────────────────────────
        PdfPTable itemsTable = buildLineItemsTable(lineItems, invoice.getCurrency());
        itemsTable.setSpacingBefore(10);
        itemsTable.setSpacingAfter(10);
        addSafely(document, itemsTable);

        // ── Totals section ───────────────────────────────────────────────────
        addSafely(document, buildTotalsTable(invoice, lineItems));

        // ── Notes (optional) ─────────────────────────────────────────────────
        if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
            addSafely(document, createHorizontalRule());
            Paragraph notesHeader = new Paragraph("Notes:", FONT_SECTION_HEADER);
            notesHeader.setSpacingBefore(10);
            addSafely(document, notesHeader);
            addSafely(document, new Paragraph(invoice.getNotes(), FONT_BODY));
        }
    }

    /**
     * Builds the four-column line-items table (Description | Qty | Unit Price | Subtotal).
     *
     * <p>Column widths are set as relative proportions: 50% / 10% / 20% / 20%.
     * The header row has a dark-blue background with white bold text; data rows
     * alternate between white and a very light grey for readability.
     *
     * @param lineItems list of line items to render
     * @param currency  ISO currency code used as a label
     * @return the fully populated {@link PdfPTable}
     */
    private PdfPTable buildLineItemsTable(List<InvoiceLineItem> lineItems, String currency) {
        // 4 columns; relative widths: description gets 50%, others share the rest
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{50f, 10f, 20f, 20f});
        } catch (DocumentException ignored) {
            // setWidths only throws if the number of widths != number of columns
        }

        // Header row background colour
        Color headerBg = new Color(0, 51, 102); // dark navy

        // Add column headers
        table.addCell(createHeaderCell("Description", headerBg));
        table.addCell(createHeaderCell("Qty", headerBg));
        table.addCell(createHeaderCell("Unit Price (" + currency + ")", headerBg));
        table.addCell(createHeaderCell("Subtotal (" + currency + ")", headerBg));

        // Add data rows
        Color evenRowBg = new Color(240, 245, 255); // very light blue-grey
        boolean isEven = false;
        for (InvoiceLineItem item : lineItems) {
            Color rowBg = isEven ? evenRowBg : Color.WHITE;
            table.addCell(createDataCell(item.getDescription(), Element.ALIGN_LEFT, rowBg));
            table.addCell(createDataCell(String.valueOf(item.getQuantity()), Element.ALIGN_CENTER, rowBg));
            table.addCell(createDataCell(formatAmount(item.getUnitPrice()), Element.ALIGN_RIGHT, rowBg));
            table.addCell(createDataCell(formatAmount(item.getSubtotal()), Element.ALIGN_RIGHT, rowBg));
            isEven = !isEven;
        }

        return table;
    }

    /**
     * Builds the right-aligned totals section showing the grand total amount.
     *
     * @param invoice   invoice entity (for currency and persisted total)
     * @param lineItems line items to sum
     * @return a right-aligned table with the total row
     */
    private PdfPTable buildTotalsTable(Invoice invoice, List<InvoiceLineItem> lineItems) {
        // A two-column table placed at 60% width, right-aligned
        PdfPTable totalsTable = new PdfPTable(2);
        totalsTable.setWidthPercentage(60);
        totalsTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        try {
            totalsTable.setWidths(new float[]{50f, 50f});
        } catch (DocumentException ignored) {
        }

        // Calculate the total from line items (for display)
        BigDecimal computed = lineItems.stream()
                .map(InvoiceLineItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Color totalBg = new Color(220, 235, 255);

        // Label cell
        PdfPCell labelCell = new PdfPCell(new Phrase("TOTAL (" + invoice.getCurrency() + ")", FONT_TOTAL));
        labelCell.setBackgroundColor(totalBg);
        labelCell.setPadding(6);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        totalsTable.addCell(labelCell);

        // Amount cell
        PdfPCell amountCell = new PdfPCell(new Phrase(formatAmount(computed), FONT_TOTAL));
        amountCell.setBackgroundColor(totalBg);
        amountCell.setPadding(6);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalsTable.addCell(amountCell);

        return totalsTable;
    }

    // ── Private helpers – report PDF ──────────────────────────────────────────

    /**
     * Adds report content (title, date, invoices summary table) to the document.
     *
     * @param document the open OpenPDF document
     * @param invoices list of all invoices to include
     */
    private void addReportContent(Document document, List<Invoice> invoices) {
        // Report title
        Paragraph title = new Paragraph("Invoice Summary Report", FONT_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(6);
        addSafely(document, title);

        Paragraph generated = new Paragraph(
                "Generated on: " + java.time.LocalDate.now().format(DATE_FORMATTER),
                FONT_BODY);
        generated.setAlignment(Element.ALIGN_CENTER);
        generated.setSpacingAfter(20);
        addSafely(document, generated);

        addSafely(document, createHorizontalRule());

        // Summary table
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        try {
            table.setWidths(new float[]{20f, 35f, 15f, 10f, 20f});
        } catch (DocumentException ignored) {
        }

        Color headerBg = new Color(0, 51, 102);
        table.addCell(createHeaderCell("Invoice #", headerBg));
        table.addCell(createHeaderCell("Customer", headerBg));
        table.addCell(createHeaderCell("Issue Date", headerBg));
        table.addCell(createHeaderCell("Currency", headerBg));
        table.addCell(createHeaderCell("Total", headerBg));

        Color evenRowBg = new Color(240, 245, 255);
        boolean isEven = false;
        for (Invoice inv : invoices) {
            Color rowBg = isEven ? evenRowBg : Color.WHITE;
            table.addCell(createDataCell(inv.getInvoiceNumber(), Element.ALIGN_LEFT, rowBg));
            table.addCell(createDataCell(inv.getCustomerName(), Element.ALIGN_LEFT, rowBg));
            table.addCell(createDataCell(
                    inv.getIssueDate() != null ? inv.getIssueDate().format(DateTimeFormatter.ISO_DATE) : "",
                    Element.ALIGN_CENTER, rowBg));
            table.addCell(createDataCell(inv.getCurrency(), Element.ALIGN_CENTER, rowBg));
            table.addCell(createDataCell(formatAmount(inv.getTotalAmount()), Element.ALIGN_RIGHT, rowBg));
            isEven = !isEven;
        }

        addSafely(document, table);

        // Footer count
        Paragraph footer = new Paragraph(
                "\nTotal invoices: " + invoices.size(), FONT_SECTION_HEADER);
        footer.setAlignment(Element.ALIGN_RIGHT);
        addSafely(document, footer);
    }

    // ── Cell factory helpers ──────────────────────────────────────────────────

    /**
     * Creates a table header cell with the given background colour and white bold text.
     *
     * @param text the header label
     * @param bg   background colour for the cell
     * @return the configured {@link PdfPCell}
     */
    private PdfPCell createHeaderCell(String text, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TABLE_HEADER));
        cell.setBackgroundColor(bg);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderColor(Color.WHITE);
        return cell;
    }

    /**
     * Creates a standard data cell with optional alignment and background colour.
     *
     * @param text      the cell content
     * @param alignment {@link Element} alignment constant (ALIGN_LEFT, etc.)
     * @param bg        background colour (use {@link Color#WHITE} for plain rows)
     * @return the configured {@link PdfPCell}
     */
    private PdfPCell createDataCell(String text, int alignment, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TABLE_CELL));
        cell.setBackgroundColor(bg);
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    /**
     * Creates a thin full-width horizontal rule using a coloured, borderless table.
     *
     * <p>OpenPDF does not have a built-in "horizontal line" element; a single-row,
     * single-cell table with a coloured background is the idiomatic workaround.
     *
     * @return a thin table that visually acts as a horizontal divider
     */
    private PdfPTable createHorizontalRule() {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setFixedHeight(2f);
        cell.setBackgroundColor(new Color(0, 51, 102));
        cell.setBorder(Rectangle.NO_BORDER);
        rule.addCell(cell);
        return rule;
    }

    /**
     * Creates a {@link Paragraph} with the given text, font, and horizontal alignment.
     *
     * @param text      the paragraph text
     * @param font      the font to apply
     * @param alignment {@link Element} alignment constant
     * @return the configured {@link Paragraph}
     */
    private Paragraph createAlignedParagraph(String text, Font font, int alignment) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(alignment);
        return p;
    }

    // ── Utility helpers ───────────────────────────────────────────────────────

    /**
     * Formats a {@link BigDecimal} as a string with two decimal places.
     * Returns "0.00" if the value is null.
     *
     * @param amount the amount to format
     * @return formatted string, e.g. "1,234.50"
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return String.format("%,.2f", amount);
    }

    /**
     * Adds an {@link Element} to the document, wrapping any {@link DocumentException}
     * in a {@link RuntimeException}.
     *
     * <p>OpenPDF's {@link Document#add(Element)} declares a checked
     * {@link DocumentException}; this helper keeps caller code clean.
     *
     * @param document the target document
     * @param element  the element to add
     */
    private void addSafely(Document document, Element element) {
        try {
            document.add(element);
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to add element to PDF document", e);
        }
    }
}
