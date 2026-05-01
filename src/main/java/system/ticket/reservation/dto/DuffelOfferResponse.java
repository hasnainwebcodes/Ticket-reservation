package system.ticket.reservation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Maps the Duffel POST /air/offer_requests response.
 * Only fields we use are mapped — @JsonIgnoreProperties handles the rest.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DuffelOfferResponse {

    @JsonProperty("data")
    private DuffelData data;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DuffelData {

        @JsonProperty("id")
        private String id;

        @JsonProperty("offers")
        private List<DuffelOffer> offers;
    }

    // ─── One flight offer ─────────────────────────────────────────
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DuffelOffer {

        @JsonProperty("id")
        private String id;

        @JsonProperty("total_amount")
        private String totalAmount;

        @JsonProperty("total_currency")
        private String totalCurrency;

        @JsonProperty("base_amount")
        private String baseAmount;

        @JsonProperty("tax_amount")
        private String taxAmount;

        @JsonProperty("slices")
        private List<DuffelSlice> slices;

        @JsonProperty("owner")
        private DuffelAirline owner;
    }

    // ─── One leg of the journey ───────────────────────────────────
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DuffelSlice {

        @JsonProperty("id")
        private String id;

        @JsonProperty("duration")
        private String duration;   // ISO 8601 duration: "PT1H30M"

        @JsonProperty("segments")
        private List<DuffelSegment> segments;
    }

    // ─── One flight segment ───────────────────────────────────────
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DuffelSegment {

        @JsonProperty("id")
        private String id;

        @JsonProperty("departing_at")
        private String departingAt;    // "2026-05-01T08:00:00"

        @JsonProperty("arriving_at")
        private String arrivingAt;

        @JsonProperty("origin")
        private DuffelPlace origin;

        @JsonProperty("destination")
        private DuffelPlace destination;

        @JsonProperty("marketing_carrier")
        private DuffelAirline marketingCarrier;

        @JsonProperty("marketing_carrier_flight_number")
        private String flightNumber;

        @JsonProperty("passengers")
        private List<DuffelPassengerSegment> passengers;
    }

    // ─── Airport / city ───────────────────────────────────────────
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DuffelPlace {

        @JsonProperty("iata_code")
        private String iataCode;

        @JsonProperty("name")
        private String name;

        @JsonProperty("city_name")
        private String cityName;

        @JsonProperty("type")
        private String type;  // "airport" or "city"
    }

    // ─── Airline ──────────────────────────────────────────────────
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DuffelAirline {

        @JsonProperty("iata_code")
        private String iataCode;

        @JsonProperty("name")
        private String name;
    }

    // ─── Passenger cabin info ─────────────────────────────────────
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DuffelPassengerSegment {

        @JsonProperty("cabin_class")
        private String cabinClass;  // "economy", "business", "first"

        @JsonProperty("cabin_class_marketing_name")
        private String cabinClassMarketingName;
    }
}