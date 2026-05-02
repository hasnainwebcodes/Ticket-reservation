package system.ticket.reservation.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import system.ticket.reservation.dto.FlightSearchDto;
import system.ticket.reservation.entity.Flight;
import system.ticket.reservation.entity.UserPrincipal;
import system.ticket.reservation.repos.FlightRepository;
import system.ticket.reservation.repos.SeatRepository;
import system.ticket.reservation.services.BookingService;
import system.ticket.reservation.services.FlightService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PageController {

    private final FlightService    flightService;
    private final FlightRepository flightRepository;
    private final SeatRepository   seatRepository;
    private final BookingService   bookingService;

    // ─── Home ─────────────────────────────────────────────────────
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("popularRoutes", List.of(
                Map.of("origin","Karachi","destination","Lahore",
                        "duration","1h 30m","price","8,500"),
                Map.of("origin","Karachi","destination","Dubai",
                        "duration","3h 10m","price","45,000"),
                Map.of("origin","Lahore","destination","Islamabad",
                        "duration","1h 00m","price","7,200")
        ));
        model.addAttribute("tomorrow",
                LocalDate.now().plusDays(1).toString());
        return "index";
    }

    // ─── Auth pages ───────────────────────────────────────────────
    @GetMapping("/login")
    public String login() { return "login"; }

    @GetMapping("/register")
    public String register() { return "register"; }

    // ─── Search page ──────────────────────────────────────────────
    @GetMapping("/search-flights")
    public String searchFlights() { return "search"; }

    // ─── Results page ─────────────────────────────────────────────
    @GetMapping("/flights/results")
    public String flightResults(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {

        log.info("Results page: {} → {} on {}", origin, destination, date);

        List<FlightSearchDto> flights = flightService.searchFlights(
                origin, destination, date);

        model.addAttribute("flights",     flights);
        model.addAttribute("origin",      origin);
        model.addAttribute("destination", destination);
        model.addAttribute("date",        date.toString());
        return "results";
    }

    // ─── Book page ────────────────────────────────────────────────
    @GetMapping("/book/{flightNumber}")
    public String bookPage(
            @PathVariable String flightNumber,
            @RequestParam BigDecimal price,
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam String airline,
            @RequestParam String departure,
            Model model) {

        // Load flight
        Flight flight = flightRepository.findByFlightNumber(flightNumber)
                .orElseThrow(() -> new RuntimeException(
                        "Flight not found: " + flightNumber));

        // Get first available seat
        Long seatId = seatRepository.findAvailableSeats(flight.getId())
                .stream()
                .findFirst()
                .map(seat -> seat.getId())
                .orElseThrow(() -> new RuntimeException(
                        "No seats available for flight " + flightNumber));

        // ── Hold seat — starts 10-minute clock ────────────────────
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof UserPrincipal principal) {
                bookingService.holdSeat(seatId, principal.getId());
                log.info("Seat {} held for user {}", seatId, principal.getId());
            }
        } catch (Exception e) {
            // Never fail the page load because of hold failure
            log.warn("Could not hold seat {}: {}", seatId, e.getMessage());
        }

        // ── Parse departure time ──────────────────────────────────
        LocalDateTime departureTime;
        try {
            departureTime = LocalDateTime.parse(
                    departure, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            departureTime = flight.getDepartureTime();
        }

        model.addAttribute("flightId",     flight.getId());
        model.addAttribute("flightNumber", flightNumber);
        model.addAttribute("seatId",       seatId);
        model.addAttribute("price",        price);
        model.addAttribute("origin",       origin);
        model.addAttribute("destination",  destination);
        model.addAttribute("airline",      airline);
        model.addAttribute("departure",    departureTime);
        return "book";
    }

    // ─── Confirmation page ────────────────────────────────────────
    @GetMapping("/booking/confirmation")
    public String confirmation() { return "confirmation"; }

    // ─── My Bookings page ─────────────────────────────────────────
    @GetMapping("/my-bookings")
    public String myBookings() { return "my-bookings"; }
}