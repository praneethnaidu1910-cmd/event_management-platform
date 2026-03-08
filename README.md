## Event Management Platform - Backend

Spring Boot backend for a Ticketmaster/Eventbrite‑style platform where organizers create events and attendees purchase tickets.

### Features
- **User authentication**: Register/login with JWT, roles: `ATTENDEE`, `ORGANIZER`, `ADMIN`.
- **Event management**: Organizers can create, update, delete events with multiple ticket types; public can browse published events.
- **Ticketing & orders**: Attendees purchase tickets; orders and individual tickets are stored with unique codes.
- **Inventory safety**: Uses transactional logic to prevent overselling under concurrent purchases.
- **Error handling**: Centralized exception handling with clear HTTP responses.

### Tech Stack
- **Language**: Java 17
- **Framework**: Spring Boot 3.2.x
- **Database**: PostgreSQL 15+
- **Security**: Spring Security + JWT (JJWT)
- **Build**: Maven

### Project Structure (high level)
- `src/main/java/com/eventmanagement`
  - `EventManagementApplication.java` – main Spring Boot entrypoint
  - `entity` – JPA entities (`User`, `Event`, `TicketType`, `Order`, `Ticket`)
  - `repository` – Spring Data JPA repositories
  - `security` – JWT provider, filter, security configuration
  - `service` – business logic for auth, events, orders, current user
  - `controller` – REST controllers for auth, events, and orders
  - `exception` – custom exceptions and global exception handler
- `src/main/resources/application.properties` – DB + JWT configuration
- `db/schema.sql` – SQL script to create the PostgreSQL schema

### Prerequisites
- Java 17 installed and on `PATH`
- PostgreSQL 15+ running locally
- Maven installed (or add a Maven wrapper)

### Database Setup
1. Create the database (or adjust `schema.sql` to remove `CREATE DATABASE` and run inside an existing DB):
   - Connect to PostgreSQL (psql, GUI, etc.).
   - Run the contents of `db/schema.sql`.
2. Confirm tables like `users`, `events`, `ticket_types`, `orders`, `tickets` exist.

### Configuration
Default configuration is in `src/main/resources/application.properties`:
- **Database**
  - `spring.datasource.url=jdbc:postgresql://localhost:5432/event_platform`
  - `spring.datasource.username=postgres`
  - `spring.datasource.password=${DB_PASSWORD:your_password}`
- **JPA**
  - `spring.jpa.hibernate.ddl-auto=validate` (expects schema to already exist)
- **JWT**
  - `app.jwt.secret=${JWT_SECRET:your-256-bit-secret-key-change-this-in-production}`
  - `app.jwt.expiration-ms=86400000`

Override sensitive values with environment variables, e.g.:
- `DB_PASSWORD`
- `JWT_SECRET`

### Running the Application
From the project root (`Event_Management`):

```bash
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

### Core API Endpoints

#### Auth
- **POST** `/api/auth/register`
  - Body: `{ "email", "password", "firstName", "lastName", "role" }`
  - Roles: `ATTENDEE` or `ORGANIZER` (or `ADMIN` if needed)
  - Response: `{ "message", "userId" }`

- **POST** `/api/auth/login`
  - Body: `{ "email", "password" }`
  - Response: `{ "token", "email", "role" }`
  - Use `Authorization: Bearer <token>` for protected endpoints.

#### Events
- **POST** `/api/events` – create event (organizer only)
  - Auth: `ROLE_ORGANIZER`
  - Body (example):
    ```json
    {
      "title": "Music Festival",
      "description": "Annual festival",
      "location": "City Park",
      "venueName": "Main Stage",
      "startDate": "2026-06-01T18:00:00",
      "endDate": "2026-06-01T23:00:00",
      "status": "PUBLISHED",
      "category": "MUSIC",
      "maxAttendees": 5000,
      "ticketTypes": [
        { "name": "General", "description": "GA", "price": 50.0, "quantity": 1000 },
        { "name": "VIP", "description": "Front row", "price": 150.0, "quantity": 100 }
      ]
    }
    ```

- **GET** `/api/events` – list published events (public)
- **GET** `/api/events/{id}` – get single published event (public)
- **PUT** `/api/events/{id}` – update event (organizer + owner)
- **DELETE** `/api/events/{id}` – delete event (organizer + owner)

#### Orders / Tickets
- **POST** `/api/orders/purchase`
  - Auth: `ROLE_ATTENDEE`
  - Body: `{ "ticketTypeId": 1, "quantity": 2 }`
  - Response: `{ "orderId", "totalAmount", "tickets": [ { "id", "ticketCode", "status", "ticketTypeId" } ] }`

### Testing with Postman (Suggested Flow)
1. **Register organizer** – `POST /api/auth/register` with role `ORGANIZER`.
2. **Register attendee** – `POST /api/auth/register` with role `ATTENDEE`.
3. **Login organizer** – `POST /api/auth/login`, copy JWT.
4. **Create event** – `POST /api/events` with organizer token.
5. **List events** – `GET /api/events` (no token required).
6. **Get event details** – `GET /api/events/{id}` (no token required).
7. **Login attendee** – `POST /api/auth/login`, copy JWT.
8. **Purchase tickets** – `POST /api/orders/purchase` with attendee token.
9. **Verify in DB** – check `orders`, `tickets`, and `ticket_types.available` values.

### Notes & Next Steps
- This backend is prepared for a future React frontend to consume the APIs.
- You can extend it with:
  - Pagination and filtering for events.
  - Admin tools for managing users and events.
  - Real payment integration (Stripe, Razorpay, etc.).

