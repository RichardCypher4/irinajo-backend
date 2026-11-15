package com.irinajo.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TipRepository extends JpaRepository<Tip, UUID> {
    @Query("select t from Tip t where t.version > :since order by t.version asc")
    List<Tip> changedSince(long since);

    @Query("select coalesce(max(t.version), 0) from Tip t")
    long currentVersion();
}
