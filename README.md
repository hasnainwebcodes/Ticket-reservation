# ✈ TicketFlow — Flight Ticket Reservation System

A production-grade flight booking platform built as a **Spring Boot monolith**, demonstrating enterprise backend concepts including pessimistic locking, async processing, third-party API integration, and JWT-based security.

---

## 🚀 Live Demo

> Coming soon — deploying in proccess

---

## 📸 Screenshots

| Home | Search Results | Booking | Confirmation |
|------|---------------|---------|--------------|
| ![Home](docs/screenshots/home.png) | ![Results](docs/screenshots/results.png) | ![Book](docs/screenshots/book.png) | ![Confirm](docs/screenshots/confirm.png) |

---

## 🧠 Key Engineering Concepts

### 1. Pessimistic Locking — Double Booking Prevention
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Seat s WHERE s.id = :seatId AND s.status = 'AVAILABLE'")
Optional<Seat> findAvailableSeatWithLock(@Param("seatId") Long seatId);
```
When two users click "Book" simultaneously, only one transaction acquires the database row lock. The other receives a "Seat no longer available" response — preventing double booking at the database level.

### 2. 10-Minute Seat Hold System
When a user opens the booking page, their seat is immediately marked as `HELD` with a timestamp and their user ID. A Spring Scheduler runs every 60 seconds to release seats held for more than 10 minutes — ensuring inventory isn't locked by users who abandon the payment page.

```
User opens book page → Seat: AVAILABLE → HELD (heldByUserId, heldAt)
                                              ↓
                                    Payment submitted within 10 min?
                                         YES → BOOKED
                                         NO  → Scheduler releases → AVAILABLE
```

### 3. User-Specific Hold Validation
The booking query checks that the seat is either `AVAILABLE` or `HELD by THIS user specifically`:
```java
@Query("SELECT s FROM Seat s WHERE s.id = :seatId AND " +
       "(s.status = 'AVAILABLE' OR " +
       "(s.status = 'HELD' AND s.heldByUserId = :userId))")
```
This means User B cannot book a seat held by User A — even if they know the seat ID.

### 4. N+1 Query Elimination
Flight search uses a single batch query for seat counts across all results:
```java
// One query for ALL flights — not one per flight
SELECT s.flight_id, COUNT(s.id) FROM seats s
WHERE s.flight_id IN (?, ?, ?, ...) AND s.status = 'AVAILABLE'
GROUP BY s.flight_id
```

### 5. Async Email Delivery
Confirmation emails with embedded QR boarding passes are sent asynchronously via `@Async` — the booking API responds immediately without waiting for SMTP.

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.x |
| Security | Spring Security + JWT (HTTP-only cookies) |
| Database | PostgreSQL |
| Cache | Redis |
| ORM | Spring Data JPA / Hibernate |
| Payments | Stripe API (test mode) |
| Flight Data | Duffel API |
| QR Code | ZXing (Google) |
| Email | Spring Mail (Gmail SMTP) |
| Frontend | Thymeleaf + Bootstrap 5 |
| API Docs | SpringDoc OpenAPI (Swagger) |
| Build | Maven |

---

## 📁 Project Structure

```
src/main/java/system/ticket/reservation/
├── config/
│   ├── AppConfig.java           # PasswordEncoder, RestTemplate, AuthManager beans
│   └── SecurityConfig.java      # JWT filter chain, route permissions
├── controllers/
│   ├── PageController.java      # Thymeleaf page routing
│   ├── AuthController.java      # /api/auth — register, login, logout
│   ├── FlightController.java    # /api/flights — search
│   └── BookingController.java   # /api/bookings — create, cancel, my-bookings
├── services/
│   ├── UserService.java         # Auth logic, UserDetailsService impl
│   ├── FlightService.java       # Search with DB-first + Duffel fallback
│   ├── DuffelService.java       # Duffel API integration + demo seeding
│   ├── BookingService.java      # Core booking flow — Stripe, QR, email
│   ├── QrCodeService.java       # ZXing QR generation
│   ├── EmailService.java        # HTML email with embedded QR
│   └── SeatHoldScheduler.java   # Scheduled seat hold expiry
├── security/
│   ├── JwtService.java          # Token generation and validation
│   └── JwtAuthenticationFilter.java  # Reads JWT from cookie OR header
├── entity/
│   ├── User.java
│   ├── UserPrincipal.java
│   ├── Flight.java
│   ├── Seat.java
│   └── Booking.java
├── dto/
│   ├── duffel/
│   │   └── DuffelOfferResponse.java
│   ├── ApiResponse.java
│   ├── AuthResponse.java
│   ├── FlightSearchDto.java
│   ├── BookingRequestDto.java
│   └── BookingResponseDto.java
├── repos/
│   ├── UserRepository.java
│   ├── FlightRepository.java
│   ├── SeatRepository.java
│   └── BookingRepository.java
└── handler/
    └── GlobalExceptionHandler.java
```

---

## ⚙️ Getting Started

### Prerequisites
- Java 17+
- PostgreSQL 14+
- Redis (local or cloud)
- Maven 3.8+

### 1. Clone the repository
```bash
git clone https://github.com/YOUR_USERNAME/ticketflow.git
cd ticketflow
```

### 2. Create PostgreSQL database
```sql
CREATE DATABASE ticket_reservation;
```

### 3. Configure environment
Create `src/main/resources/application-local.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ticket_reservation
spring.datasource.username=postgres
spring.datasource.password=your_password

spring.data.redis.host=localhost
spring.data.redis.port=6379

spring.mail.username=your@gmail.com
spring.mail.password=your_app_password

stripe.secret.key=sk_test_your_key
stripe.publishable.key=pk_test_your_key

jwt.secret=your_long_random_secret_string

duffel.api.token=duffel_test_your_token
duffel.api.version=v2
```

### 4. Run
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

App runs at `http://localhost:3010`

---

## 🔌 API Endpoints

Full interactive docs available at `/swagger-ui.html`

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, sets HTTP-only JWT cookie |
| POST | `/api/auth/logout` | Clears JWT cookie |
| GET | `/api/auth/me` | Get current user profile |

### Flights
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/flights/search?origin=Karachi&destination=Dubai&date=2026-05-01` | Search flights |

### Bookings
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/bookings` | Create booking + process payment |
| GET | `/api/bookings/my-bookings` | Get user's bookings |
| PATCH | `/api/bookings/{id}/cancel` | Cancel booking + refund |
| POST | `/api/bookings/hold/{seatId}` | Hold seat for 10 minutes |

---

## 🔐 Security

- Passwords hashed with **BCrypt**
- JWT stored in **HTTP-only cookies** (XSS protected)
- **CSRF** mitigated via SameSite cookie policy
- Pessimistic DB locking prevents **race conditions**
- All booking endpoints require authentication
- Users can only cancel **their own** bookings

---

## 💳 Stripe Test Mode

Use these test card details on the booking page:

| Field | Value |
|-------|-------|
| Card Number | `4242 4242 4242 4242` |
| Expiry | Any future date |
| CVV | Any 3 digits |

---

## 🌐 Flight Data Strategy

```
User searches route + date
        ↓
Check PostgreSQL (cached results)
        ↓ (if empty)
Call Duffel API → persist flights + generate seats
        ↓ (if Duffel returns nothing)
Seed realistic demo flights (60–220 seats, real airline names)
        ↓
Return results — all subsequent searches hit DB instantly
```

---

## 📧 Contact

**Hasnain Raza**
Karachi, Pakistan
📧 vighiorazahasnain@gmail.com

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).
