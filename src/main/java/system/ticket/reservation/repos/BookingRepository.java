package system.ticket.reservation.repos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import system.ticket.reservation.entity.Booking;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // All bookings for a user
    List<Booking> findByUserId(Long userId);

    // All bookings for a flight
    List<Booking> findByFlightId(Long flightId);

    // Find by stripe payment id
    Optional<Booking> findByStripePaymentIntentId(String stripePaymentIntentId);




    // Find confirmed bookings for a user
    @Query("SELECT b FROM Booking b WHERE " +
            "b.user.id = :userId AND " +
            "b.status = 'CONFIRMED' " +
            "ORDER BY b.createdAt DESC")
    List<Booking> findConfirmedBookingsByUser(@Param("userId") Long userId);

    // Check if seat already booked
    @Query("SELECT COUNT(b) > 0 FROM Booking b WHERE " +
            "b.seat.id = :seatId AND " +
            "b.status != 'CANCELLED'")
    boolean isSeatAlreadyBooked(@Param("seatId") Long seatId);
}