package com.example.tradingsimulator.repository;

import com.example.tradingsimulator.model.UserTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface UserTransactionRepository extends JpaRepository<UserTransaction, Long> {
    List<UserTransaction> findByStatus(String status);

    @Query(value = "SELECT * FROM user_transactions WHERE status = 'PENDING' ORDER BY id ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<UserTransaction> findPendingForUpdate(@Param("limit") int limit);

    @Modifying
    @Query("UPDATE UserTransaction t SET t.status = 'PENDING', t.claimedAt = null WHERE t.status = 'PROCESSING' AND t.claimedAt < :threshold")
    int resetStuck(@Param("threshold") Instant threshold);
}