package com.example.barcodegenerator.service;

import com.example.barcodegenerator.domain.BarcodeRequest;
import com.example.barcodegenerator.exception.BarcodeGenerationException;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Core service responsible for generating barcode and QR code images.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>The caller passes a {@link BarcodeRequest} containing the data to
 *       encode, the desired format, and the image dimensions.</li>
 *   <li>This service maps the application's {@link com.example.barcodegenerator.domain.BarcodeFormat}
 *       enum to the ZXing {@link BarcodeFormat} enum.</li>
 *   <li>ZXing's {@link MultiFormatWriter} encodes the content into a
 *       {@link BitMatrix} – a 2-D grid of black/white cells.</li>
 *   <li>{@link MatrixToImageWriter} renders that matrix to a PNG byte array
 *       which is returned to the caller for streaming back to the HTTP client.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * {@link MultiFormatWriter} is stateless; a single instance is safe to share
 * across threads. This class is a Spring singleton, so the writer is created
 * once at construction time.
 */
@Service
public class BarcodeService {

    /**
     * ZXing writer that supports all barcode formats (1-D and 2-D).
     * Stateless and thread-safe.
     */
    private final MultiFormatWriter multiFormatWriter;

    /**
     * Image format written to the output stream.
     * PNG is lossless and universally supported by browsers and scanners.
     */
    private static final String IMAGE_FORMAT = "PNG";

    /**
     * Constructs the service and initialises the ZXing writer.
     * Spring calls this constructor via dependency injection.
     */
    public BarcodeService() {
        this.multiFormatWriter = new MultiFormatWriter();
    }

    /**
     * Generates a barcode or QR code image and returns it as a PNG byte array.
     *
     * @param request the parameters describing the barcode to generate
     * @return PNG-encoded image bytes ready to be written to an HTTP response
     * @throws BarcodeGenerationException if ZXing cannot encode the given
     *         content in the chosen format (e.g. EAN-13 with non-numeric content)
     */
    public byte[] generate(BarcodeRequest request) {
        // Map the application-level format to the ZXing format constant.
        com.google.zxing.BarcodeFormat zxingFormat = toZxingFormat(request.format());

        // Build ZXing encoding hints (character set, QR error correction level, etc.)
        Map<EncodeHintType, Object> hints = buildHints(request.format());

        try {
            // Encode the content into a bit matrix (grid of black/white modules).
            // For 1-D barcodes the height parameter is the bar height in modules;
            // for 2-D barcodes (QR, PDF417) both width and height define the matrix size.
            BitMatrix bitMatrix = multiFormatWriter.encode(
                    request.content(),
                    zxingFormat,
                    request.width(),
                    request.height(),
                    hints
            );

            // Render the bit matrix as a PNG image and write it to a byte buffer.
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, IMAGE_FORMAT, outputStream);
            return outputStream.toByteArray();

        } catch (WriterException e) {
            // ZXing throws WriterException for content that cannot be encoded in the
            // chosen format (e.g. letters in an EAN-13 barcode, or content too long).
            throw new BarcodeGenerationException(
                    "Failed to encode content for format " + request.format() + ": " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            // ZXing also throws IllegalArgumentException for some format violations,
            // such as EAN-13/UPC-A content with wrong digit count or non-numeric characters.
            throw new BarcodeGenerationException(
                    "Failed to encode content for format " + request.format() + ": " + e.getMessage(), e);
        } catch (IOException e) {
            // ByteArrayOutputStream never throws IOException, but the API declares it.
            throw new BarcodeGenerationException("Failed to write barcode image to stream", e);
        }
    }

    /**
     * Maps the application's {@link com.example.barcodegenerator.domain.BarcodeFormat}
     * to the corresponding ZXing {@link com.google.zxing.BarcodeFormat}.
     *
     * @param format the application format enum constant
     * @return the equivalent ZXing format constant
     */
    private com.google.zxing.BarcodeFormat toZxingFormat(com.example.barcodegenerator.domain.BarcodeFormat format) {
        return switch (format) {
            case QR_CODE  -> com.google.zxing.BarcodeFormat.QR_CODE;
            case CODE_128 -> com.google.zxing.BarcodeFormat.CODE_128;
            case EAN_13   -> com.google.zxing.BarcodeFormat.EAN_13;
            case UPC_A    -> com.google.zxing.BarcodeFormat.UPC_A;
            case CODE_39  -> com.google.zxing.BarcodeFormat.CODE_39;
            case PDF_417  -> com.google.zxing.BarcodeFormat.PDF_417;
        };
    }

    /**
     * Builds a map of ZXing encoding hints tailored to each format.
     *
     * <ul>
     *   <li><strong>UTF-8 character set</strong> – ensures accented characters
     *       and symbols encode correctly in QR codes and Code 128.</li>
     *   <li><strong>Error correction level M</strong> (QR only) – 15 % of the
     *       codeword data can be restored if the code is damaged, a good balance
     *       between data capacity and fault tolerance.</li>
     * </ul>
     *
     * @param format the application format (used to add format-specific hints)
     * @return an {@link EnumMap} of hints, possibly empty for simple formats
     */
    private Map<EncodeHintType, Object> buildHints(com.example.barcodegenerator.domain.BarcodeFormat format) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);

        // Always set the character encoding to avoid platform-default issues.
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        // QR codes support configurable error correction.
        // Level M (medium) restores up to 15 % of lost data – ideal for printed labels.
        if (format == com.example.barcodegenerator.domain.BarcodeFormat.QR_CODE) {
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            // Remove the white border (quiet zone) so the image fills the requested size.
            hints.put(EncodeHintType.MARGIN, 1);
        }

        return hints;
    }
}
