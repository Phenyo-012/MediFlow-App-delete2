package com.medicationinventory.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.firestore.FirebaseFirestoreException;
import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.data.remote.backend.TrustedInventoryProcessorDataSource;
import com.medicationinventory.data.remote.backend.TrustedInventoryReconciliationDataSource;
import com.medicationinventory.data.repository.InventorySyncRepository;
import com.medicationinventory.data.repository.InventoryReconciliationRepository;
import com.medicationinventory.model.dto.InventoryReconciliationException;
import com.medicationinventory.model.dto.InventoryReconciliationResult;
import com.medicationinventory.model.dto.InventoryOperationRemoteResult;
import com.medicationinventory.model.dto.TrustedProcessorException;
import com.medicationinventory.model.dto.TrustedProcessorResult;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;



/**
 * Drains due operation IDs from the local queue, verifies the signed-in owner before each network phase, submits operation payloads, asks the trusted processor for authoritative results, and reconciles terminal outcomes into Room. Temporary failures receive delayed retries; permanent validation or permission failures remain visible for attention or owner-requested retry. Account changes and reconciliation failures are handled without marking unconfirmed stock as synced.
 */
public class InventorySyncWorker
        extends Worker
{

    private static final String TAG =
            "InventorySyncWorker";

    private static final long UPLOAD_TIMEOUT_SECONDS =
            45L;

    private static final long INITIAL_RETRY_DELAY_MS =
            30_000L;

    private static final long MAX_RETRY_DELAY_MS =
            30L * 60L * 1000L;


    public InventorySyncWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams
    ) {

        super(
                appContext,
                workerParams
        );
    }


    @NonNull
    @Override

    /**
     * Uploads due local operations, then reconciles submitted work that may have completed
     * while this device was offline. Work is abandoned safely when the captured session changes.
     *
     * @return WorkManager outcome indicating completion, retryable failure, or terminal failure
     */
    public Result doWork() {

        Log.d(
                TAG,
                "Worker started. attempt="
                        + getRunAttemptCount()
        );


        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplicationContext();


        InventorySyncRepository syncRepository =
                application
                        .getAppContainer()
                        .getInventorySyncRepository();



        final String workerUid = syncRepository.getCurrentUserIdForWorker();
        if (workerUid == null || workerUid.trim().isEmpty())
        {
            return Result.success();
        }

        TrustedInventoryProcessorDataSource
                trustedProcessorDataSource =
                application
                        .getAppContainer()
                        .getTrustedInventoryProcessorDataSource();

        TrustedInventoryReconciliationDataSource
                reconciliationDataSource =
                application
                        .getAppContainer()
                        .getTrustedInventoryReconciliationDataSource();


        InventoryReconciliationRepository
                reconciliationRepository =
                application
                        .getAppContainer()
                        .getInventoryReconciliationRepository();


        long now =
                System.currentTimeMillis();


        final List<String> pendingOperationIds;

        try {

            pendingOperationIds =
                    syncRepository
                            .getDueLocalPendingOperationIdsForWorker(
                                    now
                            );

        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Failed to query LOCAL_PENDING operations.",
                    exception
            );

            return Result.failure();
        }


        Log.d(
                TAG,
                "LOCAL_PENDING count="
                        + (
                        pendingOperationIds == null
                                ? -1
                                : pendingOperationIds.size()
                )
        );


        if (pendingOperationIds != null) {

            for (String operationId :
                    pendingOperationIds) {

                if (!isSameSession(syncRepository, workerUid))
                {
                    return Result.success();
                }
                if (!syncRepository.isOwnedBySessionForWorker(
                        operationId, workerUid))
                {

                    continue;
                }

                Log.d(
                        TAG,
                        "Uploading LOCAL_PENDING operation "
                                + operationId
                );


                UploadOutcome outcome =
                        uploadAndWait(
                                syncRepository,
                                operationId
                        );

                if (!isSameSession(syncRepository, workerUid))
                {
                    return Result.success();
                }

                Log.d(
                        TAG,
                        "Upload outcome="
                                + outcome.type
                );


                if (outcome.type
                        == UploadOutcome.Type.SUBMITTED) {


                    continue;
                }


                if (outcome.type
                        == UploadOutcome.Type.REMOTE_TERMINAL) {


                    continue;
                }


                Exception exception =
                        outcome.exception;


                if (isTemporaryFailure(
                        exception
                )) {

                    Log.w(
                            TAG,
                            "Temporary Firestore upload failure for "
                                    + operationId
                    );


                    recordTemporaryFailure(
                            syncRepository,
                            operationId,
                            exception
                    );


                    return Result.retry();
                }


                Log.e(
                        TAG,
                        "Permanent Firestore upload failure for "
                                + operationId,
                        exception
                );


                recordPermanentAttentionState(
                        syncRepository,
                        operationId,
                        exception
                );


                return Result.failure();
            }
        }



        final List<String> submittedOperationIds;

        try {

            submittedOperationIds =
                    syncRepository
                            .getDueSubmittedOperationIdsForWorker(
                                    System.currentTimeMillis()
                            );

        } catch (Exception exception) {

            Log.e(
                    TAG,
                    "Failed to query SUBMITTED operations.",
                    exception
            );

            return Result.failure();
        }


        Log.d(
                TAG,
                "SUBMITTED count="
                        + (
                        submittedOperationIds == null
                                ? -1
                                : submittedOperationIds.size()
                )
        );


        if (submittedOperationIds == null
                || submittedOperationIds.isEmpty()) {

            Log.d(
                    TAG,
                    "No SUBMITTED operations are due."
            );

            return Result.success();
        }


        for (String operationId :
                submittedOperationIds) {

            if (!isSameSession(syncRepository, workerUid))
            {
                return Result.success();
            }
            if (!syncRepository.isOwnedBySessionForWorker(
                    operationId, workerUid))
            {
                continue;
            }

            Log.d(
                    TAG,
                    "Invoking trusted processor for "
                            + operationId
            );


            ProcessorOutcome processorOutcome =
                    processAndWait(
                            trustedProcessorDataSource,
                            operationId
                    );

            if (!isSameSession(syncRepository, workerUid))
            {

                return Result.success();
            }

            Log.d(
                    TAG,
                    "Processor outcome="
                            + processorOutcome.type
            );


            if (processorOutcome.type
                    == ProcessorOutcome.Type.TERMINAL) {

                TrustedProcessorResult processorResult =
                        processorOutcome.result;


                if (processorResult != null) {

                    Log.d(
                            TAG,
                            "Trusted processor returned status="
                                    + processorResult.getStatus()
                                    + ", code="
                                    + processorResult.getResultCode()
                    );
                }


                Log.d(
                        TAG,
                        "Fetching authoritative reconciliation for "
                                + operationId
                );


                if (!syncRepository.isOwnedBySessionForWorker(
                        operationId, workerUid))
                {
                    return Result.success();
                }

                ReconciliationOutcome reconciliationOutcome =
                        reconcileAndWait(
                                reconciliationDataSource,
                                reconciliationRepository,
                                syncRepository,
                                operationId,
                                workerUid
                        );

                if (reconciliationOutcome.type
                        == ReconciliationOutcome.Type.SESSION_CHANGED)
                {
                    return Result.success();
                }

                if (reconciliationOutcome.type
                        == ReconciliationOutcome.Type.SUCCESS) {

                    Log.d(
                            TAG,
                            "Room reconciliation completed for "
                                    + operationId
                    );



                    continue;
                }


                InventoryReconciliationException
                        reconciliationException =
                        reconciliationOutcome.exception;


                if (reconciliationException != null
                        && reconciliationException.isTemporary()) {

                    Log.w(
                            TAG,
                            "Temporary reconciliation failure. code="
                                    + reconciliationException
                                    .getErrorCode()
                    );


                    recordTemporaryReconciliationFailure(
                            syncRepository,
                            operationId,
                            reconciliationException
                    );


                    return Result.retry();
                }


                if (reconciliationOutcome.localException != null) {

                    Log.e(
                            TAG,
                            "Local Room reconciliation failed.",
                            reconciliationOutcome.localException
                    );



                    recordLocalReconciliationFailure(
                            syncRepository,
                            operationId
                    );


                    return Result.retry();
                }


                Log.e(
                        TAG,
                        "Permanent reconciliation failure."
                );


                recordPermanentReconciliationFailure(
                        syncRepository,
                        operationId,
                        reconciliationException
                );


                return Result.failure();
            }


            TrustedProcessorException exception =
                    processorOutcome.exception;


            if (exception != null) {

                Log.e(
                        TAG,
                        "Processor error code="
                                + exception.getErrorCode()
                                + ", temporary="
                                + exception.isTemporary()
                                + ", httpStatus="
                                + exception.getHttpStatus()
                );
            }


            if (exception != null
                    && exception.isTemporary()) {

                recordTemporaryProcessorFailure(
                        syncRepository,
                        operationId,
                        exception
                );


                return Result.retry();
            }


            recordPermanentProcessorFailure(
                    syncRepository,
                    operationId,
                    exception
            );


            return Result.failure();
        }


        Log.d(
                TAG,
                "Worker completed successfully."
        );


        return Result.success();
    }


    /**
     * Confirms that this worker is still running for the UID whose queue it
     * captured. Checking both WorkManager cancellation and the current Firebase
     * UID prevents delayed callbacks from a previous account from updating its
     * sync state.
     *
     * @param repository sync repository used to read the current session UID
     * @param uid UID captured when this worker began
     * @return whether the worker may continue acting for that account
     */
    private boolean isSameSession(
            InventorySyncRepository repository, String uid)
    {
        return !isStopped()
                && uid != null
                && uid.equals(repository.getCurrentUserIdForWorker());
    }


    /**
     * Adapts the repository's asynchronous upload callback to the worker's
     * synchronous control flow. A successful callback distinguishes mere
     * submission from an already-known terminal remote state; timeout,
     * interruption, and missing callback results remain failures so an upload is
     * never mistaken for processor confirmation.
     *
     * @param syncRepository owner-scoped inventory sync repository
     * @param operationId stable ID of the queued operation
     * @return submitted, terminal, or error outcome for the caller's next phase
     */
    private UploadOutcome uploadAndWait(
            InventorySyncRepository syncRepository,
            String operationId
    ) {

        CountDownLatch latch =
                new CountDownLatch(
                        1
                );


        AtomicReference<UploadOutcome> outcome =
                new AtomicReference<>();


        syncRepository.uploadPendingOperation(
                operationId,
                new InventorySyncRepository.UploadCallback() {

                    @Override

                    public void onSubmitted(
                            InventoryOperationRemoteResult result
                    ) {

                        outcome.set(
                                UploadOutcome.submitted()
                        );

                        latch.countDown();
                    }


                    @Override

                    public void onRemoteTerminalState(
                            InventoryOperationRemoteResult result
                    ) {

                        outcome.set(
                                UploadOutcome.remoteTerminal()
                        );

                        latch.countDown();
                    }


                    @Override
                    public void onError(
                            Exception exception
                    ) {

                        outcome.set(
                                UploadOutcome.error(
                                        exception
                                )
                        );

                        latch.countDown();
                    }
                }
        );


        try {

            boolean completed =
                    latch.await(
                            UPLOAD_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );


            if (!completed) {

                return UploadOutcome.error(
                        new TimeoutException(
                                "Inventory operation upload timed out."
                        )
                );
            }

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();


            return UploadOutcome.error(
                    exception
            );
        }


        UploadOutcome result =
                outcome.get();


        if (result == null) {

            return UploadOutcome.error(
                    new IllegalStateException(
                            "Inventory upload completed without a result."
                    )
            );
        }


        return result;
    }


    /**
     * Waits for trusted server processing after upload. The callback supplies the
     * backend's terminal result; a timeout or interruption is wrapped as a
     * retryable processor failure so local stock remains pending until the
     * reconciliation path confirms the authoritative outcome.
     *
     * @param dataSource authenticated trusted processor client
     * @param operationId operation to process on the backend
     * @return terminal processor result or a classified processing failure
     */
    private ProcessorOutcome processAndWait(
            TrustedInventoryProcessorDataSource dataSource,
            String operationId
    ) {

        CountDownLatch latch =
                new CountDownLatch(
                        1
                );


        AtomicReference<ProcessorOutcome> outcome =
                new AtomicReference<>();


        dataSource.processOperation(
                operationId,
                new TrustedInventoryProcessorDataSource.Callback() {

                    @Override
                    public void onSuccess(
                            TrustedProcessorResult result
                    ) {

                        Log.d(
                                TAG,
                                "Trusted processor callback success for "
                                        + operationId
                        );


                        outcome.set(
                                ProcessorOutcome.terminal(
                                        result
                                )
                        );


                        latch.countDown();
                    }


                    @Override
                    public void onError(
                            TrustedProcessorException exception
                    ) {

                        Log.e(
                                TAG,
                                "Trusted processor callback error for "
                                        + operationId
                        );


                        outcome.set(
                                ProcessorOutcome.error(
                                        exception
                                )
                        );


                        latch.countDown();
                    }
                }
        );


        try {

            boolean completed =
                    latch.await(
                            UPLOAD_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );


            if (!completed) {

                Log.e(
                        TAG,
                        "Trusted processor timed out for "
                                + operationId
                );


                return ProcessorOutcome.error(
                        new TrustedProcessorException(
                                "PROCESSOR_TIMEOUT",
                                "Trusted inventory processing timed out.",
                                true,
                                new TimeoutException(
                                        "Trusted processor timeout."
                                )
                        )
                );
            }

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();


            return ProcessorOutcome.error(
                    new TrustedProcessorException(
                            "PROCESSOR_INTERRUPTED",
                            "Trusted inventory processing was interrupted.",
                            true,
                            exception
                    )
            );
        }


        ProcessorOutcome result =
                outcome.get();


        if (result == null) {

            return ProcessorOutcome.error(
                    new TrustedProcessorException(
                            "PROCESSOR_NO_RESULT",
                            "Trusted inventory processing returned no result.",
                            true,
                            null
                    )
            );
        }


        return result;
    }


    private boolean isTemporaryFailure(
            Exception exception
    ) {

        if (exception == null) {
            return true;
        }


        if (exception instanceof TimeoutException) {
            return true;
        }


        if (!(exception
                instanceof FirebaseFirestoreException)) {

            return false;
        }


        FirebaseFirestoreException firestoreException =
                (FirebaseFirestoreException)
                        exception;


        switch (firestoreException.getCode()) {

            case ABORTED:
            case CANCELLED:
            case DEADLINE_EXCEEDED:
            case INTERNAL:
            case RESOURCE_EXHAUSTED:
            case UNAVAILABLE:
            case UNKNOWN:
                return true;


            case ALREADY_EXISTS:

                /*
                 * Normally handled by our
                 * create-if-absent transport.
                 */
                return true;


            default:
                return false;
        }
    }


    private void recordTemporaryFailure(InventorySyncRepository syncRepository,
                                        String operationId, Exception exception)
    {
        long now = System.currentTimeMillis();


        long delay = calculateRetryDelay();


        try
        {

            syncRepository.recordTemporaryFailureForWorker(operationId, now,
                    now + delay, errorCodeFor(exception));

        }
        catch (Exception ignored)
        {
            Log.w(TAG, "Could not record temporary Firestore retry metadata.");
        }
    }


    private void recordPermanentAttentionState(InventorySyncRepository syncRepository,
                                               String operationId, Exception exception)
    {
        long now = System.currentTimeMillis();


        try
        {

            syncRepository
                    .recordTemporaryFailureForWorker(operationId, now, Long.MAX_VALUE,
                            errorCodeFor(exception));

        }
        catch (Exception ignored)
        {
            Log.w(TAG, "Could not record permanent Firestore attention state.");
        }
    }


    private void recordTemporaryProcessorFailure(InventorySyncRepository syncRepository,
                                                 String operationId, TrustedProcessorException exception)
    {

        long now = System.currentTimeMillis();


        long delay = calculateRetryDelay();


        try
        {
            syncRepository.recordTemporaryFailureForWorker(operationId, now, now
                    + delay, processorErrorCode(exception));

        }
        catch (Exception ignored)
        {
            Log.w(TAG, "Could not record trusted processor retry metadata.");
        }
    }


    private void recordPermanentProcessorFailure(InventorySyncRepository syncRepository,
                                                 String operationId, TrustedProcessorException exception)
    {
        long now = System.currentTimeMillis();


        try
        {
            syncRepository.recordTemporaryFailureForWorker(operationId, now, Long.MAX_VALUE,
                    processorErrorCode(exception));

        }
        catch (Exception ignored)
        {
            Log.w(TAG, "Could not record trusted processor attention state.");
        }
    }


    private long calculateRetryDelay()
    {
        int exponent = Math.min(getRunAttemptCount(), 6);


        long multiplier = 1L << exponent;


        return Math.min(INITIAL_RETRY_DELAY_MS * multiplier, MAX_RETRY_DELAY_MS);
    }


    private String errorCodeFor(Exception exception)
    {

        if (exception == null)
        {
            return "SYNC_UNKNOWN";
        }


        if (exception instanceof TimeoutException)
        {
            return "NETWORK_TIMEOUT";
        }


        if (exception instanceof FirebaseFirestoreException)
        {
            FirebaseFirestoreException firestoreException = (FirebaseFirestoreException)
                    exception;


            return "FIRESTORE_" + firestoreException.getCode().name();
        }


        return "LOCAL_SYNC_ERROR";
    }


    private String processorErrorCode(TrustedProcessorException exception)
    {
        if (exception == null || exception.getErrorCode() == null
                || exception.getErrorCode().trim().isEmpty())
        {

            return "TRUSTED_PROCESSOR_ERROR";
        }


        return exception
                .getErrorCode();
    }

    /**
     * Fetches the server's reconciliation snapshot and applies it to Room only
     * if the worker still belongs to the same signed-in user. This second UID
     * check is necessary because the network callback may arrive after an account
     * switch; local reconciliation errors are kept distinct from remote lookup
     * failures so retry policy can respond appropriately.
     *
     * @param dataSource trusted endpoint client for terminal operation results
     * @param repository applies the result to the local inventory ledger
     * @param syncRepository checks that the original account is still active
     * @param operationId stable ID to reconcile
     * @param workerUid UID captured by the worker
     * @return success, account-change, local-apply, or remote-fetch outcome
     */
    private ReconciliationOutcome reconcileAndWait(
            TrustedInventoryReconciliationDataSource dataSource,
            InventoryReconciliationRepository repository,
            InventorySyncRepository syncRepository,
            String operationId,
            String workerUid
    ) {

        CountDownLatch latch =
                new CountDownLatch(
                        1
                );


        AtomicReference<ReconciliationOutcome> outcome =
                new AtomicReference<>();


        dataSource.fetchReconciliation(
                operationId,
                new TrustedInventoryReconciliationDataSource.Callback() {

                    @Override
                    public void onSuccess(
                            InventoryReconciliationResult result
                    ) {

                        try {

                            if (!isSameSession(syncRepository, workerUid))
                            {
                                outcome.set(
                                        ReconciliationOutcome.sessionChanged());
                                latch.countDown();
                                return;
                            }


                            repository.reconcile(
                                    result
                            );


                            outcome.set(
                                    ReconciliationOutcome.success()
                            );

                        } catch (Exception exception) {

                            outcome.set(
                                    ReconciliationOutcome.localError(
                                            exception
                                    )
                            );
                        }


                        latch.countDown();
                    }


                    @Override
                    public void onError(
                            InventoryReconciliationException exception
                    ) {

                        outcome.set(
                                ReconciliationOutcome.remoteError(
                                        exception
                                )
                        );


                        latch.countDown();
                    }
                }
        );


        try {

            boolean completed =
                    latch.await(
                            UPLOAD_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );


            if (!completed) {

                return ReconciliationOutcome.remoteError(
                        new InventoryReconciliationException(
                                "RECONCILIATION_TIMEOUT",
                                "Inventory reconciliation timed out.",
                                true,
                                new TimeoutException(
                                        "Reconciliation timeout."
                                )
                        )
                );
            }

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();


            return ReconciliationOutcome.remoteError(
                    new InventoryReconciliationException(
                            "RECONCILIATION_INTERRUPTED",
                            "Inventory reconciliation was interrupted.",
                            true,
                            exception
                    )
            );
        }


        ReconciliationOutcome result =
                outcome.get();


        if (result == null) {

            return ReconciliationOutcome.remoteError(
                    new InventoryReconciliationException(
                            "RECONCILIATION_NO_RESULT",
                            "Inventory reconciliation returned no result.",
                            true,
                            null
                    )
            );
        }


        return result;
    }


    private void recordTemporaryReconciliationFailure(
            InventorySyncRepository syncRepository,
            String operationId,
            InventoryReconciliationException exception
    ) {

        long now =
                System.currentTimeMillis();


        try {

            syncRepository
                    .recordTemporaryFailureForWorker(
                            operationId,
                            now,
                            now + calculateRetryDelay(),
                            reconciliationErrorCode(
                                    exception
                            )
                    );

        } catch (Exception ignored) {

            Log.w(
                    TAG,
                    "Could not record reconciliation retry metadata."
            );
        }
    }

    private void recordLocalReconciliationFailure(
            InventorySyncRepository syncRepository,
            String operationId
    ) {

        long now =
                System.currentTimeMillis();


        try {

            syncRepository
                    .recordTemporaryFailureForWorker(
                            operationId,
                            now,
                            now + calculateRetryDelay(),
                            "LOCAL_RECONCILIATION_ERROR"
                    );

        } catch (Exception ignored) {

            Log.w(
                    TAG,
                    "Could not record local reconciliation retry."
            );
        }
    }

    private void recordPermanentReconciliationFailure(
            InventorySyncRepository syncRepository,
            String operationId,
            InventoryReconciliationException exception
    ) {

        long now =
                System.currentTimeMillis();


        try {

            syncRepository
                    .recordTemporaryFailureForWorker(
                            operationId,
                            now,
                            Long.MAX_VALUE,
                            reconciliationErrorCode(
                                    exception
                            )
                    );

        } catch (Exception ignored) {

            Log.w(
                    TAG,
                    "Could not record reconciliation attention state."
            );
        }
    }

    private String reconciliationErrorCode(
            InventoryReconciliationException exception
    ) {

        if (exception == null
                || exception.getErrorCode() == null
                || exception
                .getErrorCode()
                .trim()
                .isEmpty()) {

            return "RECONCILIATION_ERROR";
        }


        return exception
                .getErrorCode();
    }

    private static class UploadOutcome
    {
        enum Type
        {
            SUBMITTED,
            REMOTE_TERMINAL,
            ERROR
        }


        private final Type type;

        private final Exception exception;


        private UploadOutcome(Type type, Exception exception)
        {
            this.type = type;

            this.exception = exception;
        }


        static UploadOutcome submitted()
        {
            return new UploadOutcome(Type.SUBMITTED, null);
        }


        static UploadOutcome remoteTerminal()
        {
            return new UploadOutcome(Type.REMOTE_TERMINAL, null);
        }


        static UploadOutcome error(Exception exception)
        {

            return new UploadOutcome(Type.ERROR, exception);
        }
    }

    private static class ProcessorOutcome {

        enum Type {
            TERMINAL,
            ERROR
        }


        private final Type type;

        private final TrustedProcessorResult result;

        private final TrustedProcessorException exception;


        private ProcessorOutcome(
                Type type,
                TrustedProcessorResult result,
                TrustedProcessorException exception
        ) {

            this.type =
                    type;

            this.result =
                    result;

            this.exception =
                    exception;
        }


        static ProcessorOutcome terminal(
                TrustedProcessorResult result
        ) {

            return new ProcessorOutcome(
                    Type.TERMINAL,
                    result,
                    null
            );
        }


        static ProcessorOutcome error(
                TrustedProcessorException exception
        ) {

            return new ProcessorOutcome(
                    Type.ERROR,
                    null,
                    exception
            );
        }
    }

    private static class ReconciliationOutcome {

        enum Type {
            SUCCESS,
            REMOTE_ERROR,
            LOCAL_ERROR,
            SESSION_CHANGED
        }


        private final Type type;

        private final InventoryReconciliationException
                exception;

        private final Exception
                localException;



        private ReconciliationOutcome(
                Type type,
                InventoryReconciliationException exception,
                Exception localException
        ) {

            this.type =
                    type;

            this.exception =
                    exception;

            this.localException =
                    localException;
        }


        static ReconciliationOutcome sessionChanged() {
            return new ReconciliationOutcome(
                    Type.SESSION_CHANGED, null, null);
        }

        static ReconciliationOutcome success() {

            return new ReconciliationOutcome(
                    Type.SUCCESS,
                    null,
                    null
            );
        }


        static ReconciliationOutcome remoteError(
                InventoryReconciliationException exception
        ) {

            return new ReconciliationOutcome(
                    Type.REMOTE_ERROR,
                    exception,
                    null
            );
        }


        static ReconciliationOutcome localError(
                Exception exception
        ) {

            return new ReconciliationOutcome(
                    Type.LOCAL_ERROR,
                    null,
                    exception
            );
        }
    }
}
