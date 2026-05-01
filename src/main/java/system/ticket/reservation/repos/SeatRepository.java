package system.ticket.reservation.repos;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import system.ticket.reservation.entity.Seat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    // ─── Available seats for a flight ─────────────────────────────
    @Query("SELECT s FROM Seat s WHERE " +
            "s.flight.id = :flightId AND " +
            "s.status = 'AVAILABLE'")
    List<Seat> findAvailableSeats(@Param("flightId") Long flightId);

    // ─── Pessimistic lock — used when opening book page ───────────
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE " +
            "s.id = :seatId AND " +
            "s.status = 'AVAILABLE'")
    Optional<Seat> findAvailableSeatWithLock(@Param("seatId") Long seatId);

    // ─── Pessimistic lock — used when submitting booking ──────────
    // Allows booking if seat is AVAILABLE OR held by THIS user
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE " +
            "s.id = :seatId AND " +
            "(s.status = 'AVAILABLE' OR " +
            "(s.status = 'HELD' AND s.heldByUserId = :userId))")
    Optional<Seat> findBookableSeatWithLock(
            @Param("seatId") Long seatId,
            @Param("userId") Long userId);

    // ─── Batch seat count — eliminates N+1 on results page ────────
    @Query("SELECT s.flight.id, COUNT(s) FROM Seat s " +
            "WHERE s.flight.id IN :flightIds " +
            "AND s.status = 'AVAILABLE' " +
            "GROUP BY s.flight.id")
    List<Object[]> countAvailableSeatsBatch(
            @Param("flightIds") List<Long> flightIds);

    // ─── Single flight seat count ─────────────────────────────────
    @Query("SELECT COUNT(s) FROM Seat s WHERE " +
            "s.flight.id = :flightId AND " +
            "s.status = 'AVAILABLE'")
    int countAvailableSeats(@Param("flightId") Long flightId);

    // ─── Find seat by flight and seat number ──────────────────────
    Optional<Seat> findByFlightIdAndSeatNumber(
            Long flightId, String seatNumber);

    // ─── All seats for a flight ───────────────────────────────────
    List<Seat> findByFlightId(Long flightId);

    // ─── Expired holds — used by SeatHoldScheduler ────────────────
    @Query("SELECT s FROM Seat s WHERE " +
            "s.status = 'HELD' AND " +
            "s.heldAt < :cutoff")
    List<Seat> findExpiredHeldSeats(@Param("cutoff") LocalDateTime cutoff);
}