package com.medicationinventory.model.dto;



/**
 * Reports a trusted inventory processor failure so callers can distinguish backend rejection from temporary processing or transport errors.
 */
public class TrustedProcessorException
        extends Exception {

    private final int httpStatus;
    private final String errorCode;
    private final boolean temporary;


    public TrustedProcessorException(
            int httpStatus,
            String errorCode,
            String message,
            boolean temporary
    ) {

        super(message);

        this.httpStatus =
                httpStatus;

        this.errorCode =
                errorCode;

        this.temporary =
                temporary;
    }

    public TrustedProcessorException(
            String errorCode,
            String message,
            boolean temporary,
            Throwable cause
    ) {

        super(
                message,
                cause
        );

        this.httpStatus =
                -1;

        this.errorCode =
                errorCode;

        this.temporary =
                temporary;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public boolean isTemporary() {
        return temporary;
    }
}