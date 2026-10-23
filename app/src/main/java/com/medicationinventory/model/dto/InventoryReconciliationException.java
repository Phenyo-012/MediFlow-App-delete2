package com.medicationinventory.model.dto;



/**
 * Signals that a trusted inventory reconciliation request failed, retaining its HTTP status when available, backend error code, and retryability.
 */
public class InventoryReconciliationException
        extends Exception
{

    private final int httpStatus;
    private final String errorCode;
    private final boolean temporary;



    public InventoryReconciliationException(int httpStatus, String errorCode, String message,
                                            boolean temporary)
    {
        super(message);

        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.temporary = temporary;
    }


    public InventoryReconciliationException(String errorCode, String message, boolean temporary,
                                            Throwable cause)
    {
        super(message, cause);

        this.httpStatus = -1;
        this.errorCode = errorCode;
        this.temporary = temporary;
    }


    public int getHttpStatus()
    {
        return httpStatus;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public boolean isTemporary()
    {
        return temporary;
    }
}