package com.irinajo.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VoicePackRepository extends JpaRepository<VoicePack, UUID> {
    Optional<VoicePack> findBySpeakerAndLanguage(String speaker, String language);
}
