package com.medicationinventory.ui;


import android.view.Menu;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.medicationinventory.R;

import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;



/**
 * Contains Bottom Navigation Permission Helper behavior used by the Android interface.
 */
public final class BottomNavigationPermissionHelper
{

    private BottomNavigationPermissionHelper()
    {
    }



    public static void hideProtectedItems(
            BottomNavigationView bottomNavigation)
    {
        if (bottomNavigation == null)
        {
            return;
        }


        Menu menu =
                bottomNavigation.getMenu();


        setVisible(
                menu,
                R.id.nav_dashboard,
                true
        );


        setVisible(
                menu,
                R.id.nav_inventory,
                false
        );


        setVisible(
                menu,
                R.id.nav_alerts,
                false
        );


        setVisible(
                menu,
                R.id.nav_reports,
                false
        );


        setVisible(
                menu,
                R.id.nav_more,
                true
        );
    }



    public static void apply(
            BottomNavigationView bottomNavigation,
            UserProfile profile)
    {
        if (bottomNavigation == null)
        {
            return;
        }


        Menu menu =
                bottomNavigation.getMenu();


        if (profile == null
                ||
                !profile.isActive())
        {
            setVisible(
                    menu,
                    R.id.nav_dashboard,
                    false
            );


            setVisible(
                    menu,
                    R.id.nav_inventory,
                    false
            );


            setVisible(
                    menu,
                    R.id.nav_alerts,
                    false
            );


            setVisible(
                    menu,
                    R.id.nav_reports,
                    false
            );


            setVisible(
                    menu,
                    R.id.nav_more,
                    false
            );


            return;
        }



        setVisible(
                menu,
                R.id.nav_dashboard,
                true
        );



        setVisible(
                menu,
                R.id.nav_inventory,
                profile.can(
                        Permission.VIEW_MEDICINES
                )
        );



        setVisible(
                menu,
                R.id.nav_alerts,
                profile.can(
                        Permission.VIEW_ALERTS
                )
        );


        setVisible(
                menu,
                R.id.nav_reports,
                profile.can(
                        Permission.GENERATE_REPORTS
                )
        );



        setVisible(
                menu,
                R.id.nav_more,
                true
        );
    }


    private static void setVisible(
            Menu menu,
            int itemId,
            boolean visible)
    {
        if (menu == null)
        {
            return;
        }


        MenuItem item =
                menu.findItem(
                        itemId
                );


        if (item != null)
        {
            item.setVisible(
                    visible
            );


            item.setEnabled(
                    visible
            );
        }
    }
}