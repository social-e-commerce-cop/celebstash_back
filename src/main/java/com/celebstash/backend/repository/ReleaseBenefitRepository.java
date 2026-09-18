package com.celebstash.backend.repository;

import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.ReleaseBenefit;
import com.celebstash.backend.model.enums.BenefitType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReleaseBenefitRepository extends JpaRepository<ReleaseBenefit, Long> {
    List<ReleaseBenefit> findByRelease(MusicRelease release);
    List<ReleaseBenefit> findByReleaseAndEnabledTrue(MusicRelease release);
    Optional<ReleaseBenefit> findByReleaseAndBenefitType(MusicRelease release, BenefitType benefitType);
    void deleteByRelease(MusicRelease release);
}
