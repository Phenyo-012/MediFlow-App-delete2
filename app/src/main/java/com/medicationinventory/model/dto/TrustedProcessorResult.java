package com.medicationinventory.model.dto;

import com.medicationinventory.model.enums.InventoryOperationStatus;



/**
 * Carries Trusted Processor Result data across the Android and trusted-backend boundary.
 */
public class TrustedProcessorResult
{
    private final String operationId;

    private final InventoryOperationStatus status;

    private final String resultCode;
    private final String resultMessage;



    public TrustedProcessorResult(String operationId, InventoryOperationStatus status,
                                  String resultCode, String resultMessage)
    {
        this.operationId = operationId;

        this.status = status;

        this.resultCode = resultCode;

        this.resultMessage = resultMessage;
    }

    public String getOperationId()
    {
        return operationId;
    }

    public InventoryOperationStatus getStatus()
    {
        return status;
    }

    public String getResultCode()
    {
        return resultCode;
    }

    public String getResultMessage()
    {
        return resultMessage;
    }

}