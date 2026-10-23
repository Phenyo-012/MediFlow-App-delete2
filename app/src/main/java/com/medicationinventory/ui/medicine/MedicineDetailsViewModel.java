package com.medicationinventory.ui.medicine;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.MediatorLiveData;

import com.medicationinventory.data.repository.UserRepository;
import com.medicationinventory.data.repository.BatchRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.InventorySettingsRepository;
import com.medicationinventory.data.repository.MedicineClassificationRepository;

import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.model.domain.Batch;
import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.domain.InventorySettings;
import com.medicationinventory.model.domain.ReorderRecommendation;
import com.medicationinventory.model.domain.MedicineClassification;

import com.medicationinventory.service.ReorderRecommendationService;

import java.util.List;
import java.util.Locale;
import java.util.Collections;

/**
 * Implements the Medicine Details View Model behavior for its Android screen and the application state it presents.
 */
public class MedicineDetailsViewModel
        extends ViewModel
{

    private final AuthenticationRepository authenticationRepository;
    private final UserRepository userRepository;
    private final MedicineRepository medicineRepository;
    private final LiveData<Medicine> medicine;
    private final LiveData<UserProfile> userProfile;
    private final LiveData<List<Batch>> batches;
    private final LiveData<MedicineClassification> rawMedicineClassification;
    private final MediatorLiveData<MedicineClassification> medicineClassification =
            new MediatorLiveData<>();
    private MedicineClassification latestMedicineClassification;
    private UserProfile latestUserProfile;
    private final MedicineClassificationRepository medicineClassificationRepository;
    private final String medicineId;
    private final ReorderRecommendationService reorderRecommendationService;
    private int nearExpiryWarningDays =
            InventorySettings.DEFAULT_NEAR_EXPIRY_WARNING_DAYS;
    private final MediatorLiveData<ReorderRecommendation> reorderRecommendation =
            new MediatorLiveData<>();
    private Medicine latestMedicine;
    private List<Batch> latestBatches = Collections.emptyList();
    private final MutableLiveData<ActionState> actionState =
            new MutableLiveData<>(ActionState.idle());
    private final MutableLiveData<ClassificationActionState> classificationActionState =
            new MutableLiveData<>(ClassificationActionState.idle());
    private boolean classificationMutationInProgress =
            false;
    private boolean deactivationInProgress = false;
    private boolean classificationRefreshInProgress = false;

    public MedicineDetailsViewModel(
            String medicineId,
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MedicineRepository medicineRepository,
            BatchRepository batchRepository,
            MedicineClassificationRepository medicineClassificationRepository,
            ReorderRecommendationService reorderRecommendationService,
            InventorySettingsRepository inventorySettingsRepository)
    {
        this.medicineId = medicineId;

        this.authenticationRepository = authenticationRepository;

        this.userRepository = userRepository;

        this.medicineRepository = medicineRepository;

        this.reorderRecommendationService = reorderRecommendationService;

        this.medicineClassificationRepository = medicineClassificationRepository;

        if (inventorySettingsRepository == null)
        {
            throw new IllegalArgumentException(
                    "InventorySettingsRepository is required."
            );
        }

        this.nearExpiryWarningDays = nearExpiryWarningDays;

        medicine = medicineRepository.observeMedicine(medicineId);
        batches = batchRepository.observeBatchesForMedicine(medicineId);
        rawMedicineClassification = medicineClassificationRepository.observeForMedicine(medicineId);


        reorderRecommendation.addSource(
                medicine,
                value ->
                {
                    latestMedicine =
                            value;

                    recalculateReorderRecommendation();
                }
        );


        reorderRecommendation.addSource(
                batches,
                value ->
                {
                    latestBatches =
                            value == null
                                    ? Collections.emptyList()
                                    : value;

                    recalculateReorderRecommendation();
                }
        );

        reorderRecommendation.addSource(
                inventorySettingsRepository
                        .observeInventorySettings(),
                settings ->
                {
                    nearExpiryWarningDays =
                            settings == null
                                    ? InventorySettings
                                      .DEFAULT_NEAR_EXPIRY_WARNING_DAYS
                                    : settings
                                    .getNearExpiryWarningDays();


                    recalculateReorderRecommendation();
                }
        );

        String uid = authenticationRepository.getCurrentUserId();

        if (uid == null)
        {
            userProfile = new MutableLiveData<>(null);
        }
        else
        {

            userProfile = userRepository.observeUser(uid);
        }

        medicineClassification.addSource(
                rawMedicineClassification,
                classification ->
                {
                    latestMedicineClassification =
                            classification;

                    updateVisibleMedicineClassification();
                }
        );


        medicineClassification.addSource(
                medicineClassificationRepository.observeRestrictedAccess(),
                ignored -> updateVisibleMedicineClassification());

        medicineClassification.addSource(
                userProfile,
                profile ->
                {
                    latestUserProfile =
                            profile;

                    updateVisibleMedicineClassification();
                }
        );

    }
    public LiveData<Medicine> getMedicine()
    {
        return medicine;
    }
    public LiveData<UserProfile> getUserProfile()
    {
        return userProfile;
    }
    public LiveData<ActionState> getActionState()
    {
        return actionState;
    }
    public LiveData<List<Batch>> getBatches()
    {
        return batches;
    }
    public LiveData<ReorderRecommendation> getReorderRecommendation()
    {
        return reorderRecommendation;
    }
    public LiveData<MedicineClassification> getMedicineClassification()
    {
        return medicineClassification;
    }
    public LiveData<ClassificationActionState> getClassificationActionState()
    {
        return classificationActionState;
    }

    private void updateVisibleMedicineClassification()
    {
        boolean authorised =
                latestUserProfile != null
                        &&
                        latestUserProfile.isActive()
                        &&
                        latestUserProfile.can(
                                Permission.VIEW_ABC_VED
                        )
                        && medicineClassificationRepository.isRestrictedAccessGranted();


        if (!authorised)
        {
            medicineClassification.setValue(
                    null
            );

            return;
        }


        medicineClassification.setValue(
                latestMedicineClassification
        );
    }

    public void refreshMedicineClassification()
    {
        if (classificationRefreshInProgress)
        {
            return;
        }


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null || !medicineClassificationRepository.isRestrictedAccessGranted())
        {
            return;
        }


        classificationRefreshInProgress =
                true;


        userRepository.getUser(
                uid,
                profile ->
                {
                    if (profile == null
                            ||
                            !profile.isActive()
                            ||
                            !profile.can(
                                    Permission.VIEW_ABC_VED
                            ))
                    {
                        classificationRefreshInProgress =
                                false;

                        return;
                    }


                    medicineClassificationRepository
                            .refreshFromServer(
                                    medicineId,
                                    new MedicineClassificationRepository
                                            .RefreshCallback()
                                    {
                                        @Override

                                        public void onSuccess(
                                                MedicineClassification classification)
                                        {
                                            classificationRefreshInProgress =
                                                    false;
                                        }


                                        @Override

                                        public void onError(
                                                Exception exception)
                                        {
                                            classificationRefreshInProgress =
                                                    false;
                                        }
                                    }
                            );
                }
        );
    }

    public void saveMedicineClassification(
            String abcClassification,
            String vedClassification)
    {
        if (classificationMutationInProgress)
        {
            return;
        }


        String cleanAbc =
                normalizeAbc(
                        abcClassification
                );


        if (cleanAbc == null)
        {
            classificationActionState.setValue(
                    ClassificationActionState.error(
                            "ABC classification must be A, B or C."
                    )
            );

            return;
        }


        String cleanVed =
                normalizeVed(
                        vedClassification
                );


        if (cleanVed == null)
        {
            classificationActionState.setValue(
                    ClassificationActionState.error(
                            "VED classification must be V, E or D."
                    )
            );

            return;
        }


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null)
        {
            classificationActionState.setValue(
                    ClassificationActionState.error(
                            "Authentication is required."
                    )
            );

            return;
        }


        classificationMutationInProgress =
                true;


        classificationActionState.setValue(
                ClassificationActionState.loading()
        );



        userRepository.getUser(
                uid,
                userProfile ->
                {
                    if (userProfile == null
                            ||
                            !userProfile.isActive())
                    {
                        finishClassificationWithError(
                                "Your user profile is unavailable or inactive."
                        );

                        return;
                    }


                    if (!userProfile.can(
                            Permission.VIEW_ABC_VED
                    ))
                    {
                        finishClassificationWithError(
                                "You do not have permission to view ABC-VED classifications."
                        );

                        return;
                    }


                    if (!userProfile.can(
                            Permission.MANAGE_ABC_VED
                    ))
                    {
                        finishClassificationWithError(
                                "You do not have permission to manage ABC-VED classifications."
                        );

                        return;
                    }



                    if (latestMedicine == null)
                    {
                        finishClassificationWithError(
                                "Medicine details are unavailable."
                        );

                        return;
                    }


                    if (!latestMedicine.isActive())
                    {
                        finishClassificationWithError(
                                "ABC-VED classification cannot be changed for an inactive medicine."
                        );

                        return;
                    }


                    performClassificationMutation(
                            cleanAbc,
                            cleanVed
                    );
                }
        );
    }

    private void performClassificationMutation(
            String abcClassification,
            String vedClassification)
    {
        medicineClassificationRepository
                .setClassification(
                        medicineId,
                        abcClassification,
                        vedClassification,
                        new MedicineClassificationRepository
                                .TrustedMutationCallback()
                        {
                            @Override
                            public void onSuccess(
                                    boolean changed,
                                    boolean created,
                                    MedicineClassification classification)
                            {
                                classificationMutationInProgress =
                                        false;


                                classificationActionState.setValue(
                                        ClassificationActionState.success(
                                                changed,
                                                created
                                        )
                                );
                            }


                            @Override

                            public void onCommittedRefreshError(
                                    boolean changed,
                                    boolean created,
                                    Exception exception)
                            {
                                classificationMutationInProgress =
                                        false;


                                classificationActionState.setValue(
                                        ClassificationActionState
                                                .committedRefreshError(
                                                        changed,
                                                        created,
                                                        "ABC-VED classification was saved, "
                                                                + "but the latest server copy "
                                                                + "could not be refreshed."
                                                )
                                );
                            }


                            @Override
                            public void onError(
                                    Exception exception)
                            {
                                String message =
                                        exception == null
                                                ||
                                                exception.getMessage() == null
                                                ||
                                                exception.getMessage()
                                                        .trim()
                                                        .isEmpty()
                                                ? "Unable to save the ABC-VED classification."
                                                : exception
                                                .getMessage()
                                                .trim();


                                finishClassificationWithError(
                                        message
                                );
                            }
                        }
                );
    }

    private void finishClassificationWithError(
            String message)
    {
        classificationMutationInProgress =
                false;


        classificationActionState.setValue(
                ClassificationActionState.error(
                        message
                )
        );
    }

    private String normalizeAbc(
            String value)
    {
        if (value == null)
        {
            return null;
        }


        String normalized =
                value
                        .trim()
                        .toUpperCase(Locale.ROOT);


        if (!normalized.equals("A")
                &&
                !normalized.equals("B")
                &&
                !normalized.equals("C"))
        {
            return null;
        }


        return normalized;
    }

    private String normalizeVed(
            String value)
    {
        if (value == null)
        {
            return null;
        }


        String normalized =
                value
                        .trim()
                        .toUpperCase(Locale.ROOT);


        if (!normalized.equals("V")
                &&
                !normalized.equals("E")
                &&
                !normalized.equals("D"))
        {
            return null;
        }


        return normalized;
    }

    public void clearClassificationActionState()
    {
        if (classificationMutationInProgress)
        {
            return;
        }


        classificationActionState.setValue(
                ClassificationActionState.idle()
        );
    }
    public static class ClassificationActionState
    {
        public enum Status
        {
            IDLE,
            LOADING,
            SUCCESS,
            COMMITTED_REFRESH_ERROR,
            ERROR
        }


        private final Status status;

        private final String message;

        private final boolean changed;

        private final boolean created;



        private ClassificationActionState(
                Status status,
                String message,
                boolean changed,
                boolean created)
        {
            this.status =
                    status;

            this.message =
                    message;

            this.changed =
                    changed;

            this.created =
                    created;
        }


        public static ClassificationActionState idle()
        {
            return new ClassificationActionState(
                    Status.IDLE,
                    null,
                    false,
                    false
            );
        }


        public static ClassificationActionState loading()
        {
            return new ClassificationActionState(
                    Status.LOADING,
                    null,
                    false,
                    false
            );
        }

        public static ClassificationActionState success(
                boolean changed,
                boolean created)
        {
            return new ClassificationActionState(
                    Status.SUCCESS,
                    null,
                    changed,
                    created
            );
        }


        public static ClassificationActionState
        committedRefreshError(
                boolean changed,
                boolean created,
                String message)
        {
            return new ClassificationActionState(
                    Status.COMMITTED_REFRESH_ERROR,
                    message,
                    changed,
                    created
            );
        }


        public static ClassificationActionState error(
                String message)
        {
            return new ClassificationActionState(
                    Status.ERROR,
                    message,
                    false,
                    false
            );
        }


        public Status getStatus()
        {
            return status;
        }


        public String getMessage()
        {
            return message;
        }


        public boolean isChanged()
        {
            return changed;
        }


        public boolean isCreated()
        {
            return created;
        }
    }


    private void recalculateReorderRecommendation()
    {
        if (latestMedicine == null)
        {
            reorderRecommendation.setValue(
                    null
            );

            return;
        }


        try
        {
            ReorderRecommendation result =
                    reorderRecommendationService
                            .buildReorderRecommendation(
                                    latestMedicine,
                                    latestBatches,
                                    nearExpiryWarningDays
                            );


            reorderRecommendation.setValue(
                    result
            );
        }
        catch (IllegalArgumentException
               | IllegalStateException exception)
        {

            reorderRecommendation.setValue(
                    null
            );
        }
    }

    public void deactivateMedicine(String medicineId)
    {
        if (deactivationInProgress)
        {
            return;
        }

        if (medicineId == null || medicineId.trim().isEmpty())
        {
            actionState.setValue(ActionState.error("Medicine ID is required."));

            return;
        }

        String uid = authenticationRepository.getCurrentUserId();

        if (uid == null)
        {
            actionState.setValue(ActionState.error("Authentication is required."));

            return;
        }

        deactivationInProgress = true;

        actionState.setValue(ActionState.loading());

        userRepository.getUser(uid, userProfile ->
                {
                    if (userProfile == null || !userProfile.isActive())
                    {
                        finishWithError("Your user profile is unavailable or inactive.");

                        return;
                    }

                    if (!userProfile.can(Permission.DEACTIVATE_MEDICINE))
                    {
                        finishWithError("You do not have permission to deactivate medicines.");

                        return;
                    }

                    verifyMedicineAndDeactivate(medicineId);
                }
        );
    }


    private void verifyMedicineAndDeactivate(String medicineId)
    {
        medicineRepository.getMedicineById(medicineId, medicine ->
                {
                    if (medicine == null)
                    {
                        finishWithError("Medicine could not be found.");

                        return;
                    }

                    if (!medicine.isActive())
                    {
                        finishWithError("This medicine is already inactive.");

                        return;
                    }

                    performDeactivation(medicineId);
                }
        );
    }


    private void performDeactivation(String medicineId)
    {
        medicineRepository.deactivateMedicine(
                medicineId,
                new MedicineRepository.OperationCallback()
                {
                    @Override
                    public void onSuccess()
                    {
                        deactivationInProgress = false;

                        actionState.setValue(ActionState.success());
                    }

                    @Override
                    public void onError(Exception exception)
                    {
                        finishWithError(exception.getMessage() == null ?
                                "Unable to deactivate medicine." : exception.getMessage());
                    }
                }
        );
    }


    private void finishWithError(String message)
    {
        deactivationInProgress = false;

        actionState.setValue(ActionState.error(message));
    }
    public static class ActionState
    {
        public enum Status
        {
            IDLE, LOADING, SUCCESS, ERROR
        }
        private final Status status;
        private final String message;


        private ActionState(Status status, String message)
        {
            this.status = status;
            this.message = message;
        }

         // Keeps the idle decision in one place, rather than repeating it in several callers.
        public static ActionState idle()
        {
            return new ActionState(Status.IDLE, null);
        }

         // Looks up ing in the available data source and returns it in the form this layer expects.
        public static ActionState loading()
        {
            return new ActionState(Status.LOADING, null);
        }

         // Keeps the success decision in one place, rather than repeating it in several callers.
        public static ActionState success()
        {
            return new ActionState(Status.SUCCESS, null);
        }

         // Keeps the error decision in one place, rather than repeating it in several callers.
        public static ActionState error(String message)
        {
            return new ActionState(Status.ERROR, message);
        }
        public Status getStatus()
        {
            return status;
        }
        public String getMessage()
        {
            return message;
        }
    }
}
