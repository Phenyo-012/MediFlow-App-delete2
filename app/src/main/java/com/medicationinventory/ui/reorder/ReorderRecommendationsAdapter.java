package com.medicationinventory.ui.reorder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

import androidx.annotation.NonNull;

import androidx.recyclerview.widget.RecyclerView;

import com.medicationinventory.R;

import com.medicationinventory.model.domain.ReorderRecommendationListItem;

import java.util.ArrayList;
import java.util.List;



/**
 * Binds Reorder Recommendations records to their reusable list rows.
 */
public class ReorderRecommendationsAdapter
        extends RecyclerView.Adapter<
        ReorderRecommendationsAdapter.RecommendationViewHolder>
{
    public interface OnRecommendationClickListener
    {
        void onRecommendationClicked(
                ReorderRecommendationListItem recommendation
        );
    }


    private final List<ReorderRecommendationListItem>
            recommendations =
            new ArrayList<>();


    private final OnRecommendationClickListener
            listener;


    public ReorderRecommendationsAdapter(
            OnRecommendationClickListener listener)
    {
        this.listener =
                listener;
    }


    public void submitList(
            List<ReorderRecommendationListItem> items)
    {
        recommendations.clear();


        if (items != null)
        {
            recommendations.addAll(
                    items
            );
        }


        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public RecommendationViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType)
    {
        View view =
                LayoutInflater
                        .from(
                                parent.getContext()
                        )
                        .inflate(
                                R.layout.item_reorder_recommendation,
                                parent,
                                false
                        );


        return new RecommendationViewHolder(
                view
        );
    }


    @Override
    public void onBindViewHolder(
            @NonNull RecommendationViewHolder holder,
            int position)
    {
        ReorderRecommendationListItem item =
                recommendations.get(
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


        holder.usableStockText.setText(
                String.valueOf(
                        item.getUsableStock()
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


        holder.recommendedQuantityText.setText(
                "Recommended reorder: "
                        + item.getRecommendedQuantity()
                        + " units"
        );


        if (item.getUsableStock() == 0)
        {
            holder.statusText.setText(
                    "OUT OF STOCK"
            );
        }
        else
        {
            holder.statusText.setText(
                    "LOW STOCK"
            );
        }


        if (item.getNearExpiryStock() > 0)
        {
            holder.nearExpiryText.setText(
                    item.getNearExpiryStock()
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


        holder.itemView.setOnClickListener(
                view ->
                {
                    if (listener != null)
                    {
                        listener
                                .onRecommendationClicked(
                                        item
                                );
                    }
                }
        );
    }


    @Override
    public int getItemCount()
    {
        return recommendations.size();
    }


    private String safe(
            String value)
    {
        return value == null
                ? ""
                : value;
    }


    static class RecommendationViewHolder
            extends RecyclerView.ViewHolder
    {
        final TextView medicineNameText;

        final TextView medicineCodeText;

        final TextView statusText;

        final TextView usableStockText;

        final TextView reorderLevelText;

        final TextView targetText;

        final TextView recommendedQuantityText;

        final TextView nearExpiryText;


        RecommendationViewHolder(
                @NonNull View itemView)
        {
            super(
                    itemView
            );


            medicineNameText =
                    itemView.findViewById(
                            R.id.reorderMedicineNameText
                    );


            medicineCodeText =
                    itemView.findViewById(
                            R.id.reorderMedicineCodeText
                    );


            statusText =
                    itemView.findViewById(
                            R.id.reorderStatusText
                    );


            usableStockText =
                    itemView.findViewById(
                            R.id.reorderUsableStockText
                    );


            reorderLevelText =
                    itemView.findViewById(
                            R.id.reorderLevelValueText
                    );


            targetText =
                    itemView.findViewById(
                            R.id.reorderTargetText
                    );


            recommendedQuantityText =
                    itemView.findViewById(
                            R.id.reorderRecommendedQuantityText
                    );


            nearExpiryText =
                    itemView.findViewById(
                            R.id.reorderNearExpiryText
                    );
        }
    }
}