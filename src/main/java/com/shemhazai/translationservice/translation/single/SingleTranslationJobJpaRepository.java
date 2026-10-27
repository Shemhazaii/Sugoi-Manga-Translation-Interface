package com.shemhazai.translationservice.translation.single;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SingleTranslationJobJpaRepository extends JpaRepository<SingleTranslationJobEntity, String> {

    List<SingleTranslationJobEntity> findByStatusIn(
            List<SingleTranslationJobStatus> statuses
    );

    List<SingleTranslationJobEntity>
    findByStatusInAndUpdatedAtBefore(
            List<SingleTranslationJobStatus> statuses,
            Instant cutoff
    );

    Optional<SingleTranslationJobEntity>
    findFirstByStatusOrderByCreatedAtAsc(
            SingleTranslationJobStatus status
    );

    List<SingleTranslationJobEntity> findByStatusIn(
            Collection<SingleTranslationJobStatus> statuses
    );





}
