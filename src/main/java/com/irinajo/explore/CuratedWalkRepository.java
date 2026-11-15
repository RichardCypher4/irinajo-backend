package com.irinajo.explore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CuratedWalkRepository extends JpaRepository<CuratedWalk, UUID> {

    /** Bounding-box pre-filter; exact distance is computed in Java. */
    @Query("""
           select w from CuratedWalk w where w.active = true
             and w.startLat between :minLat and :maxLat and w.startLng between :minLng and :maxLng
             and (:category is null or w.category = :category)""")
    List<CuratedWalk> inBox(double minLat, double maxLat, double minLng, double maxLng, String category);

    @Query("""
           select w from CuratedWalk w where w.active = true and lower(w.name) like lower(concat('%', :q, '%'))
             and w.startLat between :minLat and :maxLat and w.startLng between :minLng and :maxLng""")
    List<CuratedWalk> search(String q, double minLat, double maxLat, double minLng, double maxLng);
}
