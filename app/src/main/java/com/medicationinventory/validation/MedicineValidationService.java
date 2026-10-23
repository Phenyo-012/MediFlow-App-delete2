package com.medicationinventory.validation;

import java.util.Locale;



/**
 * Validates Medicine Validation Service input and returns field-level outcomes used by the UI.
 */
public final class MedicineValidationService
{

    private MedicineValidationService()
    {
        // Prevent instantiation
    }

    /**
     * Normalizes the medicine form and evaluates required text and stock thresholds.
     * All field errors are returned together so the caller can keep the entered values.
     */
    public static ValidationResult validate(
            String medicineCode,
            String medicineName,
            String dosageForm,
            String unitOfMeasure,
            String reorderLevelText,
            String targetStockLevelText)
    {

        ValidationResult result = new ValidationResult();

        String code = medicineCode == null ? "" : medicineCode.trim();

        String name = medicineName == null ? "" : medicineName.trim();

        String form = dosageForm == null ? "" : dosageForm.trim();

        String unit = unitOfMeasure == null ? "" : unitOfMeasure.trim();

        if (code.isEmpty())
        {
            result.medicineCodeError = "Medicine code is required.";
        }

        if (name.isEmpty())
        {
            result.medicineNameError = "Medicine name is required.";
        }

        if (form.isEmpty())
        {
            result.dosageFormError = "Dosage form is required.";
        }

        if (unit.isEmpty())
        {
            result.unitOfMeasureError = "Unit of measure is required.";
        }

        Integer reorderLevel = parseNonNegativeInteger(reorderLevelText);

        if (reorderLevel == null)
        {
            result.reorderLevelError =
                    "Reorder level must be a whole number of 0 or greater.";
        }

        Integer targetStockLevel = parseNonNegativeInteger(targetStockLevelText);

        if (targetStockLevel == null)
        {
            result.targetStockLevelError =
                    "Target stock level must be a whole number of 0 or greater.";
        }

        if (reorderLevel != null
                && targetStockLevel != null && targetStockLevel < reorderLevel)
        {

            result.targetStockLevelError =
                    "Target stock level must be greater than or equal to the reorder level.";
        }

        result.valid = result.medicineCodeError == null
                && result.medicineNameError == null
                && result.dosageFormError == null
                && result.unitOfMeasureError == null
                && result.reorderLevelError == null
                && result.targetStockLevelError == null;

        if (result.valid)
        {
            result.normalizedMedicineCode = normalizeMedicineCode(code);
            result.normalizedMedicineName = name;
            result.normalizedDosageForm = form;
            result.normalizedUnitOfMeasure = unit;
            result.reorderLevel = reorderLevel;
            result.targetStockLevel = targetStockLevel;
        }

        return result;
    }

    public static String normalizeMedicineCode(String medicineCode)
    {
        if (medicineCode == null)
        {
            return "";
        }

        return medicineCode.trim().toUpperCase(Locale.ROOT);
    }

    private static Integer parseNonNegativeInteger(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }

        try
        {

            int number = Integer.parseInt(value.trim());

            if (number < 0)
            {
                return null;
            }

            return number;

        }
        catch (NumberFormatException exception)
        {
            return null;
        }
    }

    public static class ValidationResult
    {
        private boolean valid;
        private String medicineCodeError;
        private String medicineNameError;
        private String dosageFormError;
        private String unitOfMeasureError;
        private String reorderLevelError;
        private String targetStockLevelError;
        private String normalizedMedicineCode;
        private String normalizedMedicineName;
        private String normalizedDosageForm;
        private String normalizedUnitOfMeasure;
        private Integer reorderLevel;
        private Integer targetStockLevel;
        public boolean isValid()
        {
            return valid;
        }
        public String getMedicineCodeError()
        {
            return medicineCodeError;
        }
        public String getMedicineNameError()
        {
            return medicineNameError;
        }
        public String getDosageFormError()
        {
            return dosageFormError;
        }
        public String getUnitOfMeasureError()
        {
            return unitOfMeasureError;
        }
        public String getReorderLevelError()
        {
            return reorderLevelError;
        }
        public String getTargetStockLevelError()
        {
            return targetStockLevelError;
        }
        public String getNormalizedMedicineCode()
        {
            return normalizedMedicineCode;
        }
        public String getNormalizedMedicineName()
        {
            return normalizedMedicineName;
        }
        public String getNormalizedDosageForm()
        {
            return normalizedDosageForm;
        }
        public String getNormalizedUnitOfMeasure()
        {
            return normalizedUnitOfMeasure;
        }
        public Integer getReorderLevel()
        {
            return reorderLevel;
        }
        public Integer getTargetStockLevel()
        {
            return targetStockLevel;
        }
    }
}