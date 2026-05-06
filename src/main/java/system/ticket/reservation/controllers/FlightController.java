package system.ticket.reservation.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import system.ticket.reservation.dto.ApiResponse;
import system.ticket.reservation.dto.FlightSearchDto;
import system.ticket.reservation.services.FlightService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    /**
     * GET /api/flights/search?origin=Karachi&destination=Dubai&date=2026-05-01
     * Used by the search form and can be tested via Swagger.
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<FlightSearchDto>>> search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<FlightSearchDto> flights = flightService.searchFlights(
                origin, destination, date);

        if (flights.isEmpty()) {
            return ResponseEntity.ok(
                    ApiResponse.success(flights,
                            "No flights found for this route and date"));
        }

        return ResponseEntity.ok(
                ApiResponse.success(flights,
                        flights.size() + " flights found"));
    }
}