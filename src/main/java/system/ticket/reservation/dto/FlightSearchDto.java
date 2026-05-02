package system.ticket.reservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSearchDto {

    private Long          id;
    private String        flightNumber;
    private String        airline;
    private String        origin;
    private String        destination;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime departureTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime arrivalTime;

    private String        duration;
    private Integer       stops;
    private BigDecimal    price;
    private String        currency;
    private String        bookingClass;
    private Integer       availableSeats;  // computed from SeatRepository, not on entity
}