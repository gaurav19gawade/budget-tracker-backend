package com.budgettracker.repository;

import com.budgettracker.model.PlaidItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlaidItemRepository extends JpaRepository<PlaidItem, Long> {

    List<PlaidItem> findByUserId(Long userId);

    Optional<PlaidItem> findByPlaidItemId(String plaidItemId);

    Optional<PlaidItem> findByIdAndUserId(Long id, Long userId);
}