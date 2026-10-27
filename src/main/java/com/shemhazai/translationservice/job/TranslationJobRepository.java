package com.shemhazai.translationservice.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TranslationJobRepository extends JpaRepository<TranslationJob, UUID> {
}
