package system.ticket.reservation.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import system.ticket.reservation.dto.ApiResponse;
import system.ticket.reservation.dto.BookingRequestDto;
import system.ticket.reservation.dto.BookingResponseDto;
import system.ticket.reservation.entity.UserPrincipal;
import system.ticket.reservation.services.BookingService;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // ─── Create Booking ───────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponseDto>> createBooking(
            @Valid @RequestBody BookingRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {

        BookingResponseDto response = bookingService
                .createBooking(request, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(response, "Booking confirmed successfully"));
    }

    // ─── Hold Seat ────────────────────────────────────────────────
    /**
     * Called when user opens the book page.
     * Marks seat as HELD for 10 minutes.
     */
    @PostMapping("/hold/{seatId}")
    public ResponseEntity<ApiResponse<String>> holdSeat(
            @PathVariable Long seatId,
            @AuthenticationPrincipal UserPrincipal principal) {

        bookingService.holdSeat(seatId, principal.getId());
        return ResponseEntity.ok(
                ApiResponse.success("Seat held for 10 minutes",
                        "Seat held successfully"));
    }

    // ─── My Bookings ──────────────────────────────────────────────
    @GetMapping("/my-bookings")
    public ResponseEntity<ApiResponse<List<BookingResponseDto>>> getMyBookings(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<BookingResponseDto> bookings = bookingService
                .getMyBookings(principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(bookings,
                        bookings.size() + " bookings found"));
    }

    // ─── Cancel Booking ───────────────────────────────────────────
    @PatchMapping("/{bookingId}/cancel")
    public ResponseEntity<ApiResponse<BookingResponseDto>> cancelBooking(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal UserPrincipal principal) {

        BookingResponseDto response = bookingService
                .cancelBooking(bookingId, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success(response, "Booking cancelled successfully"));
    }
}