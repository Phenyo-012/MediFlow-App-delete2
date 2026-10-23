package com.medicationinventory.ui.sync;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

import androidx.annotation.NonNull;

import androidx.recyclerview.widget.RecyclerView;

import com.medicationinventory.R;

import com.medicationinventory.data.local.model.SyncStatusRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;




/**
 * Binds operation synchronization status records to rows in the sync history list.
 */
public class SyncStatusAdapter
        extends RecyclerView.Adapter<SyncStatusAdapter.ViewHolder>
{
    private static final ZoneId BUSINESS_ZONE =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private static final DateTimeFormatter
            DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy, HH:mm"
            );


    private final List<SyncStatusRecord>
            items =
            new ArrayList<>();

    private final OnItemClickListener
            listener;


    public void setItems(
            List<SyncStatusRecord> records)
    {
        items.clear();


        if (records != null)
        {
            items.addAll(
                    records
            );
        }


        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType)
    {
        View view =
                LayoutInflater
                        .from(
                                parent.getContext()
                        )
                        .inflate(
                                R.layout.item_sync_status,
                                parent,
                                false
                        );


        return new ViewHolder(
                view
        );
    }


    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position)
    {
        SyncStatusRecord item =
                items.get(
                        position
                );


        holder.operationTypeText.setText(
                displayOperationType(
                        item.operationType
                )
        );


        holder.statusText.setText(
                displayStatus(
                        item
                )
        );


        holder.medicineText.setText(
                buildMedicineText(
                        item
                )
        );


        holder.createdAtText.setText(
                formatTime(
                        item.clientCreatedAt
                )
        );


        holder.messageText.setText(
                buildMessage(
                        item
                )
        );

        holder.itemView.setOnClickListener(
                view ->
                        listener.onClick(
                                item
                        )
        );
    }


    @Override
    public int getItemCount()
    {
        return items.size();
    }



    private String displayOperationType(
            String type)
    {
        if (type == null
                ||
                type.trim().isEmpty())
        {
            return "Inventory operation";
        }


        String normalized =
                type.trim()
                        .replace(
                                '_',
                                ' '
                        );


        return normalized.substring(
                0,
                1
        ).toUpperCase(Locale.ROOT)
                +
                normalized.substring(
                        1
                ).toLowerCase(Locale.ROOT);
    }


    private String displayStatus(
            SyncStatusRecord item)
    {
        if (item == null
                ||
                item.status == null)
        {
            return "Unknown";
        }


        switch (item.status)
        {
            case "LOCAL_PENDING":

                if (item.attemptCount > 0)
                {
                    return "Retrying";
                }

                return "Pending";


            case "SUBMITTED":
                return "Processing";


            case "CONFIRMED":
                return "Synced";


            case "CONFLICT":
            case "REJECTED":
                return "Needs attention";


            default:
                return "Unknown";
        }
    }


    private String buildMedicineText(
            SyncStatusRecord item)
    {
        String name =
                item.medicineName == null
                        ||
                        item.medicineName.trim()
                                .isEmpty()
                        ? "Unknown medicine"
                        : item.medicineName.trim();


        String code =
                item.medicineCode == null
                        ||
                        item.medicineCode.trim()
                                .isEmpty()
                        ? null
                        : item.medicineCode.trim();


        if (code == null)
        {
            return name;
        }


        return name
                + " · "
                + code;
    }



    private String formatTime(
            long millis)
    {
        if (millis <= 0)
        {
            return "Time unavailable";
        }


        return Instant
                .ofEpochMilli(
                        millis
                )
                .atZone(
                        BUSINESS_ZONE
                )
                .format(
                        DATE_FORMAT
                );
    }


    private String buildMessage(
            SyncStatusRecord item)
    {
        if (item.status == null)
        {
            return "Synchronisation state is unavailable.";
        }


        switch (item.status)
        {
            case "LOCAL_PENDING":

                if (item.attemptCount > 0)
                {
                    return "Waiting to retry synchronisation.";
                }

                return "Saved locally and waiting to synchronise.";


            case "SUBMITTED":
                return "Uploaded and waiting for authoritative processing.";


            case "CONFIRMED":
                return "Synchronised successfully.";


            case "CONFLICT":

                if (hasText(
                        item.resultMessage
                ))
                {
                    return item.resultMessage.trim();
                }

                return "The shared inventory changed and this operation requires review.";


            case "REJECTED":

                if (hasText(
                        item.resultMessage
                ))
                {
                    return item.resultMessage.trim();
                }

                return "This operation was not accepted and requires review.";


            default:
                return "Synchronisation state is unavailable.";
        }
    }


    private boolean hasText(
            String value)
    {
        return value != null
                &&
                !value.trim().isEmpty();
    }


    public SyncStatusAdapter(
            OnItemClickListener listener)
    {
        if (listener == null)
        {
            throw new IllegalArgumentException(
                    "Sync item listener is required."
            );
        }


        this.listener =
                listener;
    }

    public interface OnItemClickListener
    {
        void onClick(
                SyncStatusRecord record
        );
    }

    static class ViewHolder
            extends RecyclerView.ViewHolder
    {
        final TextView
                operationTypeText;

        final TextView
                statusText;

        final TextView
                medicineText;

        final TextView
                createdAtText;

        final TextView
                messageText;


        ViewHolder(
                @NonNull View itemView)
        {
            super(
                    itemView
            );


            operationTypeText =
                    itemView.findViewById(
                            R.id.syncOperationTypeText
                    );


            statusText =
                    itemView.findViewById(
                            R.id.syncStatusText
                    );


            medicineText =
                    itemView.findViewById(
                            R.id.syncMedicineText
                    );


            createdAtText =
                    itemView.findViewById(
                            R.id.syncCreatedAtText
                    );


            messageText =
                    itemView.findViewById(
                            R.id.syncMessageText
                    );
        }
    }
}