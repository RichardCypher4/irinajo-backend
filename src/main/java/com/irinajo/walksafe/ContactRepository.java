package com.irinajo.walksafe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID> {
    List<Contact> findByUserIdOrderByCreatedAtAsc(UUID userId);
    Optional<Contact> findByIdAndUserId(UUID id, UUID userId);
    Optional<Contact> findByLinkToken(String linkToken);
    long countByUserId(UUID userId);
}
