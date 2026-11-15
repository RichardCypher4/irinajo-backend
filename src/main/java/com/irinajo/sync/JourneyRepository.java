package com.irinajo.sync;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface JourneyRepository extends JpaRepository<Journey, Journey.Key> {

    @Query("select j from Journey j where j.userId = :userId and j.seq > :since order by j.seq asc")
    List<Journey> changesSince(UUID userId, long since, Pageable page);

    @Query("""
           select j from Journey j where j.userId = :userId and j.seq > :since
             and (j.deleted = true or j.startedAt >= :startedAfter)
           order by j.seq asc""")
    List<Journey> recentChangesSince(UUID userId, long since, long startedAfter, Pageable page);

    @Query("select j from Journey j where j.userId = :userId and j.deleted = false order by j.startedAt asc")
    List<Journey> allActive(UUID userId);

    @Query("select j from Journey j where j.geocoded = false and j.deleted = false order by j.seq asc")
    List<Journey> needingGeocode(Pageable page);

    @Modifying
    @Query("delete from Journey j where j.userId = :userId")
    int deleteAllForUser(UUID userId);
}
