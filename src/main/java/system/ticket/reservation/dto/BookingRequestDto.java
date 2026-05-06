package system.ticket.reservation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingRequestDto {

    @NotNull(message = "Flight ID is required")
    private Long flightId;

    @NotNull(message = "Seat ID is required")
    private Long seatId;

    @NotBlank(message = "Passenger name is required")
    private String passengerName;

    @NotBlank(message = "Passenger email is required")
    @Email(message = "Invalid email format")
    private String passengerEmail;

    @NotBlank(message = "Passenger phone is required")
    private String passengerPhone;
}