package system.ticket.reservation.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import system.ticket.reservation.entity.Flight;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    Optional<Flight> findByFlightNumber(String flightNumber);

    // Search by origin and destination (case insensitive)
    @Query("SELECT f FROM Flight f WHERE " +
            "LOWER(f.origin) = LOWER(:origin) AND " +
            "LOWER(f.destination) = LOWER(:destination) AND " +
            "f.departureTime >= :from AND " +
            "f.departureTime <= :to")
    List<Flight> searchFlights(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    boolean existsByFlightNumber(String flightNumber);
}