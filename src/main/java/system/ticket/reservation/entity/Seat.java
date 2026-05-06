package system.ticket.reservation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"flight_id", "seat_number"}  // ← no duplicate seats per flight
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "flight")              // ← prevents StackOverflow
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(nullable = false)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Column
    private LocalDateTime heldAt;
    
    @Column
    private Long heldByUserId;
    @PrePersist
    public void prePersist() {
        if (this.status == null) this.status = SeatStatus.AVAILABLE;
    }

    public enum SeatStatus {
        AVAILABLE, HELD, BOOKED
    }
}