package com.medicationinventory.data.remote.firestore;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.medicationinventory.model.dto.InventoryOperationRemoteResult;
import com.medicationinventory.model.dto.InventoryOperationUploadDto;
import com.medicationinventory.model.enums.InventoryOperationStatus;

import java.util.Map;


/**
 * Reads or writes Firestore Inventory Operation Data Source data in Firestore and translates asynchronous results for app callers.
 */
public class FirestoreInventoryOperationDataSource
{
    private static final String COLLECTION_OPERATIONS = "inventoryOperations";

    private final FirebaseFirestore firestore;

    public FirestoreInventoryOperationDataSource(FirebaseFirestore firestore)
    {
        this.firestore = firestore;
    }

    /**
     * Writes the client operation envelope to Firestore for the trusted processor to validate.
     * A successful write means submitted, not that shared stock has been confirmed.
     */
    public void submitOperation(InventoryOperationUploadDto upload, UploadCallback callback)
    {

        if (upload == null)
        {
            callback.onError(new IllegalArgumentException("Operation upload cannot be null."));

            return;
        }

        String operationId = upload.getOperationId();

        if (operationId == null || operationId.trim().isEmpty())
        {

            callback.onError(new IllegalArgumentException("Operation ID is required."));

            return;
        }

        DocumentReference document =
                firestore.collection(COLLECTION_OPERATIONS).document(operationId);

        Map<String, Object> payload =
                upload.toFirestoreMap();

        document.set(payload).addOnSuccessListener(unused -> {callback.onSuccess(
                new InventoryOperationRemoteResult(operationId,
                        InventoryOperationStatus.SUBMITTED,
                        null, null));}
        ).addOnFailureListener(createError -> readExistingOperation(document,
                operationId, createError, callback));
    }

    private void readExistingOperation(DocumentReference document, String operationId,
                                       Exception originalCreateError, UploadCallback callback)
    {
        document.get(Source.SERVER).addOnSuccessListener(
                        snapshot ->
                        {

                            if (!snapshot.exists())
                            {
                                callback.onError(originalCreateError);

                                return;
                            }

                            try
                            {
                                InventoryOperationRemoteResult result =
                                        mapExistingResult(snapshot, operationId);

                                callback.onSuccess(result);

                            }
                            catch (Exception mappingError)
                            {
                                callback.onError(mappingError);
                            }
                        }
                )
                .addOnFailureListener(
                        readError ->
                        {

                            callback.onError(originalCreateError);
                        }
                );
    }

    private InventoryOperationRemoteResult
    mapExistingResult(DocumentSnapshot snapshot, String expectedOperationId)
    {
        String remoteOperationId = snapshot.getString("operationId");

        if (remoteOperationId == null || !expectedOperationId.equals(remoteOperationId))
        {
            throw new IllegalStateException(
                    "Remote operation ID does not match the local operation.");
        }

        String statusValue = snapshot.getString("status");

        if (statusValue == null)
        {
            throw new IllegalStateException("Remote operation has no status.");
        }

        InventoryOperationStatus status;

        try
        {
            status = InventoryOperationStatus.valueOf(statusValue);
        }
        catch (IllegalArgumentException exception)
        {
            throw new IllegalStateException("Remote operation has an unsupported status.",
                    exception);
        }

        String resultCode = snapshot.getString("resultCode");

        String resultMessage = snapshot.getString("resultMessage");

        return new InventoryOperationRemoteResult(remoteOperationId, status, resultCode,
                resultMessage);
    }

    public interface UploadCallback
    {
        void onSuccess(InventoryOperationRemoteResult result);

        void onError(Exception exception);
    }
}