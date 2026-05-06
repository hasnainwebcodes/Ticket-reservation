package system.ticket.reservation.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import system.ticket.reservation.dto.DuffelOfferResponse;
import system.ticket.reservation.dto.DuffelOfferResponse.*;
import system.ticket.reservation.entity.Flight;
import system.ticket.reservation.entity.Seat;
import system.ticket.reservation.repos.FlightRepository;
import system.ticket.reservation.repos.SeatRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DuffelService {

    @Value("${duffel.api.url}")
    private String apiUrl;

    @Value("${duffel.api.token}")
    private String apiToken;

    @Value("${duffel.api.version}")
    private String apiVersion;

    // 1 USD → PKR conversion rate (update periodically)
    private static final BigDecimal USD_TO_PKR = new BigDecimal("278.50");

    private static final int SEATS_PER_FLIGHT = 60 + new Random().nextInt(161);;

    private final RestTemplate    restTemplate;
    private final FlightRepository flightRepository;
    private final SeatRepository   seatRepository;

    // City name → IATA code
    private static final Map<String, String> CITY_TO_IATA = Map.ofEntries(
            Map.entry("karachi",   "KHI"),
            Map.entry("lahore",    "LHE"),
            Map.entry("islamabad", "ISB"),
            Map.entry("peshawar",  "PEW"),
            Map.entry("quetta",    "UET"),
            Map.entry("multan",    "MUX"),
            Map.entry("faisalabad","LYP"),
            Map.entry("dubai",     "DXB"),
            Map.entry("london",    "LHR"),
            Map.entry("new york",  "JFK"),
            Map.entry("paris",     "CDG"),
            Map.entry("singapore", "SIN"),
            Map.entry("bangkok",   "BKK"),
            Map.entry("istanbul",  "IST")
    );

    // ─── Public API ───────────────────────────────────────────────

    /**
     * Search Duffel for flights, persist new ones, return saved entities.
     */
    @Transactional
    public List<Flight> searchAndPersist(
            String originCity, String destinationCity, LocalDate date) {

        String depIata = resolveIata(originCity);
        String arrIata = resolveIata(destinationCity);

        if (depIata == null || arrIata == null) {
            log.warn("Unknown city — dep: {}, arr: {}", originCity, destinationCity);
            return Collections.emptyList();
        }

        DuffelOfferResponse response = callDuffelApi(depIata, arrIata, date);
        if (response == null || response.getData() == null
                || response.getData().getOffers() == null) {
            log.warn("Duffel returned empty response");
            return Collections.emptyList();
        }

        List<Flight> result = new ArrayList<>();

        for (DuffelOffer offer : response.getData().getOffers()) {
            try {
                Flight flight = mapOfferToFlight(offer, originCity, destinationCity);
                if (flight == null) continue;

                // Skip duplicates
                if (flightRepository.existsByFlightNumber(flight.getFlightNumber())) {
                    flightRepository.findByFlightNumber(flight.getFlightNumber())
                            .ifPresent(result::add);
                    continue;
                }

                flight = flightRepository.save(flight);
                generateSeats(flight);
                result.add(flight);
                log.info("Saved Duffel flight: {}", flight.getFlightNumber());

            } catch (Exception e) {
                log.warn("Skipping offer due to error: {}", e.getMessage());
            }
        }

        log.info("Duffel search done: {} flights saved for {} → {}",
                result.size(), originCity, destinationCity);
        return result;
    }

    // ─── Private: Duffel HTTP call ────────────────────────────────

    private DuffelOfferResponse callDuffelApi(
            String depIata, String arrIata, LocalDate date) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiToken);
        headers.set("Duffel-Version", apiVersion);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        // Duffel request body
        Map<String, Object> passenger = Map.of("type", "adult");

        Map<String, Object> slice = Map.of(
                "origin",         depIata,
                "destination",    arrIata,
                "departure_date", date.toString()   // "2026-05-01"
        );

        Map<String, Object> requestData = Map.of(
                "slices",           List.of(slice),
                "passengers",       List.of(passenger),
                "cabin_class",      "economy"
        );

        Map<String, Object> body = Map.of("data", requestData);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        String endpoint = apiUrl + "/air/offer_requests?return_offers=true";

        try {
            ResponseEntity<DuffelOfferResponse> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    entity,
                    DuffelOfferResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Duffel API error: {}", e.getMessage());
            return null;
        }
    }

    // ─── Private: Map offer → Flight entity ──────────────────────

    private Flight mapOfferToFlight(
            DuffelOffer offer, String originCity, String destinationCity) {

        if (offer.getSlices() == null || offer.getSlices().isEmpty()) return null;

        DuffelSlice slice = offer.getSlices().get(0);
        if (slice.getSegments() == null || slice.getSegments().isEmpty()) return null;

        DuffelSegment segment = slice.getSegments().get(0);

        // Flight number: carrier IATA + number e.g. "PK301"
        String carrierIata  = segment.getMarketingCarrier() != null
                ? segment.getMarketingCarrier().getIataCode() : "XX";
        String flightNumber = carrierIata + segment.getFlightNumber();

        String airlineName = segment.getMarketingCarrier() != null
                ? segment.getMarketingCarrier().getName() : "Unknown Airline";

        LocalDateTime departure = parseDateTime(segment.getDepartingAt());
        LocalDateTime arrival   = parseDateTime(segment.getArrivingAt());
        if (departure == null || arrival == null) return null;

        // Duration: parse ISO 8601 "PT2H30M" from slice
        String duration = parseDuration(slice.getDuration());

        // Number of stops = segments - 1
        int stops = slice.getSegments().size() - 1;

        // Price: Duffel gives USD → convert to PKR
        BigDecimal priceUsd = parseMoney(offer.getTotalAmount());
        BigDecimal pricePkr = priceUsd.multiply(USD_TO_PKR)
                .setScale(0, RoundingMode.HALF_UP);

        // Cabin class from first passenger segment
        String cabinClass = "Economy";
        if (segment.getPassengers() != null && !segment.getPassengers().isEmpty()) {
            String raw = segment.getPassengers().get(0).getCabinClassMarketingName();
            if (raw != null && !raw.isBlank()) cabinClass = raw;
        }

        return Flight.builder()
                .flightNumber(flightNumber)
                .airline(airlineName)
                .origin(originCity)
                .destination(destinationCity)
                .departureTime(departure)
                .arrivalTime(arrival)
                .duration(duration)
                .stops(stops)
                .price(pricePkr)
                .currency("PKR")
                .totalSeats(SEATS_PER_FLIGHT)
                .bookingClass(cabinClass)
                .build();
    }

    // ─── Private: Seat generation ─────────────────────────────────

    private void generateSeats(Flight flight) {
        String[] cols = {"A", "B", "C", "D", "E", "F"};
        int rows = (int) Math.ceil((double) SEATS_PER_FLIGHT / cols.length);
        List<Seat> seats = new ArrayList<>();

        for (int row = 1; row <= rows && seats.size() < SEATS_PER_FLIGHT; row++) {
            for (String col : cols) {
                if (seats.size() >= SEATS_PER_FLIGHT) break;
                seats.add(Seat.builder()
                        .flight(flight)
                        .seatNumber(row + col)
                        .status(Seat.SeatStatus.AVAILABLE)
                        .build());
            }
        }
        seatRepository.saveAll(seats);
    }

    // ─── Private: Parsing helpers ─────────────────────────────────

    private String resolveIata(String city) {
        if (city == null) return null;
        return CITY_TO_IATA.get(city.trim().toLowerCase());
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // Duffel format: "2026-05-01T08:00:00"
            return LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse datetime: {}", raw);
            return null;
        }
    }
    private String formatDuration(long totalMinutes) {
        if (totalMinutes <= 0) return "N/A";
        long hours   = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) return hours + "h " + minutes + "m";
        if (hours > 0)                return hours + "h";
        return minutes + "m";
    }

    /**
     * Converts ISO 8601 duration "PT2H30M" → "2h 30m"
     */
    private String parseDuration(String iso) {
        if (iso == null || iso.isBlank()) return "N/A";
        try {
            Pattern p = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?");
            Matcher m = p.matcher(iso);
            if (m.matches()) {
                int hours   = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
                int minutes = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                if (hours > 0 && minutes > 0) return hours + "h " + minutes + "m";
                if (hours > 0)               return hours + "h";
                return minutes + "m";
            }
        } catch (Exception e) {
            log.warn("Could not parse duration: {}", iso);
        }
        return "N/A";
    }

    private BigDecimal parseMoney(String amount) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
    /**
     * Seeds realistic demo flights into DB when Duffel returns nothing.
     * Ensures the UI always shows results during demos/interviews.
     */
    @Transactional
    public List<Flight> seedDemoFlights(
            String originCity, String destinationCity, LocalDate date) {

        // Demo airlines pool
        List<String[]> airlines = List.of(
                new String[]{"PK", "Pakistan International Airlines"},
                new String[]{"EK", "Emirates"},
                new String[]{"G9", "Air Arabia"},
                new String[]{"FZ", "Flydubai"},
                new String[]{"TK", "Turkish Airlines"}
        );

        // Departure times: morning, afternoon, evening
        List<int[]> departureTimes = List.of(
                new int[]{6,  0},
                new int[]{10, 30},
                new int[]{14, 0},
                new int[]{18, 45},
                new int[]{22, 15}
        );

        // Duration by route type
        boolean isIntl = isInternational(originCity, destinationCity);
        int durationMinutes = isIntl ? 210 : 90; // 3h30m intl, 1h30m domestic

        BigDecimal basePrice = isIntl
                ? new BigDecimal("45000")
                : new BigDecimal("9500");

        List<Flight> saved = new ArrayList<>();
        Random rnd = new Random();

        for (int i = 0; i < Math.min(5, airlines.size()); i++) {
            String[] airline = airlines.get(i);
            int[] depTime   = departureTimes.get(i);

            String flightNumber = airline[0]
                    + (100 + rnd.nextInt(900));

            // Skip if already exists
            if (flightRepository.existsByFlightNumber(flightNumber)) continue;

            LocalDateTime departure = date.atTime(depTime[0], depTime[1]);
            LocalDateTime arrival   = departure.plusMinutes(durationMinutes);

            // Price variation ±15%
            long variation = (long)(basePrice.longValue() * (0.85 + rnd.nextDouble() * 0.30));
            BigDecimal price = BigDecimal.valueOf(
                    (variation / 500) * 500); // round to nearest 500

            String duration = formatDuration((long) durationMinutes);

            Flight flight = Flight.builder()
                    .flightNumber(flightNumber)
                    .airline(airline[1])
                    .origin(originCity)
                    .destination(destinationCity)
                    .departureTime(departure)
                    .arrivalTime(arrival)
                    .duration(duration)
                    .stops(0)
                    .price(price)
                    .currency("PKR")
                    .totalSeats(150)
                    .bookingClass("Economy")
                    .build();

            flight = flightRepository.save(flight);
            generateSeats(flight);
            saved.add(flight);
            log.info("Seeded demo flight: {}", flightNumber);
        }

        return saved;
    }

    private boolean isInternational(String origin, String destination) {
        Set<String> domestic = Set.of(
                "karachi","lahore","islamabad","peshawar",
                "quetta","multan","faisalabad"
        );
        return !domestic.contains(origin.toLowerCase())
                || !domestic.contains(destination.toLowerCase());
    }
}