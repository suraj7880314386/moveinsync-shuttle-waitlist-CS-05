package com.moveinsync.shuttle.repository;

import com.moveinsync.shuttle.domain.Booking;
import com.moveinsync.shuttle.domain.BookingStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByTripIdAndStatus(Long tripId, BookingStatus status);

    List<Booking> findByTripIdAndStatusAndCheckedInFalse(Long tripId, BookingStatus status);

    boolean existsByTripIdAndUserIdAndStatusIn(Long tripId, Long userId, Collection<BookingStatus> statuses);

    List<Booking> findByUserIdOrderByRequestedAtDesc(Long userId);

    long countByTripIdAndStatus(Long tripId, BookingStatus status);

    long countByStatus(BookingStatus status);

    @Query("select b.tripId from Booking b where b.id = :id")
    Optional<Long> findTripIdById(@Param("id") Long id);

    @Query("select b.costCentre, coalesce(sum(b.seats), 0) from Booking b where b.tripId = :tripId and b.status = :status group by b.costCentre")
    List<Object[]> sumSeatsByCostCentre(@Param("tripId") Long tripId, @Param("status") BookingStatus status);

    @Query("select coalesce(sum(b.seats), 0) from Booking b where b.tripId = :tripId and b.status = :status")
    long sumSeats(@Param("tripId") Long tripId, @Param("status") BookingStatus status);

    @Query("select coalesce(sum(b.seats), 0) from Booking b where b.tripId = :tripId and b.status = :status and b.costCentre = :cc")
    long sumSeatsForCostCentre(@Param("tripId") Long tripId, @Param("status") BookingStatus status, @Param("cc") String cc);

    @Query("select distinct b.tripId from Booking b where b.status = :status and b.checkedIn = false "
            + "and b.tripId in (select t.id from Trip t where t.checkInCutoff < :now)")
    List<Long> findTripIdsWithNoShows(@Param("status") BookingStatus status, @Param("now") Instant now);
}
