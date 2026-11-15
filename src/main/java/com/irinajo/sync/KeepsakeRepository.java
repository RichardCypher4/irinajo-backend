package com.irinajo.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface KeepsakeRepository extends JpaRepository<Keepsake, Keepsake.Key> {

    /**
     * Add-only upsert. If two phones disagree on the date, the earliest earned date is kept.
     * Returns 1 if something changed, 0 if it was already recorded.
     */
    @Modifying
    @Query(value = """
            INSERT INTO keepsakes (user_id, keepsake_id, earned_at, seq)
            VALUES (:userId, :keepsakeId, :earnedAt, :seq)
            ON CONFLICT (user_id, keepsake_id) DO UPDATE
               SET earned_at = EXCLUDED.earned_at, seq = EXCLUDED.seq
             WHERE keepsakes.earned_at > EXCLUDED.earned_at
            """, nativeQuery = true)
    int addOnly(UUID userId, String keepsakeId, long earnedAt, long seq);

    @Query("select k from Keepsake k where k.userId = :userId and k.seq > :since order by k.seq asc")
    List<Keepsake> changesSince(UUID userId, long since);

    List<Keepsake> findByUserId(UUID userId);
}
