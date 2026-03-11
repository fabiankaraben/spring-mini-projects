package com.example.excelexport.service;

import com.example.excelexport.model.Product;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Service responsible for generating Excel (.xlsx) workbooks using Apache POI.
 *
 * <p>Apache POI provides two APIs for working with Excel files:
 * <ul>
 *   <li><b>HSSF</b> – for the legacy binary <em>.xls</em> format (Excel 97-2003)</li>
 *   <li><b>XSSF</b> – for the modern XML-based <em>.xlsx</em> format (Excel 2007+)</li>
 * </ul>
 * This project uses {@link XSSFWorkbook} (XSSF) because .xlsx is the current standard
 * and supports more than 65 535 rows per sheet.
 *
 * <p>Key Apache POI concepts used here:
 * <ul>
 *   <li>{@link Workbook}  – the top-level Excel file container</li>
 *   <li>{@link Sheet}     – a tab/worksheet inside the workbook</li>
 *   <li>{@link Row}       – a horizontal row of cells</li>
 *   <li>{@link Cell}      – a single data cell, typed (string, numeric, boolean, formula)</li>
 *   <li>{@link CellStyle} – formatting applied to one or more cells (font, fill, borders)</li>
 *   <li>{@link Font}      – typeface settings referenced by a {@link CellStyle}</li>
 *   <li>{@link DataFormat}– number/date format string referenced by a {@link CellStyle}</li>
 * </ul>
 */
@Service
public class ExcelExportService {

    // ── Column index constants ──────────────────────────────────────────────────
    // Using named constants instead of magic numbers improves readability and
    // makes it easy to add/reorder columns in the future.
    private static final int COL_ID       = 0;
    private static final int COL_NAME     = 1;
    private static final int COL_CATEGORY = 2;
    private static final int COL_PRICE    = 3;
    private static final int COL_STOCK    = 4;
    private static final int TOTAL_COLS   = 5;

    // ── Colour constants (ARGB hex) ─────────────────────────────────────────────
    // Apache POI uses ARGB byte arrays for custom colours in XSSF.
    // FF = fully opaque; the remaining 3 bytes are the standard RGB values.
    private static final byte[] COLOUR_TITLE_BG  = hexToBytes("FF1F4E79"); // dark blue
    private static final byte[] COLOUR_HEADER_BG = hexToBytes("FF2E75B6"); // medium blue
    private static final byte[] COLOUR_ROW_ALT   = hexToBytes("FFDAE3F3"); // light blue (zebra)
    private static final byte[] COLOUR_WHITE      = hexToBytes("FFFFFFFF");
    private static final byte[] COLOUR_DARK_TEXT  = hexToBytes("FF1F2D40");
    private static final byte[] COLOUR_TOTAL_BG  = hexToBytes("FFBDD7EE"); // pale blue for totals row

    /**
     * Generates a complete Excel workbook for the given product list.
     *
     * <p>Sheet structure:
     * <ol>
     *   <li>Row 0  – merged title row (reportTitle)</li>
     *   <li>Row 1  – column header row</li>
     *   <li>Rows 2…n – data rows (one per product, alternating row colours)</li>
     *   <li>Last row – totals row showing sum of stock and average price</li>
     * </ol>
     *
     * @param reportTitle text placed in the merged title cell at the top
     * @param products    list of products to write; must not be empty
     * @return byte array containing the serialised .xlsx workbook
     * @throws IOException if the in-memory stream cannot be written
     */
    public byte[] generateProductReport(String reportTitle, List<Product> products) throws IOException {
        // XSSFWorkbook implements Closeable; using try-with-resources ensures
        // native memory held by POI is released even if an exception occurs.
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {

            // Create a sheet named "Products" inside the workbook.
            Sheet sheet = workbook.createSheet("Products");

            // Build all cell styles up front (styles are workbook-scoped resources).
            CellStyles styles = new CellStyles(workbook);

            // ── Row 0: Title row ──────────────────────────────────────────────
            createTitleRow(sheet, reportTitle, styles.titleStyle);

            // ── Row 1: Header row ─────────────────────────────────────────────
            createHeaderRow(sheet, styles.headerStyle);

            // ── Rows 2…n: Data rows ───────────────────────────────────────────
            int rowIndex = 2; // start just below the header
            for (Product product : products) {
                // Alternate background colour every other row (zebra striping)
                // to improve readability of wide tables.
                CellStyle rowStyle   = (rowIndex % 2 == 0) ? styles.dataRowStyle : styles.dataRowAltStyle;
                CellStyle priceStyle = (rowIndex % 2 == 0) ? styles.priceStyle   : styles.priceAltStyle;
                createDataRow(sheet, rowIndex, product, rowStyle, priceStyle);
                rowIndex++;
            }

            // ── Totals row ────────────────────────────────────────────────────
            createTotalsRow(sheet, rowIndex, products, styles.totalsStyle, styles.totalsPriceStyle);

            // ── Column widths ─────────────────────────────────────────────────
            // autoSizeColumn measures the content width; the multiplication by
            // 1.2 adds a 20 % padding so text is not truncated at cell edges.
            for (int col = 0; col < TOTAL_COLS; col++) {
                sheet.autoSizeColumn(col);
                // Multiply the auto-computed width by 1.2 for padding
                sheet.setColumnWidth(col, (int) (sheet.getColumnWidth(col) * 1.2));
            }

            // Freeze the first two rows (title + header) so they remain visible
            // while the user scrolls down through many data rows.
            sheet.createFreezePane(0, 2);

            // Serialise the workbook to a byte array so it can be written to
            // the HTTP response body without touching the file system.
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Creates the merged title row (row index 0) with the report title text.
     *
     * <p>A {@link CellRangeAddress} spans all columns so the title appears as a
     * single wide cell even though POI requires you to set the value on just the
     * first cell of the merged region.
     */
    private void createTitleRow(Sheet sheet, String title, CellStyle style) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(30); // taller title row

        // Create only the first cell; the merge region covers the rest.
        Cell cell = row.createCell(COL_ID);
        cell.setCellValue(title);
        cell.setCellStyle(style);

        // Merge columns 0 through TOTAL_COLS-1 in row 0.
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, TOTAL_COLS - 1));
    }

    /**
     * Creates the header row (row index 1) with column labels.
     */
    private void createHeaderRow(Sheet sheet, CellStyle style) {
        Row row = sheet.createRow(1);
        row.setHeightInPoints(20); // slightly taller for the header

        String[] headers = {"ID", "Name", "Category", "Price (USD)", "Stock"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    /**
     * Creates a single data row for the given {@link Product}.
     *
     * <p>Price is stored as a numeric cell so Excel can format and sort it
     * correctly. All other fields are stored as strings.
     */
    private void createDataRow(Sheet sheet, int rowIndex, Product product,
                               CellStyle baseStyle, CellStyle priceStyle) {
        Row row = sheet.createRow(rowIndex);

        // String cells – ID, Name, Category
        createStringCell(row, COL_ID,       product.id(),       baseStyle);
        createStringCell(row, COL_NAME,     product.name(),     baseStyle);
        createStringCell(row, COL_CATEGORY, product.category(), baseStyle);

        // Numeric cell – Price (stored as double for Excel compatibility)
        Cell priceCell = row.createCell(COL_PRICE);
        priceCell.setCellValue(product.price().doubleValue());
        priceCell.setCellStyle(priceStyle); // applies "#,##0.00" format

        // Numeric cell – Stock
        Cell stockCell = row.createCell(COL_STOCK);
        stockCell.setCellValue(product.stock());
        stockCell.setCellStyle(baseStyle);
    }

    /**
     * Creates the totals row showing total stock (SUM) and average price (AVERAGE).
     *
     * <p>Excel formulas are written as strings starting with '='. POI passes them
     * through to the file; Excel evaluates them on open. The formula references
     * the data rows using standard A1-notation column letters.
     *
     * @param rowIndex    index of the totals row (one past the last data row)
     * @param products    needed to know the data row range for formula references
     */
    private void createTotalsRow(Sheet sheet, int rowIndex, List<Product> products,
                                 CellStyle baseStyle, CellStyle priceStyle) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(20);

        // "Totals" label in the ID column
        Cell labelCell = row.createCell(COL_ID);
        labelCell.setCellValue("TOTALS");
        labelCell.setCellStyle(baseStyle);

        // Empty cells for Name and Category with the totals style applied
        createStringCell(row, COL_NAME,     "", baseStyle);
        createStringCell(row, COL_CATEGORY, "", baseStyle);

        // Excel row numbers are 1-based; data starts at row index 2 (Excel row 3)
        // and ends at rowIndex - 1 (Excel row rowIndex).
        int excelFirstDataRow = 3;           // row index 2 → Excel row 3
        int excelLastDataRow  = rowIndex;    // rowIndex - 1 + 1 (1-based)

        // AVERAGE formula for the Price column (column D in Excel)
        Cell avgPriceCell = row.createCell(COL_PRICE);
        avgPriceCell.setCellFormula(
                String.format("AVERAGE(D%d:D%d)", excelFirstDataRow, excelLastDataRow));
        avgPriceCell.setCellStyle(priceStyle);

        // SUM formula for the Stock column (column E in Excel)
        Cell sumStockCell = row.createCell(COL_STOCK);
        sumStockCell.setCellFormula(
                String.format("SUM(E%d:E%d)", excelFirstDataRow, excelLastDataRow));
        sumStockCell.setCellStyle(baseStyle);
    }

    /** Convenience method to create a string cell with a given style. */
    private void createStringCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /**
     * Converts a hex colour string (e.g. "FF1F4E79") to a 4-byte ARGB array
     * as required by {@code XSSFColor}.
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    // ── Inner class: CellStyles ─────────────────────────────────────────────────

    /**
     * Groups all {@link CellStyle} objects needed by the report.
     *
     * <p>Cell styles are workbook-scoped resources – POI limits a workbook to
     * 64 000 distinct styles. Reusing style objects (instead of creating one per
     * cell) is both the correct approach and significantly faster for large sheets.
     */
    private class CellStyles {

        final CellStyle titleStyle;
        final CellStyle headerStyle;
        final CellStyle dataRowStyle;
        final CellStyle dataRowAltStyle;
        final CellStyle priceStyle;
        final CellStyle priceAltStyle;
        final CellStyle totalsStyle;
        final CellStyle totalsPriceStyle;

        CellStyles(XSSFWorkbook wb) {
            titleStyle       = buildTitleStyle(wb);
            headerStyle      = buildHeaderStyle(wb);
            dataRowStyle     = buildDataStyle(wb, COLOUR_WHITE,   COLOUR_DARK_TEXT, false);
            dataRowAltStyle  = buildDataStyle(wb, COLOUR_ROW_ALT, COLOUR_DARK_TEXT, false);
            priceStyle       = buildPriceStyle(wb, COLOUR_WHITE);
            priceAltStyle    = buildPriceStyle(wb, COLOUR_ROW_ALT);
            totalsStyle      = buildDataStyle(wb, COLOUR_TOTAL_BG, COLOUR_DARK_TEXT, true);
            totalsPriceStyle = buildTotalsPriceStyle(wb);
        }

        /**
         * Title row style: large bold white text on a dark-blue background,
         * centred both horizontally and vertically.
         */
        private CellStyle buildTitleStyle(XSSFWorkbook wb) {
            Font font = wb.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 16);
            // XSSFFont can set colour via ARGB; cast is safe because we always
            // use XSSFWorkbook which produces XSSFFont instances.
            ((org.apache.poi.xssf.usermodel.XSSFFont) font)
                    .setColor(new org.apache.poi.xssf.usermodel.XSSFColor(COLOUR_WHITE, null));

            org.apache.poi.xssf.usermodel.XSSFCellStyle style =
                    (org.apache.poi.xssf.usermodel.XSSFCellStyle) wb.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(
                    new org.apache.poi.xssf.usermodel.XSSFColor(COLOUR_TITLE_BG, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        /**
         * Header row style: bold white text on medium-blue background, centred.
         */
        private CellStyle buildHeaderStyle(XSSFWorkbook wb) {
            Font font = wb.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 11);
            ((org.apache.poi.xssf.usermodel.XSSFFont) font)
                    .setColor(new org.apache.poi.xssf.usermodel.XSSFColor(COLOUR_WHITE, null));

            org.apache.poi.xssf.usermodel.XSSFCellStyle style =
                    (org.apache.poi.xssf.usermodel.XSSFCellStyle) wb.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(
                    new org.apache.poi.xssf.usermodel.XSSFColor(COLOUR_HEADER_BG, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            // Thin border on all sides for the header cells
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        /**
         * Standard data-row style with configurable background colour.
         *
         * @param bgColour   ARGB bytes for the background fill
         * @param textColour ARGB bytes for the font colour
         * @param bold       whether the font should be bold (used for totals row)
         */
        private CellStyle buildDataStyle(XSSFWorkbook wb, byte[] bgColour,
                                         byte[] textColour, boolean bold) {
            Font font = wb.createFont();
            font.setBold(bold);
            font.setFontHeightInPoints((short) 11);
            ((org.apache.poi.xssf.usermodel.XSSFFont) font)
                    .setColor(new org.apache.poi.xssf.usermodel.XSSFColor(textColour, null));

            org.apache.poi.xssf.usermodel.XSSFCellStyle style =
                    (org.apache.poi.xssf.usermodel.XSSFCellStyle) wb.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(
                    new org.apache.poi.xssf.usermodel.XSSFColor(bgColour, null));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            // Light border on all sides
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            return style;
        }

        /**
         * Price cell style: same as a data row but with a currency number format
         * ("#,##0.00") so Excel displays the value with two decimal places and
         * thousands separators.
         *
         * @param bgColour ARGB bytes for the background fill (plain or alternate row)
         */
        private CellStyle buildPriceStyle(XSSFWorkbook wb, byte[] bgColour) {
            // DataFormat is a workbook-level registry of number format strings.
            DataFormat dataFormat = wb.createDataFormat();
            // "#,##0.00" → e.g. 1 234.56
            short fmtIndex = dataFormat.getFormat("#,##0.00");

            CellStyle base = buildDataStyle(wb, bgColour, COLOUR_DARK_TEXT, false);
            base.setDataFormat(fmtIndex);
            base.setAlignment(HorizontalAlignment.RIGHT); // numbers align right by convention
            return base;
        }

        /** Totals row price style: bold + currency format on the pale-blue background. */
        private CellStyle buildTotalsPriceStyle(XSSFWorkbook wb) {
            DataFormat dataFormat = wb.createDataFormat();
            short fmtIndex = dataFormat.getFormat("#,##0.00");

            CellStyle base = buildDataStyle(wb, COLOUR_TOTAL_BG, COLOUR_DARK_TEXT, true);
            base.setDataFormat(fmtIndex);
            base.setAlignment(HorizontalAlignment.RIGHT);
            return base;
        }
    }
}
