package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.medicationinventory.data.local.entity.SyncQueueEntity;

import java.util.List;


/**
 * Stores pending upload retry state and selects due work for background processing. Retry and manual-unblock updates are conditional so queue status reflects an actual state transition.
 */
@Dao
public interface SyncQueueDao
{
    @Insert(onConflict = OnConflictStrategy.ABORT)

    void enqueue(SyncQueueEntity item);

    @Query("SELECT * FROM sync_queue " + "WHERE nextAttemptAt <= :now " +
            "ORDER BY nextAttemptAt ASC")

    List<SyncQueueEntity> getDueItems(long now);

    @Query("SELECT COUNT(*) FROM sync_queue")

    LiveData<Integer> observePendingCount();

    @Query("UPDATE sync_queue " + "SET attemptCount = attemptCount + 1, " +
            "lastAttemptAt = :lastAttemptAt, " + "nextAttemptAt = :nextAttemptAt, " +
            "lastErrorCode = :lastErrorCode " + "WHERE operationId = :operationId")

    void recordAttempt(String operationId, long lastAttemptAt, long nextAttemptAt,
                       String lastErrorCode);

    @Query("UPDATE sync_queue SET nextAttemptAt = 0, lastErrorCode = NULL " +
            "WHERE operationId = :operationId " +
            "AND nextAttemptAt = 9223372036854775807 " +
            "AND lastErrorCode = 'FIRESTORE_PERMISSION_DENIED' " +
            "AND EXISTS (SELECT 1 FROM inventory_operations io " +
            "WHERE io.operationId = sync_queue.operationId " +
            "AND io.performedBy = :userId AND io.status = 'LOCAL_PENDING')")

    int retryHeldPermissionDeniedUpload(String operationId, String userId);

    @Query("DELETE FROM sync_queue " + "WHERE operationId = :operationId")

    void removeAfterReconciliation(String operationId);

    @Query("SELECT sq.operationId " + "FROM sync_queue sq " + "INNER JOIN inventory_operations io "
            + "ON io.operationId = sq.operationId " + "WHERE sq.nextAttemptAt <= :now "
            + "AND io.status = 'LOCAL_PENDING' " + "ORDER BY io.clientCreatedAt ASC")

    List<String> getDueLocalPendingOperationIds(long now);

    @Query(
            "SELECT sq.operationId " +
                    "FROM sync_queue sq " +
                    "INNER JOIN inventory_operations io " +
                    "ON io.operationId = sq.operationId " +
                    "WHERE sq.nextAttemptAt <= :now " +
                    "AND io.status = 'SUBMITTED' " +
                    "ORDER BY io.clientCreatedAt ASC"
    )

    List<String> getDueSubmittedOperationIds(
            long now
    );
}