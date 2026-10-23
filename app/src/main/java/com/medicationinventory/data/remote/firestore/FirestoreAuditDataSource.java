package com.medicationinventory.data.remote.firestore;

import com.google.firebase.Timestamp;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;

import com.medicationinventory.model.dto.AuditRemoteRecord;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;



/**
 * Reads recent audit records from the Firestore {@code auditLogs} collection and
 * maps documents to {@link com.medicationinventory.model.dto.AuditRemoteRecord}
 * values. The query is explicitly server-sourced and ordered newest first, so a
 * successful callback represents a fresh remote read rather than a cached local
 * audit view.
 */
public class FirestoreAuditDataSource
{
    private final FirebaseFirestore
            firestore;


    public FirestoreAuditDataSource(
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
     * Reads the newest audit documents from the server and converts each
     * document into an {@link com.medicationinventory.model.dto.AuditRemoteRecord}.
     * Non-positive limits use the adapter's default cap, and mapping failures
     * are reported through the callback rather than returning partial records.
     *
     * @param limit maximum number of newest records to request; non-positive uses the default
     * @param callback receives the complete mapped list or a query/mapping failure
     */
    public void loadRecent(
            int limit,
            Callback callback)
    {
        if (callback == null)
        {
            throw new IllegalArgumentException(
                    "Audit callback is required."
            );
        }


        int safeLimit =
                limit <= 0
                        ? 100
                        : limit;


        firestore
                .collection(
                        "auditLogs"
                )
                .orderBy(
                        "performedAt",
                        Query.Direction.DESCENDING
                )
                .limit(
                        safeLimit
                )

                .get(
                        Source.SERVER
                )

                .addOnSuccessListener(
                        querySnapshot ->
                        {
                            try
                            {
                                List<AuditRemoteRecord> records =
                                        new ArrayList<>();


                                for (DocumentSnapshot document :
                                        querySnapshot.getDocuments())
                                {
                                    records.add(
                                            mapDocument(
                                                    document
                                            )
                                    );
                                }


                                callback.onSuccess(
                                        records
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


    private AuditRemoteRecord mapDocument(
            DocumentSnapshot document)
    {
        if (document == null
                ||
                !document.exists())
        {
            throw new IllegalStateException(
                    "Audit document is unavailable."
            );
        }


        String documentId =
                document.getId();


        String storedAuditId =
                document.getString(
                        "auditId"
                );


        if (storedAuditId == null
                ||
                storedAuditId.trim().isEmpty())
        {
            throw new IllegalStateException(
                    "Audit record has no audit ID."
            );
        }


        if (!documentId.equals(
                storedAuditId.trim()
        ))
        {
            throw new IllegalStateException(
                    "Audit document ID does not match auditId."
            );
        }


        String actionType =
                requiredText(
                        document,
                        "actionType"
                );


        String performedBy =
                requiredText(
                        document,
                        "performedBy"
                );


        Timestamp performedAtTimestamp =
                document.getTimestamp(
                        "performedAt"
                );


        if (performedAtTimestamp == null)
        {
            throw new IllegalStateException(
                    "Audit record has no server timestamp."
            );
        }


        long performedAt =
                performedAtTimestamp
                        .toDate()
                        .getTime();


        if (performedAt <= 0)
        {
            throw new IllegalStateException(
                    "Audit server timestamp is invalid."
            );
        }


        String entityType =
                optionalText(
                        document.getString(
                                "entityType"
                        )
                );


        String entityId =
                optionalText(
                        document.getString(
                                "entityId"
                        )
                );


        String detailsJson =
                readDetailsJson(
                        document
                );


        return new AuditRemoteRecord(
                documentId,
                actionType,
                entityType,
                entityId,
                performedBy,
                performedAt,
                detailsJson
        );
    }


    private String requiredText(
            DocumentSnapshot document,
            String field)
    {
        String value =
                document.getString(
                        field
                );


        if (value == null
                ||
                value.trim().isEmpty())
        {
            throw new IllegalStateException(
                    "Audit record is missing "
                            + field
                            + "."
            );
        }


        return value.trim();
    }


    private String optionalText(
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


    private String readDetailsJson(
            DocumentSnapshot document)
    {
        Object rawDetails =
                document.get(
                        "details"
                );


        if (rawDetails == null)
        {
            return "{}";
        }


        if (!(rawDetails instanceof Map))
        {
            throw new IllegalStateException(
                    "Audit details have an invalid structure."
            );
        }


        @SuppressWarnings("unchecked")
        Map<String, Object> details =
                (Map<String, Object>)
                        rawDetails;


        return new JSONObject(
                details
        ).toString();
    }


    public interface Callback
    {
        void onSuccess(
                List<AuditRemoteRecord> records
        );


        void onError(
                Exception exception
        );
    }
}