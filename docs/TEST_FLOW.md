# Complete API test flow (fresh DB, bash / Git Bash / WSL)

Reset: stop the app, `rm -rf data backups`, start it again. Seeded users:
`admin/Admin@123 (ADMIN)`, `approver/Approver@123 (APPROVER)`, and password `Pass@123` for
alice(ENG,PLATINUM) bob(ENG,GOLD) carol(ENG,STANDARD) dave(OPS,STANDARD) erin(OPS,SILVER) frank(HR,STANDARD) grace(HR,GOLD).

## 0. Helpers
```bash
BASE=http://localhost:8080
login() { curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" | sed -E 's/.*"token":"([^"]+)".*/\1/'; }
call() { # call METHOD PATH TOKEN [JSON]
  if [ -n "$4" ]; then curl -s -X $1 "$BASE$2" -H "Authorization: Bearer $3" -H 'Content-Type: application/json' -d "$4"; \
  else curl -s -X $1 "$BASE$2" -H "Authorization: Bearer $3"; fi; echo; }
ADMIN=$(login admin 'Admin@123'); APPROVER=$(login approver 'Approver@123')
for u in alice bob carol dave erin frank grace; do eval "${u^^}=\$(login $u 'Pass@123')"; done
# macOS: replace  date -u -d '+3 hours'  with  date -u -v+3H
ts() { date -u -d "+$1" +%Y-%m-%dT%H:%M:%SZ; }
echo "$ALICE" | cut -c1-20   # sanity: a token prefix is printed
```
PowerShell users: use Postman, or WSL/Git Bash for the helpers above.

## 1. Login / registration
```bash
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
 -d '{"username":"henry","password":"Passw0rd!","phone":"+919800000000","costCentre":"eng","tier":"GOLD"}'
curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' -d '{"username":"alice","password":"Pass@123"}'
```
Expect 201 (user, role EMPLOYEE) then 200 `{token,tokenType:"Bearer",...}`.

## 2. Scenario A - capacity, quota, group skip, fair promotion (bus of 4)
```bash
call POST /api/admin/trips $ADMIN "{\"routeName\":\"Whitefield-Tech Park\",\"departureTime\":\"$(ts '3 hours')\",\"checkInCutoff\":\"$(ts '2 hours')\",\"capacity\":4}"
call PUT  /api/trips/1/quotas $ADMIN    '{"costCentre":"ENG","seatQuota":1}'
call PUT  /api/trips/1/quotas $APPROVER '{"costCentre":"OPS","seatQuota":2}'
# fill the bus (booking ids 1,2,3) -> all CONFIRMED
call POST /api/trips/1/bookings $ALICE '{"seats":1}'
call POST /api/trips/1/bookings $DAVE  '{"seats":1}'
call POST /api/trips/1/bookings $FRANK '{"seats":2,"groupLabel":"HR team"}'
# bus full -> everyone below is WAITLISTED, positions 1..4 (sleep keeps them in different seconds => pure FIFO)
sleep 1.1; call POST /api/trips/1/bookings $ERIN  '{"seats":2,"groupLabel":"OPS pair"}'
sleep 1.1; call POST /api/trips/1/bookings $BOB   '{"seats":1}'
sleep 1.1; call POST /api/trips/1/bookings $CAROL '{"seats":1}'
sleep 1.1; call POST /api/trips/1/bookings $GRACE '{"seats":1}'
call GET /api/trips/1/availability $ALICE     # full 4/4, waitlistedBookings 4, quotas
call GET /api/trips/1/waitlist $APPROVER      # erin(2) bob carol grace
```
**Cancel #1 - one seat frees; group of 2 skipped, next individual promoted**
```bash
call POST /api/bookings/1/cancel $ALICE
```
Expect `promoted` = bob (booking 5). erin's group (needs 2) stays WAITLISTED (`skipCount` 1 in the waitlist view).

**Cancel #2 - freed seat blocked by quota for carol, goes to grace**
```bash
call POST /api/bookings/2/cancel $DAVE
```
Expect `promoted` = grace (7). carol (ENG quota 1 already used by bob) is skipped although a seat existed.
```bash
call GET /api/trips/1/waitlist $ADMIN         # erin(skip 2), carol
```
**Cancel #3 - group promoted all-or-nothing**
```bash
call POST /api/bookings/3/cancel $FRANK
```
Expect `promoted` = erin with `seats:2` (both seats at once); carol still waitlisted (quota).

**Capacity + quota changes trigger promotion**
```bash
call PUT /api/admin/trips/1/capacity $ADMIN '{"capacity":5}'     # carol still blocked by ENG quota 1
call PUT /api/trips/1/quotas $APPROVER '{"costCentre":"ENG","seatQuota":2}'   # carol promoted now
call GET /api/trips/1/availability $ALICE
call GET /api/admin/notifications $ADMIN      # SMS rows for bob, grace, erin, carol (status SENT)
```

## 3. Scenario B - last-seat race (7 parallel requests, bus of 1)
```bash
call POST /api/admin/trips $ADMIN "{\"routeName\":\"Race\",\"departureTime\":\"$(ts '3 hours')\",\"checkInCutoff\":\"$(ts '2 hours')\",\"capacity\":1}"
for t in "$ALICE" "$BOB" "$CAROL" "$DAVE" "$ERIN" "$FRANK" "$GRACE"; do
  curl -s -X POST $BASE/api/trips/2/bookings -H "Authorization: Bearer $t" -H 'Content-Type: application/json' -d '{"seats":1}' | grep -o '"status":"[A-Z]*"' & 
done; wait
call GET /api/trips/2/availability $ADMIN     # confirmedSeats 1, waitlistedBookings 6
```
Exactly one `CONFIRMED`, six `WAITLISTED`. The automated version with 20 threads: `mvn test -Dtest=SeatConcurrencyTest`.

## 4. Scenario C - tiebreaker (same second)
```bash
call POST /api/admin/trips $ADMIN "{\"routeName\":\"Tiebreak\",\"departureTime\":\"$(ts '3 hours')\",\"checkInCutoff\":\"$(ts '2 hours')\",\"capacity\":1}"
call POST /api/trips/3/bookings $DAVE '{"seats":1}'
call POST /api/trips/3/bookings $CAROL '{"seats":1}'; call POST /api/trips/3/bookings $ALICE '{"seats":1}'
call GET /api/trips/3/waitlist $ADMIN   # alice (PLATINUM) before carol if both landed in the same second; rerun if a second boundary was crossed
```

## 5. Scenario D - no-show reclaim (automatic, ~75 s)
```bash
call POST /api/admin/trips $ADMIN "{\"routeName\":\"NoShow\",\"departureTime\":\"$(ts '4 minutes')\",\"checkInCutoff\":\"$(ts '1 minutes')\",\"capacity\":1}"
call POST /api/trips/4/bookings $DAVE '{"seats":1}'      # CONFIRMED, never checks in
call POST /api/trips/4/bookings $ERIN '{"seats":1}'      # WAITLISTED
sleep 90                                                  # cut-off passes; job runs every 10 s
call GET /api/bookings/me $ERIN                           # erin CONFIRMED, checkedIn true
call GET /api/bookings/me $DAVE                           # dave CANCELLED, cancelReason NO_SHOW
call GET /api/admin/notifications $ADMIN
```
Check-in path: `call POST /api/bookings/<id>/check-in $TOKEN` (before cut-off => 200; after => 409 CHECK_IN_CLOSED).
Manual trigger: `call POST /api/admin/trips/4/reclaim $ADMIN`.

## 6. Error handling & security
```bash
curl -s -i $BASE/api/trips | head -1                                   # 401
call POST /api/admin/trips $ALICE '{}'                                 # 403 FORBIDDEN (employee)
call POST /api/admin/trips $ADMIN '{"routeName":"","capacity":0}'      # 400 VALIDATION_FAILED + violations
call POST /api/trips/1/bookings $BOB '{"seats":1}'                     # 409 DUPLICATE_BOOKING
call POST /api/trips/1/bookings $DAVE '{"seats":5}'                    # 422 EXCEEDS_VEHICLE_CAPACITY
call POST /api/bookings/3/cancel $BOB                                  # 403 (not owner)  /  409 if already cancelled
call GET /api/trips/999/availability $ALICE                            # 404
curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' -d '{"username":"alice","password":"wrong"}'  # 401 generic
```

## 7. Monitoring, caching, resilience
```bash
call GET /actuator/health $ADMIN
call GET /actuator/metrics/shuttle.promotion.total $ADMIN
call GET /actuator/metrics/shuttle.promotion.skipped $ADMIN
call GET /actuator/metrics/shuttle.request.rejected $ADMIN
curl -s $BASE/actuator/prometheus -H "Authorization: Bearer $ADMIN" | grep '^shuttle'
call GET /api/trips/1/availability $ALICE; call GET /api/trips/1/availability $ALICE   # same computedAt = cache hit
call POST /api/admin/reconcile $ADMIN       # {"tripsChecked":..,"promotions":0,"violations":0}
call POST /api/admin/backup $ADMIN          # file path under ./backups
```
SMS retry demo: restart with `--app.sms.failure-probability=0.7` (`mvn spring-boot:run -Dspring-boot.run.arguments=--app.sms.failure-probability=0.7`), trigger promotions, watch `/api/admin/notifications` attempts increase and eventually SENT.

## 8. Automated tests
`mvn clean verify` (unit + concurrency + security integration tests).
