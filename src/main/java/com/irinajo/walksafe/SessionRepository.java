package com.irinajo.walksafe;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SharingSession, UUID> {

    Optional<SharingSession> findByUserIdAndClientSessionId(UUID userId, String clientSessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SharingSession s where s.id = :id and s.userId = :userId")
    Optional<SharingSession> lockOwned(UUID id, UUID userId);

    Optional<SharingSession> findFirstByUserIdOrderByStartedAtDesc(UUID userId);

    @Query("select s from SharingSession s where s.userId = :userId and s.status = 'ACTIVE'")
    List<SharingSession> activeFor(UUID userId);

    /** Sessions the app forgot to end (phone died, app killed): close after 30 min without points. */
    @Query("select s from SharingSession s where s.status = 'ACTIVE' and coalesce(s.lastPointAt, s.startedAt) < :before")
    List<SharingSession> stale(Instant before);
}
