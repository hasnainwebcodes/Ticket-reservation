package system.ticket.reservation.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.Collections;
import org.springframework.stereotype.Service;
import system.ticket.reservation.dto.FlightSearchDto;
import system.ticket.reservation.entity.Flight;
import system.ticket.reservation.repos.FlightRepository;
import system.ticket.reservation.repos.SeatRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlightService {

    private final FlightRepository flightRepository;
    private final SeatRepository   seatRepository;
    private final DuffelService    duffelService;

    /**
     * Search strategy:
     * 1. Check our DB for flights on this route + date
     * 2. If empty → call Duffel, persist results
     * 3. Re-query DB (now has Duffel data), return DTOs
     *
     * Duffel is only called once per route+date combo.
     * Subsequent searches hit the DB instantly (cached).
     */
    public List<FlightSearchDto> searchFlights(
            String origin, String destination, LocalDate date) {

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to   = date.atTime(23, 59, 59);

        List<Flight> flights = flightRepository.searchFlights(
                origin, destination, from, to);

        if (flights.isEmpty()) {
            log.info("No cached results. Calling Duffel for {} → {} on {}",
                    origin, destination, date);
            duffelService.searchAndPersist(origin, destination, date);
            flights = flightRepository.searchFlights(origin, destination, from, to);

            if (flights.isEmpty()) {
                log.info("Duffel returned nothing. Seeding demo flights.");
                duffelService.seedDemoFlights(origin, destination, date);
                flights = flightRepository.searchFlights(origin, destination, from, to);
            }
        } else {
            log.info("Returning {} cached flights for {} → {}",
                    flights.size(), origin, destination);
        }

        return toDtoList(flights);  // ← single batch query
    }

    /**
     * Maps all flights to DTOs using ONE seat count query for all flights.
     * Replaces the per-flight toDto() call that caused N+1.
     */
    private List<FlightSearchDto> toDtoList(List<Flight> flights) {
        if (flights.isEmpty()) return Collections.emptyList();

        // One query for all seat counts
        List<Long> flightIds = flights.stream()
                .map(Flight::getId)
                .collect(Collectors.toList());

        Map<Long, Integer> seatCountMap = seatRepository
                .countAvailableSeatsBatch(flightIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long)    row[0],   // flight_id
                        row -> ((Long)   row[1]).intValue()  // count
                ));

        return flights.stream().map(f -> FlightSearchDto.builder()
                .id(f.getId())
                .flightNumber(f.getFlightNumber())
                .airline(f.getAirline())
                .origin(f.getOrigin())
                .destination(f.getDestination())
                .departureTime(f.getDepartureTime())
                .arrivalTime(f.getArrivalTime())
                .duration(f.getDuration())
                .stops(f.getStops())
                .price(f.getPrice())
                .currency(f.getCurrency())
                .bookingClass(f.getBookingClass())
                .availableSeats(seatCountMap.getOrDefault(f.getId(), 0))
                .build()
        ).collect(Collectors.toList());
    }

    // Keep this for single-flight use (book page etc.)
    public FlightSearchDto toDto(Flight flight) {
        int available = seatRepository.countAvailableSeats(flight.getId());
        return FlightSearchDto.builder()
                .id(flight.getId())
                .flightNumber(flight.getFlightNumber())
                .airline(flight.getAirline())
                .origin(flight.getOrigin())
                .destination(flight.getDestination())
                .departureTime(flight.getDepartureTime())
                .arrivalTime(flight.getArrivalTime())
                .duration(flight.getDuration())
                .stops(flight.getStops())
                .price(flight.getPrice())
                .currency(flight.getCurrency())
                .bookingClass(flight.getBookingClass())
                .availableSeats(available)
                .build();
    }

}