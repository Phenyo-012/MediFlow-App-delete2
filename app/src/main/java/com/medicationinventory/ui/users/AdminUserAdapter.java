package com.medicationinventory.ui.users;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.medicationinventory.R;
import com.medicationinventory.model.dto.AdminUserSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;




/**
 * Binds Admin User records to their reusable list rows.
 */
public class AdminUserAdapter
        extends RecyclerView.Adapter<AdminUserAdapter.ViewHolder>
{
    private final List<AdminUserSummary>
            users =
            new ArrayList<>();


    private final OnUserClickListener
            listener;



    public AdminUserAdapter(
            OnUserClickListener listener)
    {
        this.listener =
                listener;
    }


    public void setUsers(
            List<AdminUserSummary> items)
    {
        users.clear();


        if (items != null)
        {
            users.addAll(
                    items
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
                                R.layout.item_admin_user,
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
        AdminUserSummary user =
                users.get(
                        position
                );


        holder.nameText.setText(
                user.getFullName()
        );


        holder.emailText.setText(
                hasText(
                        user.getEmail()
                )
                        ? user.getEmail()
                        : "Email unavailable"
        );


        holder.roleText.setText(
                "Role: "
                        + friendlyRole(
                        user.getRoleId()
                )
        );


        holder.statusText.setText(
                user.isActive()
                        ? "Active"
                        : "Inactive"
        );


        holder.itemView.setOnClickListener(
                view ->
                {
                    if (listener != null)
                    {
                        listener.onClick(
                                user
                        );
                    }
                }
        );
    }


    @Override
    public int getItemCount()
    {
        return users.size();
    }


    private String friendlyRole(
            String value)
    {
        if (!hasText(
                value
        ))
        {
            return "Not assigned";
        }


        String normalized =
                value.trim()
                        .replace(
                                '_',
                                ' '
                        )
                        .toLowerCase(Locale.ROOT);


        String[] words =
                normalized.split(
                        "\\s+"
                );


        StringBuilder builder =
                new StringBuilder();


        for (String word : words)
        {
            if (word.isEmpty())
            {
                continue;
            }


            if (builder.length() > 0)
            {
                builder.append(
                        ' '
                );
            }


            builder.append(
                    Character.toUpperCase(
                            word.charAt(
                                    0
                            )
                    )
            );


            if (word.length() > 1)
            {
                builder.append(
                        word.substring(
                                1
                        )
                );
            }
        }


        return builder.toString();
    }



    private boolean hasText(
            String value)
    {
        return value != null
                &&
                !value.trim().isEmpty();
    }


    public interface OnUserClickListener
    {
        void onClick(
                AdminUserSummary user
        );
    }


    static class ViewHolder
            extends RecyclerView.ViewHolder
    {
        final TextView nameText;
        final TextView emailText;
        final TextView roleText;
        final TextView statusText;


        ViewHolder(
                @NonNull View itemView)
        {
            super(
                    itemView
            );


            nameText =
                    itemView.findViewById(
                            R.id.adminUserNameText
                    );


            emailText =
                    itemView.findViewById(
                            R.id.adminUserEmailText
                    );


            roleText =
                    itemView.findViewById(
                            R.id.adminUserRoleText
                    );


            statusText =
                    itemView.findViewById(
                            R.id.adminUserStatusText
                    );
        }
    }
}