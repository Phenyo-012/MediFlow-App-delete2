package com.medicationinventory.ui.inventory;


import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.InventoryMedicineItem;
import com.medicationinventory.model.domain.InventoryFilterState;
import com.medicationinventory.model.domain.UserProfile;
import com.medicationinventory.model.enums.Permission;

import com.medicationinventory.ui.BottomNavigationPermissionHelper;
import com.medicationinventory.ui.alerts.AlertsActivity;
import com.medicationinventory.ui.auth.LoginActivity;
import com.medicationinventory.ui.dashboard.DashboardActivity;
import com.medicationinventory.ui.medicine.AddMedicineActivity;
import com.medicationinventory.ui.medicine.MedicineDetailsActivity;
import com.medicationinventory.ui.scanner.BarcodeScannerActivity;
import com.medicationinventory.ui.more.MoreActivity;
import com.medicationinventory.ui.reports.ReportsActivity;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;




/**
 * Implements the Android screen for Inventory and connects user actions to application state.
 */
public class InventoryActivity
        extends AppCompatActivity
{
    private TextView inventoryCountText;

    private TextView inventoryProfileInitials;

    private MaterialButton addMedicineButton;

    private InventoryViewModel inventoryViewModel;

    private RecyclerView medicineRecyclerView;

    private LinearLayout emptyInventoryState;
    private ProgressBar inventoryLoadingProgress;

    private MedicineAdapter medicineAdapter;

    private TextInputEditText inventorySearchInput;

    private TextView emptyInventoryTitle;

    private TextView emptyInventoryMessage;

    private BottomNavigationView bottomNavigation;


    private MaterialButton inventoryFilterButton;
    private TextView inventoryFilterSummary;
    private TextView inventoryClearFilters;
    private List<String> availableCategories = Collections.emptyList();
    private InventoryFilterState currentFilters = InventoryFilterState.defaults();
    private boolean restrictedFiltersAllowed = false;
    private BottomSheetDialog activeFilterDialog;
    private TextView activeClassificationStatusText;
    private String classificationStatus;
    private int activeMedicineCount = 0;
    private int displayedMedicineCount = 0;


    private final ActivityResultLauncher<Intent> barcodeScanLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != Activity.RESULT_OK
                                || result.getData() == null
                                || inventoryViewModel == null
                                || inventorySearchInput == null) {
                            return;
                        }
                        String code = result.getData().getStringExtra(
                                BarcodeScannerActivity.EXTRA_SCANNED_VALUE);
                        if (code == null || code.isEmpty()) return;
                        inventoryViewModel.clearSelections();
                        inventorySearchInput.setText(code);
                        inventorySearchInput.setSelection(code.length());
                        Toast.makeText(this,
                                "Code scanned. Showing matching inventory entries.",
                                Toast.LENGTH_SHORT).show();
                    }
            );

    private String currentSearchQuery =
            "";


    @Override

    protected void onCreate(
            Bundle savedInstanceState)
    {
        super.onCreate(
                savedInstanceState
        );


        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        AuthenticationRepository authenticationRepository =
                application
                        .getAppContainer()
                        .getAuthenticationRepository();


        if (!authenticationRepository.isSignedIn())
        {
            goToLogin();

            return;
        }


        setContentView(
                R.layout.activity_inventory
        );


        bindViews();

        setupMedicineList();


        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        MedicineRepository medicineRepository =
                application
                        .getAppContainer()
                        .getMedicineRepository();


        InventoryViewModelFactory factory =
                new InventoryViewModelFactory(
                        authenticationRepository,
                        userRepository,
                        medicineRepository,
                        application.getAppContainer().getMedicineClassificationRepository()
                );


        inventoryViewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                InventoryViewModel.class
                        );


        setupSearch();
        setupBarcodeScanner();
        setupFilterControls();


        setupBottomNavigation();


        observeData();

        setupAddMedicineButton();
    }


    private void bindViews()
    {
        inventoryCountText =
                findViewById(
                        R.id.inventoryCountText
                );


        inventoryProfileInitials =
                findViewById(
                        R.id.inventoryProfileInitials
                );


        addMedicineButton =
                findViewById(
                        R.id.addMedicineButton
                );


        medicineRecyclerView =
                findViewById(
                        R.id.medicineRecyclerView
                );
        inventoryLoadingProgress = findViewById(R.id.inventoryLoadingProgress);


        emptyInventoryState =
                findViewById(
                        R.id.emptyInventoryState
                );


        inventorySearchInput =
                findViewById(
                        R.id.inventorySearchInput
                );


        emptyInventoryTitle =
                findViewById(
                        R.id.emptyInventoryTitle
                );


        emptyInventoryMessage =
                findViewById(
                        R.id.emptyInventoryMessage
                );

        inventoryFilterButton = findViewById(R.id.inventoryFilterButton);
        inventoryFilterSummary = findViewById(R.id.inventoryFilterSummary);
        inventoryClearFilters = findViewById(R.id.inventoryClearFilters);
    }



    private void setupBarcodeScanner() {
        TextInputLayout searchLayout = findViewById(R.id.inventorySearchLayout);
        searchLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        searchLayout.setEndIconDrawable(android.R.drawable.ic_menu_camera);
        searchLayout.setEndIconContentDescription("Scan barcode or QR code");
        searchLayout.setEndIconOnClickListener(view ->
                barcodeScanLauncher.launch(
                        new Intent(this, BarcodeScannerActivity.class)));
    }



    private void setupMedicineList()
    {
        medicineAdapter =
                new MedicineAdapter(
                        medicine ->
                        {
                            Intent intent =
                                    new Intent(
                                            this,
                                            MedicineDetailsActivity.class
                                    );


                            intent.putExtra(
                                    MedicineDetailsActivity
                                            .EXTRA_MEDICINE_ID,
                                    medicine.getMedicineId()
                            );


                            startActivity(
                                    intent
                            );
                        }
                );


        medicineRecyclerView
                .setLayoutManager(
                        new LinearLayoutManager(
                                this
                        )
                );


        medicineRecyclerView
                .setAdapter(
                        medicineAdapter
                );
    }



    private void observeData()
    {
        inventoryViewModel
                .getUserProfile()
                .observe(
                        this,
                        this::handleUserProfile
                );


        inventoryViewModel
                .getMedicineCount()
                .observe(
                        this,
                        this::showMedicineCount
                );


        inventoryViewModel
                .getMedicines()
                .observe(
                        this,
                        this::showMedicines
                );

        inventoryViewModel.getFilters().observe(this, state -> {
            currentFilters = state == null ? InventoryFilterState.defaults() : state;
            updateFilterSummary();
        });
        inventoryViewModel.getAvailableCategories().observe(this, categories ->
                availableCategories = categories == null
                        ? Collections.emptyList() : new ArrayList<>(categories));
        inventoryViewModel.getAbcVedFiltersAvailable().observe(this, allowed -> {
            boolean next = Boolean.TRUE.equals(allowed);
            if (restrictedFiltersAllowed && !next && activeFilterDialog != null)
                activeFilterDialog.dismiss();
            restrictedFiltersAllowed = next;
            updateFilterSummary();
        });
        inventoryViewModel.getAbcVedRefreshStatus().observe(this, status -> {
            classificationStatus = status;
            if (activeClassificationStatusText != null)
                activeClassificationStatusText.setText(status == null
                        ? "Restricted classifications are cached locally and may be incomplete offline."
                        : status);
        });
    }



    private void showMedicines(List<InventoryMedicineItem> medicines) {
        inventoryLoadingProgress.setVisibility(View.GONE);
        medicineAdapter.setMedicines(medicines);
        boolean empty = medicines == null || medicines.isEmpty();
        displayedMedicineCount = medicines == null ? 0 : medicines.size();
        updateMedicineCountText();

        medicineRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        emptyInventoryState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) return;
        if (currentFilters.hasAnyCriteria()) {
            emptyInventoryTitle.setText("No matching medicines");
            emptyInventoryMessage.setText(
                    "Try adjusting your search or filters. Your inventory hasn't been changed.");
        } else {
            emptyInventoryTitle.setText("No medicines yet");
            emptyInventoryMessage.setText(
                    "Medicines added to the inventory will appear here.");
        }
    }


    private void handleUserProfile(
            UserProfile userProfile)
    {

        BottomNavigationPermissionHelper
                .apply(
                        bottomNavigation,
                        userProfile
                );


        if (userProfile == null)
        {
            return;
        }


        if (!userProfile.isActive())
        {
            goToLogin();

            return;
        }


        if (!userProfile.can(
                Permission.VIEW_MEDICINES
        ))
        {
            Toast.makeText(
                    this,
                    "You do not have permission to view medicines.",
                    Toast.LENGTH_LONG
            ).show();


            goToDashboard();

            return;
        }


        inventoryProfileInitials
                .setText(
                        buildInitials(
                                userProfile
                        )
                );


        addMedicineButton
                .setVisibility(
                        userProfile.can(
                                Permission.CREATE_MEDICINE
                        )
                                ? View.VISIBLE
                                : View.GONE
                );
    }



    private void showMedicineCount(Integer count) {
        activeMedicineCount = count == null ? 0 : Math.max(0, count);
        updateMedicineCountText();
    }


    private void updateMedicineCountText() {
        inventoryCountText.setText("Showing " + displayedMedicineCount
                + " of " + activeMedicineCount + " medicines");
    }


    private void setupFilterControls() {
        inventoryFilterButton.setOnClickListener(view -> showFilterSheet());
        inventoryClearFilters.setOnClickListener(view -> inventoryViewModel.clearSelections());
        updateFilterSummary();
    }


    private void updateFilterSummary() {
        int applied = currentFilters.getActiveSelectionCount();
        inventoryFilterButton.setText(applied == 0
                ? "Filter & Sort" : "Filters (" + applied + ")");
        inventoryClearFilters.setVisibility(applied == 0 ? View.GONE : View.VISIBLE);
        if (applied == 0) {
            inventoryFilterSummary.setText("All active medicines");
            return;
        }
        StringBuilder text = new StringBuilder();
        if (!currentFilters.getCategory().isEmpty())
            text.append(currentFilters.getCategory());
        if (currentFilters.getStockStatus() != InventoryFilterState.StockStatus.ALL)
            appendSummary(text, currentFilters.getStockStatus().getLabel());
        if (currentFilters.getExpiryRange() != InventoryFilterState.ExpiryRange.ALL)
            appendSummary(text, currentFilters.getExpiryRange().getLabel());
        if (currentFilters.getSortOrder() != InventoryFilterState.SortOrder.NAME_ASC)
            appendSummary(text, currentFilters.getSortOrder().getLabel());
        if (restrictedFiltersAllowed && currentFilters.getAbcClass()
                != InventoryFilterState.AbcClass.ALL)
            appendSummary(text, "ABC: " + currentFilters.getAbcClass().getLabel());
        if (restrictedFiltersAllowed && currentFilters.getVedClass()
                != InventoryFilterState.VedClass.ALL)
            appendSummary(text, "VED: " + currentFilters.getVedClass().getLabel());
        inventoryFilterSummary.setText(text.toString());
    }


    private void appendSummary(StringBuilder text, String addition) {
        if (text.length() > 0) text.append(" • ");
        text.append(addition);
    }


    private void showFilterSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.bottom_sheet_inventory_filters, null);
        dialog.setContentView(sheet);
        activeFilterDialog = dialog;
        dialog.setOnDismissListener(unused -> {
            if (activeFilterDialog == dialog) {
                activeFilterDialog = null;
                activeClassificationStatusText = null;
            }
        });

        MaterialAutoCompleteTextView categoryInput =
                sheet.findViewById(R.id.filterCategoryInput);
        MaterialAutoCompleteTextView statusInput =
                sheet.findViewById(R.id.filterStockInput);
        MaterialAutoCompleteTextView expiryInput =
                sheet.findViewById(R.id.filterExpiryInput);
        MaterialAutoCompleteTextView sortInput =
                sheet.findViewById(R.id.filterSortInput);

        List<String> categoryOptions = new ArrayList<>();
        categoryOptions.add("All categories");
        for (String category : availableCategories) {
            if (category != null && !category.trim().isEmpty()
                    && !containsCategory(categoryOptions, category))
                categoryOptions.add(category);
        }

        if (!currentFilters.getCategory().isEmpty()
                && !containsCategory(categoryOptions, currentFilters.getCategory()))
            categoryOptions.add(currentFilters.getCategory());
        bindDropdown(categoryInput, categoryOptions,
                currentFilters.getCategory().isEmpty()
                        ? "All categories" : currentFilters.getCategory());

        List<String> statusOptions = new ArrayList<>();
        for (InventoryFilterState.StockStatus item : InventoryFilterState.StockStatus.values())
            statusOptions.add(item.getLabel());
        bindDropdown(statusInput, statusOptions, currentFilters.getStockStatus().getLabel());

        List<String> expiryOptions = new ArrayList<>();
        for (InventoryFilterState.ExpiryRange item : InventoryFilterState.ExpiryRange.values())
            expiryOptions.add(item.getLabel());
        bindDropdown(expiryInput, expiryOptions, currentFilters.getExpiryRange().getLabel());

        List<String> sortOptions = new ArrayList<>();
        for (InventoryFilterState.SortOrder item : InventoryFilterState.SortOrder.values())
            sortOptions.add(item.getLabel());
        bindDropdown(sortInput, sortOptions, currentFilters.getSortOrder().getLabel());

        View classificationSection = sheet.findViewById(R.id.filterClassificationSection);
        classificationSection.setVisibility(restrictedFiltersAllowed ? View.VISIBLE : View.GONE);
        MaterialAutoCompleteTextView abcInput = sheet.findViewById(R.id.filterAbcInput);
        MaterialAutoCompleteTextView vedInput = sheet.findViewById(R.id.filterVedInput);
        activeClassificationStatusText = sheet.findViewById(R.id.filterClassificationStatusText);
        if (restrictedFiltersAllowed) {
            List<String> abcOptions = new ArrayList<>();
            for (InventoryFilterState.AbcClass option : InventoryFilterState.AbcClass.values())
                abcOptions.add(option.getLabel());
            bindDropdown(abcInput, abcOptions, currentFilters.getAbcClass().getLabel());
            List<String> vedOptions = new ArrayList<>();
            for (InventoryFilterState.VedClass option : InventoryFilterState.VedClass.values())
                vedOptions.add(option.getLabel());
            bindDropdown(vedInput, vedOptions, currentFilters.getVedClass().getLabel());
            activeClassificationStatusText.setText(classificationStatus == null
                    ? "Restricted classifications are cached locally and may be incomplete offline."
                    : classificationStatus);
            MaterialButton refreshRestricted = sheet.findViewById(
                    R.id.filterRefreshClassificationButton);
            refreshRestricted.setOnClickListener(view -> inventoryViewModel.refreshClassifications());
        }

        MaterialButton apply = sheet.findViewById(R.id.filterApplyButton);
        MaterialButton reset = sheet.findViewById(R.id.filterClearButton);
        reset.setOnClickListener(view -> {
            inventoryViewModel.clearSelections();
            dialog.dismiss();
        });
        apply.setOnClickListener(view -> {
            String category = selected(categoryInput);
            inventoryViewModel.setAllSelections(
                    "All categories".equals(category) ? "" : category,
                    InventoryFilterState.StockStatus.fromLabel(selected(statusInput)),
                    InventoryFilterState.ExpiryRange.fromLabel(selected(expiryInput)),
                    InventoryFilterState.SortOrder.fromLabel(selected(sortInput)),
                    restrictedFiltersAllowed
                            ? InventoryFilterState.AbcClass.fromLabel(selected(abcInput))
                            : InventoryFilterState.AbcClass.ALL,
                    restrictedFiltersAllowed
                            ? InventoryFilterState.VedClass.fromLabel(selected(vedInput))
                            : InventoryFilterState.VedClass.ALL);
            dialog.dismiss();
        });
        dialog.show();
    }


    private void bindDropdown(MaterialAutoCompleteTextView input,
                              List<String> options, String selected) {
        input.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, options));
        input.setText(selected, false);
    }


    private boolean containsCategory(List<String> values, String candidate) {
        for (String value : values)
            if (value.equalsIgnoreCase(candidate)) return true;
        return false;
    }


    private String selected(MaterialAutoCompleteTextView input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String buildInitials(
            UserProfile profile)
    {
        StringBuilder initials =
                new StringBuilder();


        if (profile.getFirstName() != null
                &&
                !profile
                        .getFirstName()
                        .trim()
                        .isEmpty())
        {
            initials.append(
                    profile
                            .getFirstName()
                            .trim()
                            .charAt(
                                    0
                            )
            );
        }


        if (profile.getLastName() != null
                &&
                !profile
                        .getLastName()
                        .trim()
                        .isEmpty())
        {
            initials.append(
                    profile
                            .getLastName()
                            .trim()
                            .charAt(
                                    0
                            )
            );
        }


        if (initials.length() == 0)
        {
            return "--";
        }


        return initials
                .toString()
                .toUpperCase(Locale.getDefault());
    }



    private void setupSearch() {
        InventoryFilterState initial = inventoryViewModel.getCurrentFilters();
        currentSearchQuery = initial.getSearchQuery();
        if (!currentSearchQuery.equals(String.valueOf(inventorySearchInput.getText())))
            inventorySearchInput.setText(currentSearchQuery);
        inventorySearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int after) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                currentSearchQuery = s == null ? "" : s.toString().trim();
                inventoryViewModel.setSearchQuery(currentSearchQuery);
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
    }


    private void setupAddMedicineButton()
    {
        addMedicineButton
                .setOnClickListener(
                        view ->
                                startActivity(
                                        new Intent(
                                                this,
                                                AddMedicineActivity.class
                                        )
                                )
                );
    }



    private void setupBottomNavigation()
    {
        bottomNavigation =
                findViewById(
                        R.id.bottomNavigation
                );


        BottomNavigationPermissionHelper
                .hideProtectedItems(
                        bottomNavigation
                );


        bottomNavigation
                .setSelectedItemId(
                        R.id.nav_inventory
                );


        bottomNavigation
                .setOnItemSelectedListener(
                        item ->
                        {
                            int itemId =
                                    item.getItemId();


                            if (itemId == R.id.nav_inventory)
                            {
                                return true;
                            }


                            if (itemId == R.id.nav_dashboard)
                            {
                                startActivity(
                                        new Intent(
                                                this,
                                                DashboardActivity.class
                                        )
                                );


                                finish();

                                return true;
                            }


                            if (itemId
                                    == R.id.nav_alerts)
                            {
                                startActivity(
                                        new Intent(
                                                this,
                                                AlertsActivity.class
                                        )
                                );


                                finish();

                                return true;
                            }


                            if (itemId
                                    == R.id.nav_reports)
                            {
                                startActivity(
                                        new Intent(
                                                this,
                                                ReportsActivity.class
                                        )
                                );


                                finish();

                                return true;
                            }


                            if (itemId
                                    == R.id.nav_more)
                            {
                                startActivity(
                                        new Intent(
                                                this,
                                                MoreActivity.class
                                        )
                                );


                                finish();

                                return true;
                            }


                            return false;
                        }
                );
    }


    @Override

    protected void onResume() {
        super.onResume();
        if (inventoryViewModel != null) inventoryViewModel.refreshCalendarIfNeeded();
    }


    private void goToDashboard()
    {
        Intent intent =
                new Intent(
                        this,
                        DashboardActivity.class
                );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );


        startActivity(
                intent
        );


        finish();
    }



    private void goToLogin()
    {
        Intent intent =
                new Intent(
                        this,
                        LoginActivity.class
                );


        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        finish();
    }
}
