package com.medicationinventory.data.local.dao;

import androidx.lifecycle.LiveData;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Upsert;

import com.medicationinventory.data.local.entity.AuditCacheEntity;

import java.util.List;


/**
 * Maintains the bounded local cache of server audit records and exposes recent, detail, and filtered views to the audit UI. This DAO does not create authoritative audit events; trusted backend services write those events.
 */
@Dao
public interface AuditDao
{

    @Upsert

    void upsertFromSync(
            AuditCacheEntity auditRecord
    );


    @Upsert

    void upsertFromSync(
            List<AuditCacheEntity> auditRecords
    );


    @Query(
            "SELECT * " +
                    "FROM audit_cache " +
                    "ORDER BY performedAt DESC, auditId DESC " +
                    "LIMIT :limit"
    )
    LiveData<List<AuditCacheEntity>>
    observeRecent(
            int limit
    );


    @Query(
            "SELECT * " +
                    "FROM audit_cache " +
                    "WHERE auditId = :auditId " +
                    "LIMIT 1"
    )
    LiveData<AuditCacheEntity>
    observeById(
            String auditId
    );


    @Query(
            "SELECT * " +
                    "FROM audit_cache " +

                    "WHERE (:actionType IS NULL " +
                    "OR actionType = :actionType) " +

                    "AND (:performedBy IS NULL " +
                    "OR performedBy = :performedBy) " +

                    "AND (:startInclusive <= 0 " +
                    "OR performedAt >= :startInclusive) " +

                    "AND (:endExclusive <= 0 " +
                    "OR performedAt < :endExclusive) " +

                    "ORDER BY performedAt DESC, auditId DESC " +

                    "LIMIT :limit"
    )
    LiveData<List<AuditCacheEntity>>
    observeFiltered(
            String actionType,
            String performedBy,
            long startInclusive,
            long endExclusive,
            int limit
    );


    @Query(
            "DELETE FROM audit_cache"
    )

    void clearCache();
}