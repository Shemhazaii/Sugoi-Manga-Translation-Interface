package com.shemhazai.translationservice.translation.batch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface BatchTranslationJobJpaRepository extends JpaRepository<BatchTranslationJobEntity, String> {

    List<BatchTranslationJobEntity> findByStatusIn(
            Collection<BatchTranslationStatus> statuses
    );

    Optional<BatchTranslationJobEntity>
    findFirstByStatusOrderByCreatedAtAsc(
            BatchTranslationStatus status
    );

}
