package com.medicationinventory.ui.reports.stock;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.medicationinventory.R;

import com.medicationinventory.model.domain.StockLevelReportItem;
import com.medicationinventory.model.enums.StockLevelStatus;

import java.util.ArrayList;
import java.util.List;


/**
 * Binds Stock Level Report records to their reusable list rows.
 */
public class StockLevelReportAdapter
        extends RecyclerView.Adapter<
        StockLevelReportAdapter.StockLevelViewHolder>
{
    public interface OnItemClickListener
    {
        void onItemClicked(
                StockLevelReportItem item
        );
    }


    private final List<StockLevelReportItem>
            items =
            new ArrayList<>();


    private final OnItemClickListener
            listener;



    public StockLevelReportAdapter(
            OnItemClickListener listener)
    {
        this.listener =
                listener;
    }


    public void submitList(
            List<StockLevelReportItem> result)
    {
        items.clear();


        if (result != null)
        {
            items.addAll(
                    result
            );
        }


        notifyDataSetChanged();
    }


    @NonNull
    @Override

    public StockLevelViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType)
    {
        View view =
                LayoutInflater
                        .from(
                                parent.getContext()
                        )
                        .inflate(
                                R.layout.item_stock_level_report,
                                parent,
                                false
                        );


        return new StockLevelViewHolder(
                view
        );
    }


    @Override

    public void onBindViewHolder(
            @NonNull StockLevelViewHolder holder,
            int position)
    {
        StockLevelReportItem item =
                items.get(
                        position
                );


        holder.medicineNameText.setText(
                safe(
                        item.getMedicineName()
                )
        );


        holder.medicineCodeText.setText(
                safe(
                        item.getMedicineCode()
                )
        );


        holder.availableText.setText(
                String.valueOf(
                        item.getTotalAvailableQuantity()
                )
        );


        holder.reorderLevelText.setText(
                String.valueOf(
                        item.getReorderLevel()
                )
        );


        holder.targetText.setText(
                String.valueOf(
                        item.getTargetStockLevel()
                )
        );


        holder.recommendedText.setText(
                "Recommended reorder: "
                        + item.getRecommendedReorderQuantity()
                        + " units"
        );


        holder.batchSummaryText.setText(
                item.getBatchCount()
                        + " batches · "
                        + item.getBatchesWithStock()
                        + " with stock"
        );


        String unit =
                item.getUnitOfMeasure();


        holder.unitText.setText(
                "Unit: "
                        + (
                        unit == null
                                ||
                                unit.trim().isEmpty()
                                ? "Not specified"
                                : unit
                )
        );


        if (item.getNearExpiryQuantity() > 0)
        {
            holder.nearExpiryText.setText(
                    item.getNearExpiryQuantity()
                            + " usable units are near expiry"
            );


            holder.nearExpiryText.setVisibility(
                    View.VISIBLE
            );
        }
        else
        {
            holder.nearExpiryText.setVisibility(
                    View.GONE
            );
        }


        StockLevelStatus status =
                item.getStockStatus();


        if (status == StockLevelStatus.OUT_OF_STOCK)
        {
            holder.statusText.setText(
                    "OUT OF STOCK"
            );
        }
        else if (status == StockLevelStatus.LOW_STOCK)
        {
            holder.statusText.setText(
                    "LOW STOCK"
            );
        }
        else
        {
            holder.statusText.setText(
                    "NORMAL"
            );
        }


        holder.itemView.setOnClickListener(
                view ->
                {
                    if (listener != null)
                    {
                        listener.onItemClicked(
                                item
                        );
                    }
                }
        );
    }


    @Override
    public int getItemCount()
    {
        return items.size();
    }


    private String safe(
            String value)
    {
        return value == null
                ? ""
                : value;
    }


    static class StockLevelViewHolder
            extends RecyclerView.ViewHolder
    {
        final TextView medicineNameText;

        final TextView medicineCodeText;

        final TextView statusText;

        final TextView availableText;

        final TextView reorderLevelText;

        final TextView targetText;

        final TextView recommendedText;

        final TextView batchSummaryText;

        final TextView nearExpiryText;

        final TextView unitText;


        StockLevelViewHolder(
                @NonNull View itemView)
        {
            super(
                    itemView
            );


            medicineNameText =
                    itemView.findViewById(
                            R.id.stockReportMedicineNameText
                    );


            medicineCodeText =
                    itemView.findViewById(
                            R.id.stockReportMedicineCodeText
                    );


            statusText =
                    itemView.findViewById(
                            R.id.stockReportStatusText
                    );


            availableText =
                    itemView.findViewById(
                            R.id.stockReportAvailableText
                    );


            reorderLevelText =
                    itemView.findViewById(
                            R.id.stockReportReorderLevelText
                    );


            targetText =
                    itemView.findViewById(
                            R.id.stockReportTargetText
                    );


            recommendedText =
                    itemView.findViewById(
                            R.id.stockReportRecommendedText
                    );


            batchSummaryText =
                    itemView.findViewById(
                            R.id.stockReportBatchSummaryText
                    );


            nearExpiryText =
                    itemView.findViewById(
                            R.id.stockReportNearExpiryText
                    );


            unitText =
                    itemView.findViewById(
                            R.id.stockReportUnitText
                    );
        }
    }
}