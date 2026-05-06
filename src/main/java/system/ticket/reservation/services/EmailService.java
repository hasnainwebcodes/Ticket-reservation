package system.ticket.reservation.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import system.ticket.reservation.entity.Booking;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /**
     * Sends booking confirmation email with QR code embedded.
     * @Async so it doesn't block the booking response.
     */
    @Async
    public void sendBookingConfirmation(Booking booking, byte[] qrCodeBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(booking.getPassengerEmail());
            helper.setSubject("✈ Booking Confirmed — " +
                    booking.getFlight().getFlightNumber() +
                    " | TicketFlow #" + booking.getId());

            helper.setText(buildEmailHtml(booking), true); // true = HTML

            // Embed QR code as inline image
            if (qrCodeBytes != null && qrCodeBytes.length > 0) {
                helper.addInline("qrCode",
                        new org.springframework.core.io.ByteArrayResource(qrCodeBytes),
                        "image/png");
            }

            mailSender.send(message);
            log.info("Confirmation email sent to: {}", booking.getPassengerEmail());

        } catch (MessagingException e) {
            // Email failure must never fail the booking itself
            log.error("Failed to send confirmation email for booking {}: {}",
                    booking.getId(), e.getMessage());
        }
    }

    // ─── Private: HTML email template ─────────────────────────────

    private String buildEmailHtml(Booking booking) {
        String departure = booking.getFlight().getDepartureTime() != null
                ? booking.getFlight().getDepartureTime().format(DISPLAY_FORMAT)
                : "—";

        String arrival = booking.getFlight().getArrivalTime() != null
                ? booking.getFlight().getArrivalTime().format(DISPLAY_FORMAT)
                : "—";

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8"/>
                    <style>
                        body { font-family: 'Segoe UI', sans-serif; background:#f8fafc; margin:0; padding:0; }
                        .container { max-width:600px; margin:40px auto; background:white;
                                     border-radius:16px; overflow:hidden;
                                     box-shadow:0 4px 20px rgba(0,0,0,0.08); }
                        .header { background:linear-gradient(135deg,#1e293b,#1a73e8);
                                  color:white; padding:32px; text-align:center; }
                        .header h1 { margin:0; font-size:1.8rem; }
                        .header p  { margin:8px 0 0; opacity:0.85; }
                        .body { padding:32px; }
                        .badge { display:inline-block; background:#dcfce7; color:#16a34a;
                                 border-radius:20px; padding:6px 16px;
                                 font-weight:600; font-size:0.9rem; margin-bottom:24px; }
                        .info-grid { display:grid; grid-template-columns:1fr 1fr;
                                     gap:16px; margin-bottom:24px; }
                        .info-item label { display:block; color:#64748b;
                                           font-size:0.8rem; margin-bottom:4px; }
                        .info-item span  { font-weight:600; color:#1e293b; }
                        .qr-section { text-align:center; padding:24px;
                                      background:#f8fafc; border-radius:12px;
                                      margin-bottom:24px; }
                        .qr-section p { color:#64748b; font-size:0.9rem; margin-bottom:12px; }
                        .footer { background:#f1f5f9; padding:20px 32px;
                                  text-align:center; color:#64748b; font-size:0.85rem; }
                        .divider { border:none; border-top:1px solid #e2e8f0; margin:24px 0; }
                    </style>
                </head>
                <body>
                <div class="container">
                    <div class="header">
                        <h1>✈ TicketFlow</h1>
                        <p>Your booking is confirmed!</p>
                    </div>
                    <div class="body">
                        <div class="badge">✓ Booking Confirmed</div>
                        <div class="info-grid">
                            <div class="info-item">
                                <label>Booking ID</label>
                                <span>#%d</span>
                            </div>
                            <div class="info-item">
                                <label>Flight</label>
                                <span>%s</span>
                            </div>
                            <div class="info-item">
                                <label>Passenger</label>
                                <span>%s</span>
                            </div>
                            <div class="info-item">
                                <label>Seat</label>
                                <span>%s</span>
                            </div>
                            <div class="info-item">
                                <label>Route</label>
                                <span>%s → %s</span>
                            </div>
                            <div class="info-item">
                                <label>Departure</label>
                                <span>%s</span>
                            </div>
                            <div class="info-item">
                                <label>Arrival</label>
                                <span>%s</span>
                            </div>
                            <div class="info-item">
                                <label>Amount Paid</label>
                                <span>PKR %s</span>
                            </div>
                        </div>
                        <hr class="divider"/>
                        <div class="qr-section">
                            <p>Your QR Boarding Pass — present at the gate</p>
                            <img src="cid:qrCode" alt="QR Boarding Pass"
                                 style="width:200px;height:200px;"/>
                        </div>
                    </div>
                    <div class="footer">
                        <p>Thank you for choosing TicketFlow</p>
                        <p>Karachi, Pakistan • support@ticketflow.pk</p>
                    </div>
                </div>
                </body>
                </html>
                """.formatted(
                booking.getId(),
                booking.getFlight().getFlightNumber(),
                booking.getPassengerName(),
                booking.getSeat().getSeatNumber(),
                booking.getFlight().getOrigin(),
                booking.getFlight().getDestination(),
                departure,
                arrival,
                booking.getTotalPrice().toPlainString()
        );
    }
}