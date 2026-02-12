package com.budgettracker.repository;

import com.budgettracker.model.TellerEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TellerEnrollmentRepository extends JpaRepository<TellerEnrollment, Long> {

    List<TellerEnrollment> findByUserId(Long userId);

    Optional<TellerEnrollment> findByEnrollmentId(String enrollmentId);

    Optional<TellerEnrollment> findByIdAndUserId(Long id, Long userId);
}
