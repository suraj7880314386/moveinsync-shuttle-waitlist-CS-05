# Complete API Test Flow

## 0. Helpers & Tokens
```bash
BASE=http://localhost:8080

login() { 
  curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" | sed -E 's/.*"token":"([^"]+)".*/\1/'; 
}

call() { 
  if [ -n "$4" ]; then 
    curl -s -X $1 "$BASE$2" -H "Authorization: Bearer $3" -H 'Content-Type: application/json' -d "$4"; 
  else 
    curl -s -X $1 "$BASE$2" -H "Authorization: Bearer $3"; 
  fi; 
  echo; 
}

ts() { date -u -d "+$1" +%Y-%m-%dT%H:%M:%SZ; }

# Tokens Generation
ADMIN=$(login admin 'Nitin7880')
APPROVER=$(login approver 'Approver7880')
SURAJ=$(login suraj 'Suraj7880')
RAHUL=$(login rahul 'Rahul7880')
AJAY=$(login ajay 'Ajay7880')
MOHIT=$(login mohit 'Mohit7880')
AMAN=$(login aman 'Aman7880')
RAJ=$(login raj 'Raj7880')
HIMANSHU=$(login himanshu 'Himanshu7880')

echo "Auth setup complete. Admin Token: ${ADMIN:0:15}..."
1. Registration Test
Bash
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
 -d '{"username":"neha","password":"Passw0rd!","phone":"+919800000000","costCentre":"SALES","tier":"GOLD"}'
2. Scenario A: Capacity, Quota, Group Skip & Auto-Promotion
Bash
# 1. Create Trip (Capacity: 4)
call POST /api/admin/trips $ADMIN "{\"routeName\":\"Gonda Ambedkar Chock to Delhi Kashmiri Gate\",\"departureTime\":\"$(ts '3 hours')\",\"checkInCutoff\":\"$(ts '2 hours')\",\"capacity\":4}"

# 2. Set Quotas
call PUT /api/trips/1/quotas $ADMIN    '{"costCentre":"SALES","seatQuota":1}'
call PUT /api/trips/1/quotas $APPROVER '{"costCentre":"MARKETING","seatQuota":2}'

# 3. Fill the bus (4 seats confirmed)
call POST /api/trips/1/bookings $SURAJ '{"seats":1}'
call POST /api/trips/1/bookings $MOHIT '{"seats":1}'
call POST /api/trips/1/bookings $RAJ   '{"seats":2,"groupLabel":"Finance team"}'

# 4. Waitlist Queue
sleep 1.1; call POST /api/trips/1/bookings $AMAN     '{"seats":2,"groupLabel":"Marketing pair"}'
sleep 1.1; call POST /api/trips/1/bookings $RAHUL    '{"seats":1}'
sleep 1.1; call POST /api/trips/1/bookings $AJAY     '{"seats":1}'
sleep 1.1; call POST /api/trips/1/bookings $HIMANSHU '{"seats":1}'

# 5. Check Availability & Waitlist
call GET /api/trips/1/availability $SURAJ
call GET /api/trips/1/waitlist $APPROVER

# 6. Cancel #1: Suraj cancels -> Aman (group of 2) skipped -> Rahul promoted
call POST /api/bookings/1/cancel $SURAJ

# 7. Cancel #2: Mohit cancels -> Ajay blocked by SALES quota -> Himanshu promoted
call POST /api/bookings/2/cancel $MOHIT
call GET /api/trips/1/waitlist $ADMIN

# 8. Cancel #3: Raj (group of 2) cancels -> Aman (group of 2) promoted together
call POST /api/bookings/3/cancel $RAJ

# 9. Increase Capacity & Quota -> Ajay promoted
call PUT /api/admin/trips/1/capacity $ADMIN '{"capacity":5}'
call PUT /api/trips/1/quotas $APPROVER '{"costCentre":"SALES","seatQuota":2}'
call GET /api/trips/1/availability $SURAJ
call GET /api/admin/notifications $ADMIN
3. Scenario B: High Concurrency Last-Seat Race
Bash
call POST /api/admin/trips $ADMIN "{\"routeName\":\"Race-Trip\",\"departureTime\":\"$(ts '3 hours')\",\"checkInCutoff\":\"$(ts '2 hours')\",\"capacity\":1}"
for t in "$SURAJ" "$RAHUL" "$AJAY" "$MOHIT" "$AMAN" "$RAJ" "$HIMANSHU"; do
  curl -s -X POST $BASE/api/trips/2/bookings -H "Authorization: Bearer $t" -H 'Content-Type: application/json' -d '{"seats":1}' | grep -o '"status":"[A-Z]*"' & 
done; wait
call GET /api/trips/2/availability $ADMIN
4. Scenario C: Same-Second Tiebreaker (Tier Priority)
Bash
call POST /api/admin/trips $ADMIN "{\"routeName\":\"Tiebreak-Trip\",\"departureTime\":\"$(ts '3 hours')\",\"checkInCutoff\":\"$(ts '2 hours')\",\"capacity\":1}"
call POST /api/trips/3/bookings $MOHIT '{"seats":1}'
call POST /api/trips/3/bookings $AJAY '{"seats":1}'; call POST /api/trips/3/bookings $SURAJ '{"seats":1}'
call GET /api/trips/3/waitlist $ADMIN
5. Scenario D: No-Show Seat Reclaim
Bash
call POST /api/admin/trips $ADMIN "{\"routeName\":\"NoShow-Trip\",\"departureTime\":\"$(ts '4 minutes')\",\"checkInCutoff\":\"$(ts '1 minutes')\",\"capacity\":1}"
call POST /api/trips/4/bookings $MOHIT '{"seats":1}'
call POST /api/trips/4/bookings $AMAN  '{"seats":1}'
sleep 90
call GET /api/bookings/me $AMAN
call GET /api/bookings/me $MOHIT
call GET /api/admin/notifications $ADMIN
6. Error & Security Validation
Bash
curl -s -i $BASE/api/trips | head -1
call POST /api/admin/trips $SURAJ '{}'
call POST /api/admin/trips $ADMIN '{"routeName":"","capacity":0}'
call POST /api/trips/1/bookings $RAHUL '{"seats":1}'
call POST /api/trips/1/bookings $MOHIT '{"seats":5}'
call POST /api/bookings/3/cancel $RAHUL
call GET /api/trips/999/availability $SURAJ
curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' -d '{"username":"suraj","password":"wrong"}'
7. Metrics & Monitoring
Bash
call GET /actuator/health $ADMIN
call GET /actuator/metrics/shuttle.promotion.total $ADMIN
call GET /actuator/metrics/shuttle.promotion.skipped $ADMIN
call GET /actuator/metrics/shuttle.request.rejected $ADMIN
curl -s $BASE/actuator/prometheus -H "Authorization: Bearer $ADMIN" | grep '^shuttle'
call POST /api/admin/reconcile $ADMIN
call POST /api/admin/backup $ADMIN
