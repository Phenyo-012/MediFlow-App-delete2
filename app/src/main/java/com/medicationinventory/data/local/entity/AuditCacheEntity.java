package com.medicationinventory.data.local.entity;

import androidx.annotation.NonNull;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;



/**
 * Caches auditable server events for the audit-log screen. Action, entity, actor, timestamp, and
 * serialized details preserve the fields presented to administrators; this cache supports offline
 * viewing and is distinct from the authoritative Firestore audit record.
 */
@Entity(
        tableName = "audit_cache",
        indices = {
                @Index("performedAt"),
                @Index("actionType")
        }
)
public class AuditCacheEntity
{
    @PrimaryKey
    @NonNull
    private String auditId;


    @NonNull
    private String actionType;


    private String entityType;

    private String entityId;


    @NonNull
    private String performedBy;


    private long performedAt;


    @NonNull
    private String detailsJson;


    public AuditCacheEntity(
            @NonNull String auditId,
            @NonNull String actionType,
            String entityType,
            String entityId,
            @NonNull String performedBy,
            long performedAt,
            @NonNull String detailsJson)
    {
        this.auditId =
                auditId;

        this.actionType =
                actionType;

        this.entityType =
                entityType;

        this.entityId =
                entityId;

        this.performedBy =
                performedBy;

        this.performedAt =
                performedAt;

        this.detailsJson =
                detailsJson;
    }


    @NonNull
    public String getAuditId()
    {
        return auditId;
    }


    public void setAuditId(
            @NonNull String auditId)
    {
        this.auditId =
                auditId;
    }


    @NonNull
    public String getActionType()
    {
        return actionType;
    }


    public void setActionType(
            @NonNull String actionType)
    {
        this.actionType =
                actionType;
    }


    public String getEntityType()
    {
        return entityType;
    }


    public void setEntityType(
            String entityType)
    {
        this.entityType =
                entityType;
    }


    public String getEntityId()
    {
        return entityId;
    }


    public void setEntityId(
            String entityId)
    {
        this.entityId =
                entityId;
    }


    @NonNull
    public String getPerformedBy()
    {
        return performedBy;
    }


    public void setPerformedBy(
            @NonNull String performedBy)
    {
        this.performedBy =
                performedBy;
    }


    public long getPerformedAt()
    {
        return performedAt;
    }


    public void setPerformedAt(
            long performedAt)
    {
        this.performedAt =
                performedAt;
    }


    @NonNull
    public String getDetailsJson()
    {
        return detailsJson;
    }


    public void setDetailsJson(
            @NonNull String detailsJson)
    {
        this.detailsJson =
                detailsJson;
    }
}