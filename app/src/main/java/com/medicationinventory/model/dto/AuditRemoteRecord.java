package com.medicationinventory.model.dto;



/**
 * Carries Audit Remote Record data across the Android and trusted-backend boundary.
 */
public class AuditRemoteRecord
{
    private final String auditId;
    private final String actionType;

    private final String entityType;
    private final String entityId;

    private final String performedBy;

    private final long performedAt;

    private final String detailsJson;



    public AuditRemoteRecord(
            String auditId,
            String actionType,
            String entityType,
            String entityId,
            String performedBy,
            long performedAt,
            String detailsJson)
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


    public String getAuditId()
    {
        return auditId;
    }


    public String getActionType()
    {
        return actionType;
    }


    public String getEntityType()
    {
        return entityType;
    }


    public String getEntityId()
    {
        return entityId;
    }


    public String getPerformedBy()
    {
        return performedBy;
    }


    public long getPerformedAt()
    {
        return performedAt;
    }


    public String getDetailsJson()
    {
        return detailsJson;
    }
}