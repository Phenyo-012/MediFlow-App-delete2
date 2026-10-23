package com.medicationinventory.ui.reports.alert;

import android.view.View;

import android.widget.AdapterView;



/**
 * Implements the Simple Item Selected Listener behavior for its Android screen and the application state it presents.
 */
public class SimpleItemSelectedListener
        implements AdapterView.OnItemSelectedListener
{
    public interface SelectionHandler
    {
        void onSelected(
                int position
        );
    }


    private final SelectionHandler
            handler;


    public SimpleItemSelectedListener(
            SelectionHandler handler)
    {
        this.handler =
                handler;
    }


    @Override

    public void onItemSelected(
            AdapterView<?> parent,
            View view,
            int position,
            long id)
    {
        if (handler != null)
        {
            handler.onSelected(
                    position
            );
        }
    }


    @Override

    public void onNothingSelected(
            AdapterView<?> parent)
    {
        // No action.
    }
}