package com.irinajo.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {
    @Modifying
    @Transactional
    @Query("delete from OtpChallenge o where o.expiresAt < :before")
    int deleteExpired(Instant before);
}
