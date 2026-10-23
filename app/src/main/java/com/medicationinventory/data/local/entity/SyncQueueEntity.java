package com.medicationinventory.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;


/**
 * Retry metadata for one pending inventory operation. The operation ID is also the queue primary
 * key; attempt count and last/next attempt times support backoff, while the last error code explains
 * why synchronization is delayed. The row is local scheduling state and does not itself indicate
 * that the server accepted the operation.
 */
@Entity(tableName = "sync_queue",

        foreignKeys = {
                @ForeignKey(
                        entity = InventoryOperationEntity.class,
                        parentColumns = "operationId",
                        childColumns = "operationId",
                        onUpdate = ForeignKey.NO_ACTION,
                        onDelete = ForeignKey.CASCADE
                )
        },

        indices = {
                @Index(value = "nextAttemptAt")
        }
)
public class SyncQueueEntity {

    @PrimaryKey
    @NonNull
    private String operationId;

    private int attemptCount;

    private long nextAttemptAt;

    private long lastAttemptAt;

    private String lastErrorCode;


    public SyncQueueEntity(
            @NonNull String operationId,
            int attemptCount,
            long nextAttemptAt,
            long lastAttemptAt,
            String lastErrorCode
    )
    {

        this.operationId = operationId;

        this.attemptCount = attemptCount;

        this.nextAttemptAt = nextAttemptAt;

        this.lastAttemptAt = lastAttemptAt;

        this.lastErrorCode = lastErrorCode;
    }

    @NonNull
    public String getOperationId()
    {
        return operationId;
    }

    public void setOperationId(@NonNull String operationId)
    {
        this.operationId = operationId;
    }

    public int getAttemptCount()
    {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount)
    {
        this.attemptCount = attemptCount;
    }

    public long getNextAttemptAt()
    {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(long nextAttemptAt)
    {
        this.nextAttemptAt =
                nextAttemptAt;
    }

    public long getLastAttemptAt()
    {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(long lastAttemptAt)
    {
        this.lastAttemptAt =
                lastAttemptAt;
    }

    public String getLastErrorCode()
    {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode)
    {
        this.lastErrorCode =
                lastErrorCode;
    }
}