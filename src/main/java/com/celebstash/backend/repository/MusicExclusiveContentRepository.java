package com.celebstash.backend.repository;

import com.celebstash.backend.model.MusicExclusiveContent;
import com.celebstash.backend.model.MusicRelease;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MusicExclusiveContentRepository extends JpaRepository<MusicExclusiveContent, Long> {
    List<MusicExclusiveContent> findByReleaseOrderBySortOrderAscCreatedAtAsc(MusicRelease release);
    void deleteByRelease(MusicRelease release);
}
