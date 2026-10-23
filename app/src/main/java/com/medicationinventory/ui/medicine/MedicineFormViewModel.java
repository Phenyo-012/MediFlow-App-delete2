package com.medicationinventory.ui.medicine;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.MedicineCategoryCatalog;
import com.medicationinventory.model.domain.UserProfile;

import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.validation.MedicineValidationService;

import java.util.List;
import java.util.UUID;



/**
 * Owns add/edit form state for a medicine and mediates validation, account permissions, and the
 * shared-master save workflow. Existing records are loaded for editing; new records are validated
 * locally before the repository submits them to the trusted medicine service. The UI receives
 * explicit validation, in-progress, saved, and rejected states so a backend rejection is not
 * presented as a successful catalog update.
 */
public class MedicineFormViewModel
        extends ViewModel
{
    private final AuthenticationRepository
            authenticationRepository;


    private final UserRepository
            userRepository;


    private final MedicineRepository
            medicineRepository;


    private final LiveData<List<String>>
            activeCategories;


    private final MutableLiveData<MedicineFormState>
            formState =
            new MutableLiveData<>(
                    MedicineFormState.idle()
            );


    private final MutableLiveData<Medicine>
            medicineToEdit =
            new MutableLiveData<>();


    private boolean
            submissionInProgress = false;

    private String pendingNewMedicineId;
    private boolean saveOutcomeUncertain;


    public MedicineFormViewModel(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MedicineRepository medicineRepository)
    {
        this.authenticationRepository =
                authenticationRepository;


        this.userRepository =
                userRepository;


        this.medicineRepository =
                medicineRepository;


        activeCategories =
                medicineRepository
                        .observeActiveCategories();
    }


    public LiveData<MedicineFormState>
    getFormState()
    {
        return formState;
    }


    public LiveData<Medicine>
    getMedicineToEdit()
    {
        return medicineToEdit;
    }


    public LiveData<List<String>>
    getActiveCategories()
    {
        return activeCategories;
    }



    public void loadMedicine(
            String medicineId)
    {
        if (medicineId == null
                ||
                medicineId.trim().isEmpty())
        {
            formState.setValue(
                    MedicineFormState.error(
                            "Medicine ID is required."
                    )
            );


            return;
        }


        medicineRepository
                .getMedicineById(
                        medicineId,
                        medicine ->
                        {
                            if (medicine == null)
                            {
                                formState.setValue(
                                        MedicineFormState.error(
                                                "Medicine could not be found."
                                        )
                                );


                                return;
                            }


                            medicineToEdit.setValue(
                                    medicine
                            );
                        }
                );
    }


    /**
     * Validates the submitted fields and starts the authorized shared-medicine save path. While a
     * request is in flight, duplicate saves are suppressed; completion updates the form state only
     * through the repository callback, which can distinguish authoritative acceptance from
     * rejection.
     *
     * @param existingMedicineId identifier when editing, or {@code null} for a new record
     * @param medicineCode medicine identifier entered in the form; leading zeroes are preserved
     * @param medicineName display name
     * @param genericName generic name, when supplied
     * @param category selected medicine category
     * @param strength medicine strength text
     * @param dosageForm dosage form label
     * @param unitOfMeasure unit used for stock quantities
     * @param reorderLevelText threshold that triggers a reorder recommendation
     * @param targetStockLevelText desired stock level used to calculate the recommendation quantity
     */
    public void saveMedicine(
            String existingMedicineId,
            String medicineCode,
            String medicineName,
            String genericName,
            String category,
            String strength,
            String dosageForm,
            String unitOfMeasure,
            String reorderLevelText,
            String targetStockLevelText)
    {
        if (submissionInProgress || saveOutcomeUncertain)
        {
            return;
        }


        String normalizedCategory =
                normalizeCategory(
                        category
                );


        if (normalizedCategory == null)
        {
            formState.setValue(
                    MedicineFormState.error(
                            "Medicine category is required."
                    )
            );


            return;
        }


        if (MedicineCategoryCatalog
                .OTHER
                .equalsIgnoreCase(
                        normalizedCategory
                ))
        {
            formState.setValue(
                    MedicineFormState.error(
                            "Please provide the new medicine category."
                    )
            );


            return;
        }


        MedicineValidationService.ValidationResult
                validation =
                MedicineValidationService.validate(
                        medicineCode,
                        medicineName,
                        dosageForm,
                        unitOfMeasure,
                        reorderLevelText,
                        targetStockLevelText
                );


        if (!validation.isValid())
        {
            formState.setValue(
                    MedicineFormState
                            .validationError(
                                    validation
                            )
            );


            return;
        }


        boolean editing =
                existingMedicineId != null
                        &&
                        !existingMedicineId
                                .trim()
                                .isEmpty();


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null
                ||
                uid.trim().isEmpty())
        {
            formState.setValue(
                    MedicineFormState.error(
                            "Authentication is required."
                    )
            );


            return;
        }


        submissionInProgress =
                true;


        formState.setValue(
                MedicineFormState.loading()
        );


        userRepository
                .getUser(
                        uid,
                        userProfile ->
                                checkPermissionAndSave(
                                        userProfile,
                                        editing,
                                        existingMedicineId,
                                        validation,
                                        genericName,
                                        normalizedCategory,
                                        strength
                                )
                );
    }


    private void checkPermissionAndSave(
            UserProfile userProfile,
            boolean editing,
            String existingMedicineId,
            MedicineValidationService.ValidationResult validation,
            String genericName,
            String category,
            String strength)
    {
        if (userProfile == null
                ||
                !userProfile.isActive())
        {
            finishWithError(
                    "Your user profile is unavailable or inactive."
            );


            return;
        }


        Permission requiredPermission =
                editing
                        ? Permission.UPDATE_MEDICINE
                        : Permission.CREATE_MEDICINE;


        if (!userProfile.can(
                requiredPermission
        ))
        {
            finishWithError(
                    editing
                            ? "You do not have permission to update medicines."
                            : "You do not have permission to create medicines."
            );


            return;
        }

        medicineRepository
                .getMedicineByCode(
                        validation
                                .getNormalizedMedicineCode(),

                        existingByCode ->
                                handleDuplicateCheck(
                                        existingByCode,
                                        editing,
                                        existingMedicineId,
                                        validation,
                                        genericName,
                                        category,
                                        strength
                                )
                );
    }


    private void handleDuplicateCheck(
            Medicine existingByCode,
            boolean editing,
            String existingMedicineId,
            MedicineValidationService.ValidationResult validation,
            String genericName,
            String category,
            String strength)
    {
        if (existingByCode != null)
        {
            boolean belongsToMedicineBeingEdited =
                    editing
                            &&
                            existingMedicineId
                                    .equals(
                                            existingByCode
                                                    .getMedicineId()
                                    );


            if (!belongsToMedicineBeingEdited)
            {
                submissionInProgress =
                        false;


                formState.setValue(
                        MedicineFormState
                                .duplicateCode(
                                        "A medicine with this code already exists."
                                )
                );


                return;
            }
        }


        if (editing)
        {
            medicineRepository
                    .getMedicineById(
                            existingMedicineId,
                            originalMedicine ->
                            {
                                if (originalMedicine == null)
                                {
                                    finishWithError(
                                            "Medicine could not be found."
                                    );


                                    return;
                                }


                                persistMedicine(
                                        originalMedicine,
                                        validation,
                                        genericName,
                                        category,
                                        strength
                                );
                            }
                    );
        }
        else
        {
            persistMedicine(
                    null,
                    validation,
                    genericName,
                    category,
                    strength
            );
        }
    }



    private void persistMedicine(
            Medicine originalMedicine,
            MedicineValidationService.ValidationResult validation,
            String genericName,
            String category,
            String strength)
    {
        boolean editing =
                originalMedicine != null;


        Medicine medicine =
                new Medicine(
                        /*
                         * Medicine ID
                         */
                        editing
                                ? originalMedicine
                                .getMedicineId()
                                : getOrCreateNewMedicineId(),

                        /*
                         * Medicine code
                         */
                        validation
                                .getNormalizedMedicineCode(),

                        /*
                         * Medicine name
                         */
                        validation
                                .getNormalizedMedicineName(),

                        /*
                         * Generic name
                         */
                        normalizeOptionalText(
                                genericName
                        ),

                        /*
                         * Medicine category
                         */
                        category,

                        /*
                         * Strength
                         */
                        normalizeOptionalText(
                                strength
                        ),

                        /*
                         * Dosage form
                         */
                        validation
                                .getNormalizedDosageForm(),

                        /*
                         * Unit of measure
                         */
                        validation
                                .getNormalizedUnitOfMeasure(),

                        /*
                         * Reorder level
                         */
                        validation
                                .getReorderLevel(),

                        /*
                         * Target stock level
                         */
                        validation
                                .getTargetStockLevel(),

                        /*
                         * Active state
                         */
                        editing
                                ? originalMedicine
                                .isActive()
                                : true,

                        /*
                         * Existing sync metadata is
                         * preserved during local edits.
                         */
                        editing
                                ? originalMedicine
                                .getUpdatedAt()
                                : 0L,

                        editing
                                ? originalMedicine
                                .getSyncVersion()
                                : 0L
                );


        medicineRepository.saveSharedMedicine(medicine, editing,
                new MedicineRepository.SharedMedicineSaveCallback() {
                    @Override

                    public void onSaved(Medicine authoritative) {
                        submissionInProgress = false;
                        pendingNewMedicineId = null;
                        formState.setValue(MedicineFormState.success(authoritative));
                    }

                    @Override

                    public void onRejected(String message) {
                        finishWithError(message);
                    }

                    @Override

                    public void onOutcomeUncertain(String message) {
                        submissionInProgress = false;
                        saveOutcomeUncertain = true;
                        formState.setValue(MedicineFormState.cloudOutcomeUncertain(message));
                    }
                });
    }

    private String getOrCreateNewMedicineId() {
        if (pendingNewMedicineId == null) {
            pendingNewMedicineId = UUID.randomUUID().toString();
        }
        return pendingNewMedicineId;
    }


    /*
     * NORMALISATION
     */

    private String normalizeCategory(
            String value)
    {
        if (value == null)
        {
            return null;
        }

        String normalized =
                value
                        .trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        if (normalized.isEmpty())
        {
            return null;
        }


        return normalized;
    }


    private String normalizeOptionalText(
            String value)
    {
        if (value == null)
        {
            return null;
        }


        String trimmed =
                value.trim();


        return trimmed.isEmpty()
                ? null
                : trimmed;
    }


    /*
     * ERROR HANDLING
     */
    private void finishWithError(
            String message)
    {
        submissionInProgress =
                false;


        formState.setValue(
                MedicineFormState.error(
                        message
                )
        );
    }


    /*
     * FORM STATE
     */
    public static class MedicineFormState
    {
        public enum Status
        {
            IDLE,
            LOADING,
            VALIDATION_ERROR,
            DUPLICATE_CODE,
            SUCCESS,
            CLOUD_OUTCOME_UNCERTAIN,
            ERROR
        }


        private final Status
                status;


        private final MedicineValidationService
                .ValidationResult
                validationResult;


        private final Medicine
                medicine;


        private final String
                message;


        private MedicineFormState(
                Status status,
                MedicineValidationService.ValidationResult validationResult,
                Medicine medicine,
                String message)
        {
            this.status =
                    status;


            this.validationResult =
                    validationResult;


            this.medicine =
                    medicine;


            this.message =
                    message;
        }


        public static MedicineFormState idle()
        {
            return new MedicineFormState(
                    Status.IDLE,
                    null,
                    null,
                    null
            );
        }

        public static MedicineFormState loading()
        {
            return new MedicineFormState(
                    Status.LOADING,
                    null,
                    null,
                    null
            );
        }


        public static MedicineFormState
        validationError(
                MedicineValidationService.ValidationResult result)
        {
            return new MedicineFormState(
                    Status.VALIDATION_ERROR,
                    result,
                    null,
                    null
            );
        }


        public static MedicineFormState
        duplicateCode(
                String message)
        {
            return new MedicineFormState(
                    Status.DUPLICATE_CODE,
                    null,
                    null,
                    message
            );
        }


        public static MedicineFormState
        success(
                Medicine medicine)
        {
            return new MedicineFormState(
                    Status.SUCCESS,
                    null,
                    medicine,
                    null
            );
        }


        public static MedicineFormState cloudOutcomeUncertain(String message) {
            return new MedicineFormState(Status.CLOUD_OUTCOME_UNCERTAIN,
                    null, null, message);
        }

        public static MedicineFormState
        error(
                String message)
        {
            return new MedicineFormState(
                    Status.ERROR,
                    null,
                    null,
                    message
            );
        }


        public Status getStatus()
        {
            return status;
        }


        public MedicineValidationService
                .ValidationResult
        getValidationResult()
        {
            return validationResult;
        }


        public Medicine getMedicine()
        {
            return medicine;
        }


        public String getMessage()
        {
            return message;
        }
    }
}