package system.ticket.reservation.services;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Service
@Slf4j
public class QrCodeService {

    private static final int QR_WIDTH  = 300;
    private static final int QR_HEIGHT = 300;

    /**
     * Generates a QR code PNG from booking details.
     *
     * @return byte[] of the PNG image (stored in DB)
     */
    public byte[] generateQrCode(Long bookingId, String flightNumber,
                                 String passengerName, String seatNumber) {
        String content = buildQrContent(bookingId, flightNumber,
                passengerName, seatNumber);
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.MARGIN, 1
            );
            BitMatrix matrix = writer.encode(
                    content, BarcodeFormat.QR_CODE,
                    QR_WIDTH, QR_HEIGHT, hints);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();

        } catch (WriterException | IOException e) {
            log.error("QR code generation failed for booking {}: {}",
                    bookingId, e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Converts byte[] QR PNG → base64 data URI for use in <img src="...">
     */
    public String toBase64DataUri(byte[] qrBytes) {
        if (qrBytes == null || qrBytes.length == 0) return null;
        return "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(qrBytes);
    }

    // ─── Private ──────────────────────────────────────────────────

    private String buildQrContent(Long bookingId, String flightNumber,
                                  String passengerName, String seatNumber) {
        // Human-readable format that airport scanners can display
        return String.format(
                "TICKETFLOW BOARDING PASS\n" +
                        "Booking: #%d\n" +
                        "Flight: %s\n" +
                        "Passenger: %s\n" +
                        "Seat: %s",
                bookingId, flightNumber, passengerName, seatNumber
        );
    }
}