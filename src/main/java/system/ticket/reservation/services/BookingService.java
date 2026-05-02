package system.ticket.reservation.services;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import system.ticket.reservation.dto.BookingRequestDto;
import system.ticket.reservation.dto.BookingResponseDto;
import system.ticket.reservation.entity.*;
import system.ticket.reservation.repos.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    private final BookingRepository bookingRepository;
    private final FlightRepository  flightRepository;
    private final SeatRepository    seatRepository;
    private final UserRepository    userRepository;
    private final QrCodeService     qrCodeService;
    private final EmailService      emailService;

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    // ─── Create Booking ───────────────────────────────────────────

    /**
     * Full booking flow:
     * 1. Validate user, flight, seat
     * 2. Pessimistic lock on seat — prevent double booking
     * 3. Charge via Stripe
     * 4. Save booking as CONFIRMED
     * 5. Mark seat as BOOKED
     * 6. Generate QR code
     * 7. Send confirmation email (async)
     */
    @Transactional
    public BookingResponseDto createBooking(
            BookingRequestDto request, Long userId) {

        // ── 1. Load entities ──────────────────────────────────────
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Flight flight = flightRepository.findById(request.getFlightId())
                .orElseThrow(() -> new RuntimeException("Flight not found"));

        // ── 2. Pessimistic lock — only one thread can book this seat
        Seat seat = seatRepository
                .findBookableSeatWithLock(request.getSeatId(), userId)
                .orElseThrow(() -> new RuntimeException(
                        "Seat is no longer available. Please select another seat."));

        // ── 3. Stripe Payment ─────────────────────────────────────
        String paymentIntentId = processStripePayment(
                flight.getPrice(), flight.getFlightNumber(),
                request.getPassengerName());

        // ── 4. Create booking record ──────────────────────────────
        Booking booking = Booking.builder()
                .user(user)
                .flight(flight)
                .seat(seat)
                .passengerName(request.getPassengerName())
                .passengerEmail(request.getPassengerEmail())
                .passengerPhone(request.getPassengerPhone())
                .totalPrice(flight.getPrice())
                .status(Booking.BookingStatus.CONFIRMED)
                .stripePaymentIntentId(paymentIntentId)
                .build();

        booking = bookingRepository.save(booking);

        // ── 5. Mark seat as BOOKED ────────────────────────────────
        seat.setStatus(Seat.SeatStatus.BOOKED);
        seat.setHeldAt(null);
        seat.setHeldByUserId(null);
        seatRepository.save(seat);

        // ── 6. Generate QR code ───────────────────────────────────
        byte[] qrBytes = qrCodeService.generateQrCode(
                booking.getId(),
                flight.getFlightNumber(),
                booking.getPassengerName(),
                seat.getSeatNumber());

        booking.setQrCode(qrBytes);
        booking = bookingRepository.save(booking);

        // ── 7. Send confirmation email (non-blocking) ─────────────
        emailService.sendBookingConfirmation(booking, qrBytes);

        log.info("Booking #{} confirmed for {} on flight {}",
                booking.getId(), booking.getPassengerName(),
                flight.getFlightNumber());

        return toResponseDto(booking, qrBytes);
    }

    // ─── Hold Seat ────────────────────────────────────────────────

    /**
     * Called when user opens the book page.
     * Marks seat as HELD with current timestamp.
     * Scheduler releases it after 10 minutes if payment not completed.
     */
    @Transactional
    public void holdSeat(Long seatId, Long userId) {
        seatRepository.findAvailableSeatWithLock(seatId)
                .ifPresent(seat -> {
                    seat.setStatus(Seat.SeatStatus.HELD);
                    seat.setHeldAt(LocalDateTime.now());
                    seat.setHeldByUserId(userId);        // ← store who held it
                    seatRepository.save(seat);
                    log.info("Seat {} held by user {}", seatId, userId);
                });
    }

    // ─── Get My Bookings ──────────────────────────────────────────

    public List<BookingResponseDto> getMyBookings(Long userId) {
        return bookingRepository.findByUserId(userId)
                .stream()
                .map(b -> toResponseDto(b, b.getQrCode()))
                .toList();
    }

    // ─── Cancel Booking ───────────────────────────────────────────

    @Transactional
    public BookingResponseDto cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        // Security: ensure this user owns the booking
        if (!booking.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to cancel this booking");
        }

        // Cannot cancel an already cancelled/refunded booking
        if (booking.getStatus() == Booking.BookingStatus.CANCELLED ||
                booking.getStatus() == Booking.BookingStatus.REFUNDED) {
            throw new RuntimeException("Booking is already " +
                    booking.getStatus().name().toLowerCase());
        }

        // If confirmed — attempt Stripe refund
        if (booking.getStatus() == Booking.BookingStatus.CONFIRMED) {
            processStripeRefund(booking.getStripePaymentIntentId());
            booking.setStatus(Booking.BookingStatus.REFUNDED);
        } else {
            // PENDING — just cancel
            booking.setStatus(Booking.BookingStatus.CANCELLED);
        }

        // Release seat back to AVAILABLE — clear all hold data
        Seat seat = booking.getSeat();
        seat.setStatus(Seat.SeatStatus.AVAILABLE);
        seat.setHeldAt(null);
        seat.setHeldByUserId(null);
        seatRepository.save(seat);

        booking = bookingRepository.save(booking);
        log.info("Booking #{} {} by user {}",
                bookingId, booking.getStatus(), userId);

        return toResponseDto(booking, booking.getQrCode());
    }

    // ─── Private: Stripe ──────────────────────────────────────────

    private String processStripePayment(
            java.math.BigDecimal amount,
            String flightNumber,
            String passengerName) {

        Stripe.apiKey = stripeSecretKey;
        try {
            long amountLong = amount.longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountLong)
                    .setCurrency("pkr")
                    .setDescription("Flight " + flightNumber +
                            " — Passenger: " + passengerName)
                    .setConfirm(true)
                    .setPaymentMethod("pm_card_visa")
                    // ← Fix: disable redirect-based payment methods
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(
                                            PaymentIntentCreateParams
                                                    .AutomaticPaymentMethods
                                                    .AllowRedirects.NEVER)
                                    .build()
                    )
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            log.info("Stripe PaymentIntent created: {}", intent.getId());
            return intent.getId();

        } catch (StripeException e) {
            log.error("Stripe payment failed: {}", e.getMessage());
            throw new RuntimeException("Payment failed: " + e.getMessage());
        }
    }

    private void processStripeRefund(String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) return;

        Stripe.apiKey = stripeSecretKey;
        try {
            com.stripe.model.Refund.create(
                    com.stripe.param.RefundCreateParams.builder()
                            .setPaymentIntent(paymentIntentId)
                            .build()
            );
            log.info("Stripe refund processed for: {}", paymentIntentId);
        } catch (StripeException e) {
            // Log but don't fail — booking is already cancelled
            log.error("Stripe refund failed for {}: {}", paymentIntentId, e.getMessage());
        }
    }

    // ─── Private: Entity → DTO ────────────────────────────────────

    private BookingResponseDto toResponseDto(Booking booking, byte[] qrBytes) {
        String qrBase64 = qrCodeService.toBase64DataUri(qrBytes);

        String departure = booking.getFlight().getDepartureTime() != null
                ? booking.getFlight().getDepartureTime().format(DISPLAY_FMT)
                : "—";

        return BookingResponseDto.builder()
                .id(booking.getId())
                .flightNumber(booking.getFlight().getFlightNumber())
                .airline(booking.getFlight().getAirline())
                .origin(booking.getFlight().getOrigin())
                .destination(booking.getFlight().getDestination())
                .departureTime(departure)
                .passengerName(booking.getPassengerName())
                .passengerEmail(booking.getPassengerEmail())
                .passengerPhone(booking.getPassengerPhone())
                .seatNumber(booking.getSeat().getSeatNumber())
                .totalPrice(booking.getTotalPrice())
                .status(booking.getStatus().name())
                .stripePaymentIntentId(booking.getStripePaymentIntentId())
                .qrCodeBase64(qrBase64)
                .build();
    }
}