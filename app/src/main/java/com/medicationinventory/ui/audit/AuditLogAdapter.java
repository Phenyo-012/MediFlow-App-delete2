package com.medicationinventory.ui.audit;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

import androidx.annotation.NonNull;

import androidx.recyclerview.widget.RecyclerView;

import com.medicationinventory.R;

import com.medicationinventory.data.local.entity.AuditCacheEntity;

import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;




/**
 * Binds Audit Log records to their reusable list rows.
 */
public class AuditLogAdapter
        extends RecyclerView.Adapter<AuditLogAdapter.ViewHolder>
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


    private final List<AuditCacheEntity>
            items =
            new ArrayList<>();


    public void setItems(
            List<AuditCacheEntity> records)
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
                                R.layout.item_audit_log,
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
        AuditCacheEntity item =
                items.get(
                        position
                );


        holder.actionText.setText(
                displayAction(
                        item.getActionType()
                )
        );


        holder.dateText.setText(
                Instant
                        .ofEpochMilli(
                                item.getPerformedAt()
                        )
                        .atZone(
                                BUSINESS_ZONE
                        )
                        .format(
                                DATE_FORMAT
                        )
        );


        String entity =
                item.getEntityType() == null
                        ? "Entity: —"
                        : "Entity: "
                          + item.getEntityType()
                          +
                          (
                                  item.getEntityId() == null
                                  ? ""
                                  : " · "
                                    + item.getEntityId()
                          );


        holder.entityText.setText(
                entity
        );


        holder.performedByText.setText(
                "Performed by: "
                        + item.getPerformedBy()
        );


        holder.detailsText.setText(
                prettyDetails(
                        item.getDetailsJson()
                )
        );
    }


    @Override
    public int getItemCount()
    {
        return items.size();
    }


    private String displayAction(
            String value)
    {
        if (value == null)
        {
            return "Unknown Audit Event";
        }


        return value
                .replace(
                        '_',
                        ' '
                );
    }



    private String prettyDetails(
            String json)
    {
        if (json == null
                ||
                json.trim().isEmpty()
                ||
                "{}".equals(
                        json.trim()
                ))
        {
            return "No additional details.";
        }


        try
        {
            return new JSONObject(
                    json
            )
                    .toString(
                            2
                    );
        }
        catch (Exception exception)
        {
            return json;
        }
    }


    static class ViewHolder
            extends RecyclerView.ViewHolder
    {
        final TextView actionText;
        final TextView dateText;
        final TextView entityText;
        final TextView performedByText;
        final TextView detailsText;


        ViewHolder(
                @NonNull View itemView)
        {
            super(
                    itemView
            );


            actionText =
                    itemView.findViewById(
                            R.id.auditActionText
                    );


            dateText =
                    itemView.findViewById(
                            R.id.auditDateText
                    );


            entityText =
                    itemView.findViewById(
                            R.id.auditEntityText
                    );


            performedByText =
                    itemView.findViewById(
                            R.id.auditPerformedByText
                    );


            detailsText =
                    itemView.findViewById(
                            R.id.auditDetailsText
                    );
        }
    }
}