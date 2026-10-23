package com.medicationinventory.validation;



/**
 * Validates Receipt Validation Service input and returns field-level outcomes used by the UI.
 */
public final class ReceiptValidationService
{

    private ReceiptValidationService()
    {
        // Prevent instantiation
    }


    /**
     * Checks receipt identity, positive whole-number quantity, and date ordering.
     * The normalized batch number is returned even when other fields are invalid.
     */
    public static ValidationResult validate(String batchNumber, String quantityText,
                                            long receivedDate, long expiryDate)
    {
        String normalizedBatchNumber = batchNumber == null ? "" : batchNumber.trim();
        String batchNumberError = null;String quantityError = null;String receivedDateError = null;
        String expiryDateError = null;

        int quantity = 0;

        if (normalizedBatchNumber.isEmpty())
        {
            batchNumberError = "Batch number is required.";
        }

        if (quantityText == null || quantityText.trim().isEmpty())
        {
            quantityError = "Quantity received is required.";
        }
        else
        {
            try
            {
                quantity = Integer.parseInt(quantityText.trim());

                if (quantity <= 0)
                {
                    quantityError = "Quantity received must be greater than zero.";
                }

            }
            catch (NumberFormatException exception)
            {
                quantityError = "Quantity received must be a valid whole number.";
            }
        }

        if (receivedDate <= 0L)
        {
            receivedDateError = "Received date is required.";
        }

        if (expiryDate <= 0L)
        {
            expiryDateError = "Expiry date is required.";
        }
        else if (receivedDate > 0L && expiryDate <= receivedDate)
        {
            expiryDateError = "Expiry date must be after the received date.";
        }

        boolean valid = batchNumberError == null && quantityError == null
                && receivedDateError == null && expiryDateError == null;

        return new ValidationResult(valid, normalizedBatchNumber, quantity, receivedDate,
                expiryDate, batchNumberError, quantityError, receivedDateError, expiryDateError
        );
    }

    public static class ValidationResult
    {
        private final boolean valid;

        private final String normalizedBatchNumber;
        private final int quantity;
        private final long receivedDate;
        private final long expiryDate;

        private final String batchNumberError;
        private final String quantityError;
        private final String receivedDateError;
        private final String expiryDateError;


        private ValidationResult(boolean valid, String normalizedBatchNumber, int quantity,
                                 long receivedDate, long expiryDate, String batchNumberError, String quantityError,
                                 String receivedDateError, String expiryDateError
        )
        {
            this.valid = valid;

            this.normalizedBatchNumber = normalizedBatchNumber;

            this.quantity = quantity;

            this.receivedDate = receivedDate;

            this.expiryDate = expiryDate;

            this.batchNumberError = batchNumberError;

            this.quantityError = quantityError;

            this.receivedDateError = receivedDateError;

            this.expiryDateError = expiryDateError;
        }
        public boolean isValid()
        {
            return valid;
        }
        public String getNormalizedBatchNumber()
        {
            return normalizedBatchNumber;
        }
        public int getQuantity()
        {
            return quantity;
        }
        public long getReceivedDate()
        {
            return receivedDate;
        }
        public long getExpiryDate()
        {
            return expiryDate;
        }
        public String getBatchNumberError()
        {
            return batchNumberError;
        }
        public String getQuantityError()
        {
            return quantityError;
        }
        public String getReceivedDateError()
        {
            return receivedDateError;
        }
        public String getExpiryDateError()
        {
            return expiryDateError;
        }
    }
}