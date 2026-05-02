package system.ticket.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponseDto {

    private Long       id;
    private String     flightNumber;
    private String     airline;
    private String     origin;
    private String     destination;
    private String     departureTime;       // formatted string for JS display
    private String     passengerName;
    private String     passengerEmail;
    private String     passengerPhone;
    private String     seatNumber;
    private BigDecimal totalPrice;
    private String     status;
    private String     stripePaymentIntentId;
    private String     qrCodeBase64;        // "data:image/png;base64,..." for <img src>
}