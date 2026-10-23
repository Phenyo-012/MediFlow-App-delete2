package com.medicationinventory.data.remote.firestore;


import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import com.medicationinventory.model.domain.MedicineClassification;



/**
 * Reads a medicine's ABC/VED classification from the Firestore
 * {@code medicineClassifications} collection and maps the document to the
 * classification domain model. Reads request the server explicitly; write
 * authority and restricted-data access are handled by the trusted path and
 * repository policy rather than by this read adapter.
 */
public class FirestoreMedicineClassificationDataSource
{
    private static final String COLLECTION =
            "medicineClassifications";


    private final FirebaseFirestore
            firestore;


    public FirestoreMedicineClassificationDataSource(
            FirebaseFirestore firestore)
    {
        if (firestore == null)
        {
            throw new IllegalArgumentException(
                    "Firestore is required."
            );
        }


        this.firestore =
                firestore;
    }


    /**
     * Loads one classification document from the server and maps its ABC/VED
     * values to the domain object. Blank medicine IDs fail immediately; Firestore
     * lookup or field-mapping failures are delivered to the callback.
     *
     * @param medicineId document ID of the medicine classification
     * @param callback receives the mapped classification or lookup/mapping error
     */
    public void getForMedicine(
            String medicineId,
            ClassificationCallback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Classification callback is required."
            );
        }


        if (medicineId == null
                ||
                medicineId.trim().isEmpty())
        {
            callback.onError(
                    new IllegalArgumentException(
                            "Medicine ID is required."
                    )
            );

            return;
        }


        String cleanMedicineId =
                medicineId.trim();


        firestore
                .collection(
                        COLLECTION
                )
                .document(
                        cleanMedicineId
                )
                .get(
                        Source.SERVER
                )
                .addOnSuccessListener(
                        snapshot ->
                        {
                            try
                            {
                                MedicineClassification classification =
                                        mapSnapshot(
                                                cleanMedicineId,
                                                snapshot
                                        );


                                callback.onSuccess(
                                        classification
                                );
                            }
                            catch (Exception exception)
                            {
                                callback.onError(
                                        exception
                                );
                            }
                        }
                )
                .addOnFailureListener(
                        callback::onError
                );
    }


    private MedicineClassification mapSnapshot(
            String requestedMedicineId,
            DocumentSnapshot snapshot)
    {
        if (snapshot == null)
        {
            throw new IllegalStateException(
                    "Classification response is unavailable."
            );
        }



        if (!snapshot.exists())
        {
            return null;
        }


        String documentMedicineId =
                snapshot.getId();


        String storedMedicineId =
                snapshot.getString(
                        "medicineId"
                );


        String abcClassification =
                snapshot.getString(
                        "abcClassification"
                );


        String vedClassification =
                snapshot.getString(
                        "vedClassification"
                );


        Timestamp updatedAt =
                snapshot.getTimestamp(
                        "updatedAt"
                );


        if (!requestedMedicineId.equals(
                documentMedicineId
        ))
        {
            throw new IllegalStateException(
                    "Classification document ID does not match the requested medicine."
            );
        }


        if (storedMedicineId == null
                ||
                !requestedMedicineId.equals(
                        storedMedicineId.trim()
                ))
        {
            throw new IllegalStateException(
                    "Classification medicine ID is invalid."
            );
        }


        if (!isValidAbc(
                abcClassification
        ))
        {
            throw new IllegalStateException(
                    "Classification contains an invalid ABC value."
            );
        }


        if (!isValidVed(
                vedClassification
        ))
        {
            throw new IllegalStateException(
                    "Classification contains an invalid VED value."
            );
        }


        if (updatedAt == null)
        {
            throw new IllegalStateException(
                    "Classification update timestamp is missing."
            );
        }


        return new MedicineClassification(
                requestedMedicineId,
                abcClassification.trim(),
                vedClassification.trim(),
                updatedAt.toDate().getTime()
        );
    }


    private boolean isValidAbc(
            String value)
    {
        if (value == null)
        {
            return false;
        }


        String normalized =
                value.trim();


        return "A".equals(
                normalized
        )
                ||
                "B".equals(
                        normalized
                )
                ||
                "C".equals(
                        normalized
                );
    }


    private boolean isValidVed(
            String value)
    {
        if (value == null)
        {
            return false;
        }


        String normalized =
                value.trim();


        return "V".equals(
                normalized
        )
                ||
                "E".equals(
                        normalized
                )
                ||
                "D".equals(
                        normalized
                );
    }


    public interface ClassificationCallback
    {
        void onSuccess(
                MedicineClassification classification
        );


        void onError(
                Exception exception
        );
    }
}