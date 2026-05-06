package system.ticket.reservation.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import system.ticket.reservation.entity.Seat;
import system.ticket.reservation.repos.SeatRepository;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeatHoldScheduler {

    private final SeatRepository seatRepository;

    private static final int HOLD_MINUTES = 10;

    /**
     * Runs every 60 seconds.
     * Finds all HELD seats where heldAt is older than 10 minutes
     * and reverts them to AVAILABLE — clearing all hold data.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseExpiredHolds() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(HOLD_MINUTES);

        List<Seat> expiredSeats = seatRepository.findExpiredHeldSeats(cutoff);

        if (expiredSeats.isEmpty()) return;

        expiredSeats.forEach(seat -> {
            seat.setStatus(Seat.SeatStatus.AVAILABLE);
            seat.setHeldAt(null);
            seat.setHeldByUserId(null);   // ← clear user association
        });

        seatRepository.saveAll(expiredSeats);
        log.info("Released {} expired seat holds", expiredSeats.size());
    }
}