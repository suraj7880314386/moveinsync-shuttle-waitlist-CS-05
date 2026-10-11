# Shuttle Waitlist & Fair Seat Allocation Engine

Backend service built with Spring Boot 3 and Java 21 to manage office shuttle reservations, waitlist queuing, department quotas, and race-condition-free promotions.

---

## 1. Tech Stack
* **Language & Framework:** Java 21, Spring Boot 3.5
* **Persistence & Locking:** Spring Data JPA, Hibernate, H2 (File-based: `./data/shuttle`)
* **Security:** Stateless JWT Authentication with Role-Based Access Control (RBAC)
* **Caching:** Caffeine In-Memory Cache (TTL eviction)
* **Metrics & Observability:** Micrometer, Actuator, Prometheus

---

## 2. Quick Start

### Build & Run
```bash
# Compile and run test suite
mvn clean verify

# Start service on http://localhost:8080
mvn spring-boot:run
Default Admin Credentials: admin / Nitin7880
Health Endpoint: GET http://localhost:8080/actuator/health
API Test Flow: Refer to docs/TEST_FLOW.md for complete curl/bash execution.
3. Data Model (ER)
erDiagram
  APP_USER ||--o{ BOOKING : requests
  TRIP ||--o{ BOOKING : contains
  TRIP ||--o{ COST_CENTRE_QUOTA : constrains
  APP_USER ||--o{ NOTIFICATION : receives

  TRIP {
    long id PK
    string routeName
    instant departureTime
    instant checkInCutoff
    int capacity
  }
  BOOKING {
    long id PK
    long tripId FK
    long userId FK
    string costCentre
    string tier
    int seats
    string status
    int waitlistPosition
    bool checkedIn
  }
  COST_CENTRE_QUOTA {
    long id PK
    long tripId FK
    string costCentre
    int seatQuota
  }
  APP_USER {
    long id PK
    string username
    string role
    string costCentre
    string tier
    string phone
  }
  NOTIFICATION {
    long id PK
    long userId
    string message
    string status
  }
4. Core Business Logic
Fair Queue Ordering: Waitlist follows strict FIFO order based on booking request timestamp. If two requests arrive in the exact same second, priority breaks down by Employee Tier (PLATINUM > GOLD > SILVER > STANDARD).
Cost-Centre Quota Check: Bookings require both available shuttle seats and remaining departmental quota. If a department cap is reached, new requests enter the waitlist even if physical bus seats are free.
All-or-Nothing Groups: Multi-seat team bookings are treated atomically. If insufficient contiguous seats free up, the group is skipped rather than fragmented, allowing smaller requests behind them to advance.
Concurrency Control: Seat modifications acquire a pessimistic write lock (SELECT ... FOR UPDATE) on the target trip row, preventing double-allocations and over-booking under parallel booking/cancellation load.
No-Show Reclaim: Periodic worker reclaims confirmed seats if check-in is missed prior to the configured cut-off timestamp.
5. REST API Endpoints
Method	Endpoint	Access	Description
POST	/api/auth/login	Public	Authenticate user & issue JWT
POST	/api/auth/register	Public	Employee self-registration
POST	/api/admin/trips	ADMIN	Create shuttle route with capacity
PUT	/api/admin/trips/{id}/capacity	ADMIN	Update vehicle capacity
PUT	/api/trips/{id}/quotas	ADMIN, APPROVER	Configure department quota
GET	/api/trips/{id}/availability	Public	Query seat & quota availability (Cached)
GET	/api/trips/{id}/waitlist	ADMIN, APPROVER	View ordered waitlist
POST	/api/trips/{id}/bookings	Authenticated	Create individual or group booking
POST	/api/bookings/{id}/cancel	Owner / ADMIN	Cancel reservation & auto-promote
POST	/api/bookings/{id}/check-in	Owner / ADMIN	Passenger check-in before cutoff
POST	/api/admin/trips/{id}/reclaim	ADMIN	Trigger no-show seat reclamation
GET	/actuator/metrics	ADMIN	Expose operational counters
6. Architectural Evaluation Matrix
Area	Solution
Authentication & RBAC	BCrypt password hashing, stateless JWT filter, granular method security (@PreAuthorize).
Concurrency Safety	Pessimistic database row locks serialise updates per trip while allowing independent trips to process concurrently.
Fault Tolerance	Transactional outbox pattern for asynchronous SMS notifications with scheduled retries.
Caching Strategy	Caffeine cache on /availability endpoint with automated cache eviction upon booking/cancellation commits.
Observability	Custom Micrometer metrics tracking promotions, waitlist size, quota skips, and cancellations.
Error Handling	Unified @ControllerAdvice providing consistent error formats without leaking database details or internal stack traces.
7. Algorithmic Complexity
Waitlist Evaluation: 
O
(
W
log
⁡W
)
O(WlogW)
 where 
W
W
 is total waitlisted entries (sorting by arrival time and tier tiebreaker).
Seat Allocation Pass: 
O
(
W
)
O(W)
 linear scan across active waitlist entries.
Availability Lookup: 
O
(
1
)
O(1)
 on cache hit; 
O
(
C
)
O(C)
 database aggregation where 
C
C
 is cost-centre count.
8. Verification & Testing
Execute automated unit, integration, and high-concurrency race condition tests:
code
Bash
mvn test
Key test suites:
WaitlistOrderTest: Verifies FIFO and same-second tier tiebreaker correctness.
PromotionPlannerTest: Validates departmental quota restrictions and group-skip logic.
SeatConcurrencyTest: Simulates 20 parallel threads competing for the last seat to prove zero double-booking.
SecurityApiTest: End-to-end authorization checks (401/403/400).
