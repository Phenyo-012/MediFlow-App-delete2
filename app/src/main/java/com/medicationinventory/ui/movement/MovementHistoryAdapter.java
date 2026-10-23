package com.medicationinventory.ui.movement;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.medicationinventory.R;

import com.medicationinventory.data.local.model.MovementHistoryRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds Movement History records to their reusable list rows.
 */
public class MovementHistoryAdapter
        extends RecyclerView.Adapter<
        MovementHistoryAdapter.ViewHolder>
{
    private final List<MovementHistoryRecord>
            items =
            new ArrayList<>();

    private boolean correctionSelectionMode =
            false;


    private OnMovementSelectedListener onMovementSelectedListener;


    private final ZoneId businessZone =
            ZoneId.of(
                    "Africa/Johannesburg"
            );


    private final DateTimeFormatter
            dateTimeFormatter =
            DateTimeFormatter.ofPattern(
                    "dd MMM yyyy • HH:mm"
            );

    public void setCorrectionSelectionMode(
            boolean correctionSelectionMode)
    {
        this.correctionSelectionMode =
                correctionSelectionMode;

        notifyDataSetChanged();
    }


    public void setOnMovementSelectedListener(
            OnMovementSelectedListener listener)
    {
        this.onMovementSelectedListener =
                listener;
    }

    public void setItems(
            List<MovementHistoryRecord> newItems)
    {
        items.clear();


        if (newItems != null)
        {
            items.addAll(
                    newItems
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
                                R.layout.item_movement_history,
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
        MovementHistoryRecord item =
                items.get(
                        position
                );


        holder.typeText.setText(
                displayType(
                        item.transactionType
                )
        );


        holder.quantityText.setText(
                formatQuantity(
                        item.quantityChange
                )
        );


        holder.medicineText.setText(
                item.medicineName
                        + " • "
                        + item.medicineCode
        );


        holder.batchText.setText(
                "Batch: "
                        + item.batchNumber
        );


        holder.dateText.setText(
                formatDate(
                        item
                )
        );


        holder.statusText.setText(
                displayStatus(
                        item
                )
        );


        String detail =
                buildReasonDetail(
                        item
                );


        if (detail == null)
        {
            holder.reasonText.setVisibility(
                    View.GONE
            );
        }
        else
        {
            holder.reasonText.setVisibility(
                    View.VISIBLE
            );

            holder.reasonText.setText(
                    detail
            );
        }


        holder.userText.setText(
                "Performed by: "
                        + item.performedBy
        );


        boolean selectableForCorrection =
                correctionSelectionMode
                        &&
                        !item.provisional
                        &&
                        "CONFIRMED".equals(
                                item.operationStatus
                        )
                        &&
                        hasText(
                                item.operationId
                        )
                        &&
                        hasText(
                                item.batchId
                        );


        holder.itemView.setClickable(
                selectableForCorrection
        );


        holder.itemView.setFocusable(
                selectableForCorrection
        );



        holder.itemView.setAlpha(
                correctionSelectionMode
                        && !selectableForCorrection
                        ? 0.55f
                        : 1.0f
        );


        holder.selectionHintText.setVisibility(
                selectableForCorrection
                        ? View.VISIBLE
                        : View.GONE
        );


        if (selectableForCorrection)
        {
            holder.itemView.setOnClickListener(
                    view ->
                    {
                        if (onMovementSelectedListener != null)
                        {
                            onMovementSelectedListener
                                    .onMovementSelected(
                                            item
                                    );
                        }
                    }
            );
        }
        else
        {

            holder.itemView.setOnClickListener(
                    null
            );
        }
    }


    @Override
    public int getItemCount()
    {
        return items.size();
    }


    private String displayType(
            String transactionType)
    {
        if ("RECEIPT".equals(
                transactionType
        ))
        {
            return "Stock Received";
        }


        if ("ISSUE".equals(
                transactionType
        ))
        {
            return "Stock Issued";
        }


        if ("ADJUSTMENT".equals(
                transactionType
        ))
        {
            return "Stock Adjustment";
        }


        if ("CORRECTION".equals(
                transactionType
        ))
        {
            return "Stock Correction";
        }


        return transactionType == null
                ? "Inventory Movement"
                : transactionType;
    }


    private String formatQuantity(
            int quantity)
    {
        if (quantity > 0)
        {
            return "+"
                    + quantity;
        }


        return String.valueOf(
                quantity
        );
    }


    private String formatDate(
            MovementHistoryRecord item)
    {
        long timestamp =
                item.serverCreatedAt > 0
                        ? item.serverCreatedAt
                        : item.clientCreatedAt;


        if (timestamp <= 0)
        {
            return "Time unavailable";
        }


        return Instant
                .ofEpochMilli(
                        timestamp
                )
                .atZone(
                        businessZone
                )
                .format(
                        dateTimeFormatter
                );
    }


    private String displayStatus(
            MovementHistoryRecord item)
    {
        if (item.provisional)
        {
            if ("SUBMITTED".equals(
                    item.operationStatus
            ))
            {
                return "SUBMITTED • Awaiting cloud confirmation";
            }


            return "PENDING • Stored locally";
        }


        if ("CONFIRMED".equals(
                item.operationStatus
        ))
        {
            return "CONFIRMED";
        }


        return item.operationStatus == null
                ? "Recorded"
                : item.operationStatus;
    }


    private String buildReasonDetail(
            MovementHistoryRecord item)
    {
        StringBuilder result =
                new StringBuilder();


        if (hasText(
                item.reason
        ))
        {
            result.append(
                    "Reason: "
            );

            result.append(
                    item.reason.trim()
            );
        }


        if (hasText(
                item.overrideReason
        ))
        {
            if (result.length() > 0)
            {
                result.append(
                        "\n"
                );
            }


            result.append(
                    "FEFO override: "
            );

            result.append(
                    item.overrideReason.trim()
            );
        }


        return result.length() == 0
                ? null
                : result.toString();
    }


    private boolean hasText(
            String value)
    {
        return value != null
                &&
                !value.trim().isEmpty();
    }

    public interface OnMovementSelectedListener
    {
        void onMovementSelected(
                MovementHistoryRecord record
        );
    }

    static class ViewHolder
            extends RecyclerView.ViewHolder
    {
        final TextView typeText;

        final TextView quantityText;

        final TextView medicineText;

        final TextView batchText;

        final TextView dateText;

        final TextView statusText;

        final TextView reasonText;

        final TextView userText;

        final TextView selectionHintText;


        ViewHolder(
                @NonNull View itemView)
        {
            super(
                    itemView
            );


            typeText =
                    itemView.findViewById(
                            R.id.movementTypeText
                    );


            quantityText =
                    itemView.findViewById(
                            R.id.movementQuantityText
                    );


            medicineText =
                    itemView.findViewById(
                            R.id.movementMedicineText
                    );


            batchText =
                    itemView.findViewById(
                            R.id.movementBatchText
                    );


            dateText =
                    itemView.findViewById(
                            R.id.movementDateText
                    );


            statusText =
                    itemView.findViewById(
                            R.id.movementStatusText
                    );


            reasonText =
                    itemView.findViewById(
                            R.id.movementReasonText
                    );


            userText =
                    itemView.findViewById(
                            R.id.movementUserText
                    );

            selectionHintText =
                    itemView.findViewById(
                            R.id.movementSelectionHintText
                    );
        }
    }
}