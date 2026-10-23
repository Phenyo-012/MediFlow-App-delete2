package com.medicationinventory.service;

import com.medicationinventory.data.local.entity.InventoryOperationEntity;
import com.medicationinventory.data.local.entity.InventoryTransactionEntity;
import com.medicationinventory.data.local.entity.OperationAllocationEntity;
import com.medicationinventory.data.local.entity.SyncQueueEntity;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.IssueFefoDraft;

import com.medicationinventory.model.enums.InventoryOperationStatus;
import com.medicationinventory.model.enums.InventoryOperationType;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.sync.InventorySyncScheduler;

import com.medicationinventory.util.DeviceIdProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;



/**
 * Coordinates stock issue requests after the screen has prepared batch selections. It revalidates the draft, current Firebase account, active user profile, permissions, and current batch availability before atomically saving an operation, allocations, provisional movements, and sync work. Cloud confirmation is asynchronous; local persistence does not make the issue authoritative.
 */
public class IssueService
{
    private final AuthenticationRepository
            authenticationRepository;

    private final UserRepository
            userRepository;

    private final MedicineRepository
            medicineRepository;

    private final LocalStockOperationService
            localStockOperationService;

    private final DeviceIdProvider
            deviceIdProvider;

    private final InventorySyncScheduler
            inventorySyncScheduler;


    public IssueService(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            MedicineRepository medicineRepository,
            LocalStockOperationService localStockOperationService,
            DeviceIdProvider deviceIdProvider,
            InventorySyncScheduler inventorySyncScheduler)
    {
        this.authenticationRepository =
                authenticationRepository;

        this.userRepository =
                userRepository;

        this.medicineRepository =
                medicineRepository;

        this.localStockOperationService =
                localStockOperationService;

        this.deviceIdProvider =
                deviceIdProvider;

        this.inventorySyncScheduler =
                inventorySyncScheduler;
    }


    /**
     * Commits the draft using its prepared FEFO allocations after validating the
     * draft and rechecking current authorization and stock before local persistence.
     */
    public void commitDefaultFefoIssue(
            IssueFefoDraft draft,
            IssueCallback callback)
    {
        String validationError =
                validateDraft(
                        draft
                );


        if (validationError != null)
        {
            callback.onError(
                    validationError
            );

            return;
        }


        authorizeAndCommit(
                draft,
                draft.getAllocations(),
                null,
                false,
                callback
        );
    }


    /**
     * Commits a user-reviewed allocation that differs from FEFO. The override reason
     * is required and the selected quantities are validated before authorization.
     */
    public void commitOverrideFefoIssue(
            IssueFefoDraft draft,
            List<IssueFefoDraft.Allocation>
                    overrideAllocations,
            String overrideReason,
            IssueCallback callback)
    {
        String draftValidationError =
                validateDraft(
                        draft
                );


        if (draftValidationError != null)
        {
            callback.onError(
                    draftValidationError
            );

            return;
        }


        String normalizedOverrideReason =
                normalizeOptional(
                        overrideReason
                );


        if (normalizedOverrideReason == null)
        {
            callback.onError(
                    "An override reason is required."
            );

            return;
        }


        String overrideValidationError =
                validateOverrideAllocations(
                        draft,
                        overrideAllocations
                );


        if (overrideValidationError != null)
        {
            callback.onError(
                    overrideValidationError
            );

            return;
        }


        if (matchesDefaultFefo(
                draft.getAllocations(),
                overrideAllocations
        ))
        {
            callback.onError(
                    "Change at least one batch allocation before confirming a FEFO override."
            );

            return;
        }


        authorizeAndCommit(
                draft,
                overrideAllocations,
                normalizedOverrideReason,
                true,
                callback
        );
    }


    /**
     * Reports whether the current active user may create FEFO override issues.
     * This UI check is informational; the permission is rechecked during commit before local persistence.
     * @param callback receives true only when the current profile grants the override permission
     */
    public void checkOverridePermission(
            OverridePermissionCallback callback)
    {
        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null
                || uid.trim().isEmpty())
        {
            callback.onResult(
                    false
            );

            return;
        }


        userRepository.getUser(
                uid,
                userProfile ->
                {
                    boolean allowed =
                            userProfile != null
                                    &&
                                    userProfile.isActive()
                                    &&
                                    userProfile.can(
                                            Permission.CREATE_ISSUE
                                    )
                                    &&
                                    userProfile.can(
                                            Permission.OVERRIDE_FEFO
                                    );


                    callback.onResult(
                            allowed
                    );
                }
        );
    }


    private void authorizeAndCommit(
            IssueFefoDraft draft,
            List<IssueFefoDraft.Allocation>
                    selectedAllocations,
            String overrideReason,
            boolean fefoOverride,
            IssueCallback callback)
    {
        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null
                || uid.trim().isEmpty())
        {
            callback.onError(
                    "Authentication is required."
            );

            return;
        }


        medicineRepository.getMedicineById(
                draft.getMedicineId(),
                medicine ->
                {
                    if (medicine == null)
                    {
                        callback.onError(
                                "Medicine could not be found."
                        );

                        return;
                    }


                    if (!medicine.isActive())
                    {
                        callback.onError(
                                "Stock cannot be issued for an inactive medicine."
                        );

                        return;
                    }


                    userRepository.getUser(
                            uid,
                            userProfile ->
                            {
                                if (userProfile == null
                                        || !userProfile.isActive())
                                {
                                    callback.onError(
                                            "Your user profile is unavailable or inactive."
                                    );

                                    return;
                                }


                                if (!userProfile.can(
                                        Permission.CREATE_ISSUE
                                ))
                                {
                                    callback.onError(
                                            "You do not have permission to issue stock."
                                    );

                                    return;
                                }


                                if (fefoOverride
                                        &&
                                        !userProfile.can(
                                                Permission.OVERRIDE_FEFO
                                        ))
                                {
                                    callback.onError(
                                            "You do not have permission to override FEFO."
                                    );

                                    return;
                                }


                                String latestUid =
                                        authenticationRepository
                                                .getCurrentUserId();


                                if (latestUid == null
                                        ||
                                        !uid.equals(
                                                latestUid
                                        ))
                                {
                                    callback.onError(
                                            "Your authentication session changed. Please try again."
                                    );

                                    return;
                                }


                                commitIssue(
                                        draft,
                                        uid,
                                        selectedAllocations,
                                        overrideReason,
                                        fefoOverride,
                                        callback
                                );
                            }
                    );
                }
        );
    }


    private void commitIssue(
            IssueFefoDraft draft,
            String uid,
            List<IssueFefoDraft.Allocation>
                    selectedAllocations,
            String overrideReason,
            boolean fefoOverrideAuthorized,
            IssueCallback callback)
    {
        long clientCreatedAt =
                System.currentTimeMillis();


        String operationId =
                UUID.randomUUID()
                        .toString();


        String reason =
                normalizeOptional(
                        draft.getReason()
                );


        InventoryOperationEntity operation =
                new InventoryOperationEntity(
                        operationId,
                        InventoryOperationType.ISSUE,
                        draft.getMedicineId(),
                        draft.getRequestedQuantity(),
                        reason,
                        uid,
                        clientCreatedAt,
                        InventoryOperationStatus.LOCAL_PENDING,
                        null,

                        /*
                         * Separate from the ordinary issue
                         * reason/destination.
                         */
                        normalizeOptional(
                                overrideReason
                        ),

                        deviceIdProvider.getDeviceId(),
                        null,
                        null
                );


        List<OperationAllocationEntity> allocations =
                new ArrayList<>();


        List<InventoryTransactionEntity> transactions =
                new ArrayList<>();


        for (int index = 0;
             index < selectedAllocations.size();
             index++)
        {
            IssueFefoDraft.Allocation
                    selectedAllocation =
                    selectedAllocations.get(
                            index
                    );


            String allocationId =
                    operationId
                            + "_allocation_"
                            + index;


            String transactionId =
                    operationId
                            + "_issue_"
                            + index;


            OperationAllocationEntity allocation =
                    new OperationAllocationEntity(
                            allocationId,
                            operationId,
                            selectedAllocation.getBatchId(),
                            selectedAllocation.getAllocatedQuantity(),
                            index
                    );


            InventoryTransactionEntity transaction =
                    new InventoryTransactionEntity(
                            transactionId,
                            operationId,
                            draft.getMedicineId(),
                            selectedAllocation.getBatchId(),
                            "ISSUE",
                            -selectedAllocation
                                    .getAllocatedQuantity(),
                            reason,
                            uid,
                            clientCreatedAt,
                            0L,
                            true
                    );


            allocations.add(
                    allocation
            );


            transactions.add(
                    transaction
            );
        }


        SyncQueueEntity queueItem =
                new SyncQueueEntity(
                        operationId,
                        0,
                        clientCreatedAt,
                        0L,
                        null
                );


        localStockOperationService.commitIssue(
                operation,
                allocations,
                transactions,
                queueItem,
                fefoOverrideAuthorized,
                new LocalStockOperationService.CommitCallback()
                {
                    @Override
                    public void onSuccess()
                    {
                        inventorySyncScheduler
                                .schedulePendingSync();


                        callback.onSuccess(
                                new IssueCommitResult(
                                        operationId,
                                        InventoryOperationStatus.LOCAL_PENDING
                                )
                        );
                    }


                    @Override
                    public void onError(
                            Exception exception)
                    {
                        callback.onError(
                                friendlyCommitError(
                                        exception
                                )
                        );
                    }
                }
        );
    }


    private String validateDraft(
            IssueFefoDraft draft)
    {
        if (draft == null)
        {
            return "Issue information is unavailable.";
        }


        if (draft.getMedicineId() == null
                ||
                draft.getMedicineId()
                        .trim()
                        .isEmpty())
        {
            return "Medicine is required.";
        }


        if (draft.getRequestedQuantity() <= 0)
        {
            return "Requested quantity must be greater than zero.";
        }


        List<IssueFefoDraft.Allocation>
                allocations =
                draft.getAllocations();


        if (allocations == null
                || allocations.isEmpty())
        {
            return "The FEFO allocation is unavailable.";
        }


        Set<String> batchIds =
                new HashSet<>();


        long total =
                0;


        for (IssueFefoDraft.Allocation allocation :
                allocations)
        {
            if (allocation == null
                    ||
                    allocation.getBatchId() == null
                    ||
                    allocation.getBatchId()
                            .trim()
                            .isEmpty())
            {
                return "A FEFO allocation contains an invalid batch.";
            }


            if (allocation.getAllocatedQuantity() <= 0)
            {
                return "A FEFO allocation contains an invalid quantity.";
            }


            if (!batchIds.add(
                    allocation.getBatchId()
            ))
            {
                return "The same batch appears more than once in the FEFO allocation.";
            }


            total +=
                    allocation.getAllocatedQuantity();
        }


        if (total !=
                draft.getRequestedQuantity())
        {
            return "The FEFO allocation does not match the requested quantity.";
        }


        return null;
    }


    private String validateOverrideAllocations(
            IssueFefoDraft draft,
            List<IssueFefoDraft.Allocation>
                    overrideAllocations)
    {
        if (overrideAllocations == null
                || overrideAllocations.isEmpty())
        {
            return "At least one batch must be allocated.";
        }


        List<IssueFefoDraft.EligibleBatch>
                eligibleBatches =
                draft.getEligibleBatches();


        if (eligibleBatches == null
                || eligibleBatches.isEmpty())
        {
            return "Eligible batch information is unavailable. Return and calculate FEFO again.";
        }


        Map<String, IssueFefoDraft.EligibleBatch>
                eligibleById =
                new HashMap<>();


        for (IssueFefoDraft.EligibleBatch batch :
                eligibleBatches)
        {
            eligibleById.put(
                    batch.getBatchId(),
                    batch
            );
        }


        Set<String> batchIds =
                new HashSet<>();


        long total =
                0;


        for (IssueFefoDraft.Allocation allocation :
                overrideAllocations)
        {
            if (allocation == null
                    ||
                    allocation.getBatchId() == null
                    ||
                    allocation.getBatchId()
                            .trim()
                            .isEmpty())
            {
                return "An override allocation contains an invalid batch.";
            }


            if (!batchIds.add(
                    allocation.getBatchId()
            ))
            {
                return "The same batch cannot appear more than once.";
            }


            if (allocation.getAllocatedQuantity() <= 0)
            {
                return "Override quantities must be greater than zero.";
            }


            IssueFefoDraft.EligibleBatch
                    eligibleBatch =
                    eligibleById.get(
                            allocation.getBatchId()
                    );


            if (eligibleBatch == null)
            {
                return "A selected batch is no longer eligible for issuing.";
            }


            if (allocation.getAllocatedQuantity()
                    >
                    eligibleBatch.getAvailableQuantity())
            {
                return "An override allocation exceeds the locally available batch quantity.";
            }


            total +=
                    allocation.getAllocatedQuantity();
        }


        if (total !=
                draft.getRequestedQuantity())
        {
            return "Override allocations must total exactly "
                    + draft.getRequestedQuantity()
                    + ".";
        }


        return null;
    }


    private boolean matchesDefaultFefo(
            List<IssueFefoDraft.Allocation> defaultFefo,
            List<IssueFefoDraft.Allocation> overrideAllocations)
    {
        if (defaultFefo == null
                || overrideAllocations == null
                ||
                defaultFefo.size()
                        != overrideAllocations.size())
        {
            return false;
        }


        for (int index = 0;
             index < defaultFefo.size();
             index++)
        {
            IssueFefoDraft.Allocation
                    defaultAllocation =
                    defaultFefo.get(
                            index
                    );


            IssueFefoDraft.Allocation
                    overrideAllocation =
                    overrideAllocations.get(
                            index
                    );


            if (!defaultAllocation
                    .getBatchId()
                    .equals(
                            overrideAllocation
                                    .getBatchId()
                    ))
            {
                return false;
            }


            if (defaultAllocation
                    .getAllocatedQuantity()
                    !=
                    overrideAllocation
                            .getAllocatedQuantity())
            {
                return false;
            }
        }


        return true;
    }


    private String friendlyCommitError(
            Exception exception)
    {
        String message =
                exception == null
                        ? null
                        : exception.getMessage();


        if ("FEFO_CHANGED".equals(
                message
        ))
        {
            return "Available stock changed while you were reviewing the issue. "
                    + "Return and calculate the FEFO allocation again.";
        }


        if ("FEFO_OVERRIDE_REASON_REQUIRED".equals(
                message
        ))
        {
            return "An override reason is required.";
        }


        if ("FEFO_OVERRIDE_NOT_ALLOWED".equals(
                message
        ))
        {
            return "You do not have permission to override FEFO.";
        }


        if ("INVALID_BATCH".equals(
                message
        ))
        {
            return "One of the selected batches is no longer eligible for issuing.";
        }


        if ("INSUFFICIENT_STOCK".equals(
                message
        ))
        {
            return "Available stock changed and can no longer satisfy this issue.";
        }


        if ("NEGATIVE_STOCK".equals(
                message
        ))
        {
            return "The issue would result in negative stock and was not recorded.";
        }


        if ("BATCH_UPDATE_FAILED".equals(
                message
        ))
        {
            return "A batch changed while the issue was being recorded. Please try again.";
        }


        return "Unable to record the stock issue locally.";
    }


    private String normalizeOptional(
            String value)
    {
        if (value == null)
        {
            return null;
        }


        String normalized =
                value.trim();


        return normalized.isEmpty()
                ? null
                : normalized;
    }


    public interface IssueCallback
    {
        void onSuccess(
                IssueCommitResult result
        );

        void onError(
                String message
        );
    }


    public interface OverridePermissionCallback
    {
        void onResult(
                boolean allowed
        );
    }


    public static class IssueCommitResult
    {
        private final String operationId;

        private final InventoryOperationStatus status;


        public IssueCommitResult(
                String operationId,
                InventoryOperationStatus status)
        {
            this.operationId =
                    operationId;

            this.status =
                    status;
        }


        public String getOperationId()
        {
            return operationId;
        }


        public InventoryOperationStatus getStatus()
        {
            return status;
        }
    }
}