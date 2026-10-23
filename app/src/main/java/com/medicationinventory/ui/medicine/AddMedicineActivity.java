package com.medicationinventory.ui.medicine;


import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.medicationinventory.MedicationInventoryApplication;
import com.medicationinventory.R;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.MedicineRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.Medicine;
import com.medicationinventory.model.domain.MedicineCategoryCatalog;
import com.medicationinventory.model.domain.MedicineCategoryOptionPolicy;

import com.medicationinventory.validation.MedicineValidationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * Implements the Add Medicine Activity behavior for its Android screen and the application state it presents.
 */
public class AddMedicineActivity
        extends AppCompatActivity
{
    public static final String EXTRA_MEDICINE_ID =
            "medicine_id";


    private TextInputLayout medicineCodeLayout;

    private TextInputLayout medicineNameLayout;

    private TextInputLayout categoryLayout;

    private TextInputLayout customCategoryLayout;

    private TextInputLayout dosageFormLayout;

    private TextInputLayout unitOfMeasureLayout;

    private TextInputLayout reorderLevelLayout;

    private TextInputLayout targetStockLevelLayout;


    private TextInputEditText medicineCodeInput;

    private TextInputEditText medicineNameInput;

    private TextInputEditText genericNameInput;

    private TextInputEditText customCategoryInput;

    private TextInputEditText strengthInput;

    private TextInputEditText dosageFormInput;

    private TextInputEditText unitOfMeasureInput;

    private TextInputEditText reorderLevelInput;

    private TextInputEditText targetStockLevelInput;


    private MaterialAutoCompleteTextView
            categoryInput;


    private MaterialButton
            saveMedicineButton;


    private ProgressBar
            saveProgress;


    private TextView
            formErrorText;


    private TextView
            formTitleText;


    private MedicineFormViewModel
            viewModel;


    private String
            editingMedicineId;


    private boolean
            editingMode = false;


    private boolean
            populateFormFromModel = true;


    private final List<String>
            categoryOptions =
            new ArrayList<>();


    private String
            currentMedicineCategory;

    private List<String> localCategories;
    private List<String> sharedCloudCategories;


    @Override

    protected void onCreate(
            Bundle savedInstanceState)
    {
        super.onCreate(
                savedInstanceState
        );


        editingMedicineId =
                getIntent()
                        .getStringExtra(
                                EXTRA_MEDICINE_ID
                        );


        editingMode =
                editingMedicineId != null
                        &&
                        !editingMedicineId
                                .trim()
                                .isEmpty();


        populateFormFromModel =
                savedInstanceState == null;


        setContentView(
                R.layout.activity_add_medicine
        );


        bindViews();

        setupViewModel();

        setupCategoryDropdown();

        observeFormState();

        observeCategories();

        configureFormMode();

        observeMedicineToEdit();

        setupActions();


        if (editingMode
                &&
                savedInstanceState == null)
        {
            viewModel.loadMedicine(
                    editingMedicineId
            );
        }
    }


    private void bindViews()
    {
        medicineCodeLayout =
                findViewById(
                        R.id.medicineCodeLayout
                );


        medicineNameLayout =
                findViewById(
                        R.id.medicineNameLayout
                );


        categoryLayout =
                findViewById(
                        R.id.categoryLayout
                );


        customCategoryLayout =
                findViewById(
                        R.id.customCategoryLayout
                );


        dosageFormLayout =
                findViewById(
                        R.id.dosageFormLayout
                );


        unitOfMeasureLayout =
                findViewById(
                        R.id.unitOfMeasureLayout
                );


        reorderLevelLayout =
                findViewById(
                        R.id.reorderLevelLayout
                );


        targetStockLevelLayout =
                findViewById(
                        R.id.targetStockLevelLayout
                );


        medicineCodeInput =
                findViewById(
                        R.id.medicineCodeInput
                );


        medicineNameInput =
                findViewById(
                        R.id.medicineNameInput
                );


        genericNameInput =
                findViewById(
                        R.id.genericNameInput
                );


        categoryInput =
                findViewById(
                        R.id.categoryInput
                );


        customCategoryInput =
                findViewById(
                        R.id.customCategoryInput
                );


        strengthInput =
                findViewById(
                        R.id.strengthInput
                );


        dosageFormInput =
                findViewById(
                        R.id.dosageFormInput
                );


        unitOfMeasureInput =
                findViewById(
                        R.id.unitOfMeasureInput
                );


        reorderLevelInput =
                findViewById(
                        R.id.reorderLevelInput
                );


        targetStockLevelInput =
                findViewById(
                        R.id.targetStockLevelInput
                );


        saveMedicineButton =
                findViewById(
                        R.id.saveMedicineButton
                );


        saveProgress =
                findViewById(
                        R.id.saveProgress
                );


        formErrorText =
                findViewById(
                        R.id.formErrorText
                );


        formTitleText =
                findViewById(
                        R.id.formTitleText
                );
    }


    private void setupViewModel()
    {
        MedicationInventoryApplication application =
                (MedicationInventoryApplication)
                        getApplication();


        AuthenticationRepository
                authenticationRepository =
                application
                        .getAppContainer()
                        .getAuthenticationRepository();


        UserRepository userRepository =
                application
                        .getAppContainer()
                        .getUserRepository();


        MedicineRepository medicineRepository =
                application
                        .getAppContainer()
                        .getMedicineRepository();


        MedicineFormViewModelFactory factory =
                new MedicineFormViewModelFactory(
                        authenticationRepository,
                        userRepository,
                        medicineRepository
                );


        viewModel =
                new ViewModelProvider(
                        this,
                        factory
                )
                        .get(
                                MedicineFormViewModel.class
                        );
    }


    private void setupCategoryDropdown()
    {
        rebuildCategoryOptions(
                null
        );


        categoryInput
                .setThreshold(
                        0
                );


        categoryInput
                .setOnClickListener(
                        view ->
                                categoryInput
                                        .showDropDown()
                );


        categoryInput
                .setOnItemClickListener(
                        (
                                parent,
                                view,
                                position,
                                id
                        ) ->
                                handleCategorySelected(
                                        categoryInput
                                                .getText()
                                                .toString()
                                )
                );
    }


    private void observeCategories()
    {
        // The existing Room-derived options are retained, including offline and
        // un-reconciled local records.
        viewModel.getActiveCategories().observe(this, this::rebuildCategoryOptions);

        // The application already listens to server-confirmed /medicines
        // snapshots. Reuse that listener rather than opening another query.
        MedicationInventoryApplication application =
                (MedicationInventoryApplication) getApplication();
        application.getAppContainer().getMedicineCloudSyncCoordinator()
                .observeSharedCategories().observe(this, categories -> {
                    sharedCloudCategories = categories;
                    rebuildCategoryOptions(localCategories);
                });
    }

    private void rebuildCategoryOptions(List<String> storedCategories)
    {
        localCategories = storedCategories;
        categoryOptions.clear();
        categoryOptions.addAll(MedicineCategoryOptionPolicy.build(
                localCategories, sharedCloudCategories, currentMedicineCategory));

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, categoryOptions);
        categoryInput.setAdapter(adapter);
    }

    private void handleCategorySelected(
            String selected)
    {
        categoryLayout.setError(
                null
        );


        if (MedicineCategoryCatalog
                .OTHER
                .equalsIgnoreCase(
                        selected
                ))
        {
            customCategoryLayout
                    .setVisibility(
                            View.VISIBLE
                    );


            customCategoryInput
                    .requestFocus();


            return;
        }


        customCategoryLayout
                .setError(
                        null
                );


        customCategoryLayout
                .setVisibility(
                        View.GONE
                );
    }

    private void observeFormState()
    {
        viewModel
                .getFormState()
                .observe(
                        this,
                        state ->
                        {
                            if (state == null)
                            {
                                return;
                            }


                            switch (state.getStatus())
                            {
                                case IDLE:
                                    showIdle();
                                    break;


                                case LOADING:
                                    showLoading();
                                    break;


                                case VALIDATION_ERROR:
                                    showValidationErrors(
                                            state
                                                    .getValidationResult()
                                    );
                                    break;


                                case DUPLICATE_CODE:
                                    showDuplicateCode(
                                            state
                                                    .getMessage()
                                    );
                                    break;


                                case SUCCESS:
                                    showSuccess();
                                    break;


                                case CLOUD_OUTCOME_UNCERTAIN:
                                    showError(state.getMessage());
                                    saveMedicineButton.setEnabled(false);
                                    break;

                                case ERROR:
                                    showError(
                                            state
                                                    .getMessage()
                                    );
                                    break;
                            }
                        }
                );
    }


    private void setupActions()
    {
        ImageButton backButton =
                findViewById(
                        R.id.backButton
                );


        backButton
                .setOnClickListener(
                        view ->
                                finish()
                );


        saveMedicineButton
                .setOnClickListener(
                        view ->
                                submitForm()
                );
    }


    private void submitForm()
    {
        clearErrors();


        String category =
                resolveSelectedCategory();


        if (category == null)
        {
            return;
        }


        viewModel.saveMedicine(
                editingMedicineId,
                textOf(
                        medicineCodeInput
                ),
                textOf(
                        medicineNameInput
                ),
                textOf(
                        genericNameInput
                ),
                category,
                textOf(
                        strengthInput
                ),
                textOf(
                        dosageFormInput
                ),
                textOf(
                        unitOfMeasureInput
                ),
                textOf(
                        reorderLevelInput
                ),
                textOf(
                        targetStockLevelInput
                )
        );
    }


    private String resolveSelectedCategory()
    {
        String selected =
                normalizeCategoryText(
                        categoryInput
                                .getText()
                                .toString()
                );


        if (selected == null)
        {
            categoryLayout.setError(
                    "Medicine category is required."
            );


            return null;
        }


        if (MedicineCategoryCatalog
                .OTHER
                .equalsIgnoreCase(
                        selected
                ))
        {
            String custom =
                    normalizeCategoryText(
                            textOf(
                                    customCategoryInput
                            )
                    );


            if (custom == null)
            {
                customCategoryLayout
                        .setError(
                                "Enter the new medicine category."
                        );


                return null;
            }



            String existing =
                    findExistingCategory(
                            custom
                    );


            if (existing != null)
            {
                return existing;
            }


            return capitalizeFirstLetter(
                    custom
            );
        }


        String known =
                findExistingCategory(
                        selected
                );


        return known != null
                ? known
                : selected;
    }


    private String findExistingCategory(
            String candidate)
    {
        if (candidate == null)
        {
            return null;
        }


        for (String existing :
                categoryOptions)
        {
            if (MedicineCategoryCatalog
                    .OTHER
                    .equalsIgnoreCase(
                            existing
                    ))
            {
                continue;
            }


            if (existing.equalsIgnoreCase(
                    candidate
            ))
            {
                return existing;
            }
        }


        return null;
    }


    private String normalizeCategoryText(
            String value)
    {
        if (value == null)
        {
            return null;
        }


        String normalized =
                value.trim()
                        .replaceAll(
                                "\\s+",
                                " "
                        );


        return normalized.isEmpty()
                ? null
                : normalized;
    }


    private String capitalizeFirstLetter(
            String value)
    {
        if (value == null
                ||
                value.isEmpty())
        {
            return value;
        }


        if (value.length() == 1)
        {
            return value.toUpperCase(Locale.getDefault());
        }


        return Character.toUpperCase(
                value.charAt(
                        0
                )
        )
                +
                value.substring(
                        1
                );
    }


    private String textOf(
            TextView input)
    {
        if (input.getText() == null)
        {
            return "";
        }


        return input
                .getText()
                .toString();
    }

    private void showIdle()
    {
        saveProgress
                .setVisibility(
                        View.GONE
                );


        saveMedicineButton
                .setEnabled(
                        true
                );
    }


    private void showLoading()
    {
        clearErrors();


        saveProgress
                .setVisibility(
                        View.VISIBLE
                );


        saveMedicineButton
                .setEnabled(
                        false
                );
    }


    private void showValidationErrors(
            MedicineValidationService.ValidationResult result)
    {
        saveProgress
                .setVisibility(
                        View.GONE
                );


        saveMedicineButton
                .setEnabled(
                        true
                );


        if (result == null)
        {
            return;
        }


        medicineCodeLayout
                .setError(
                        result.getMedicineCodeError()
                );


        medicineNameLayout
                .setError(
                        result.getMedicineNameError()
                );


        dosageFormLayout
                .setError(
                        result.getDosageFormError()
                );


        unitOfMeasureLayout
                .setError(
                        result.getUnitOfMeasureError()
                );


        reorderLevelLayout
                .setError(
                        result.getReorderLevelError()
                );


        targetStockLevelLayout
                .setError(
                        result.getTargetStockLevelError()
                );
    }


    private void showDuplicateCode(
            String message)
    {
        saveProgress
                .setVisibility(
                        View.GONE
                );


        saveMedicineButton
                .setEnabled(
                        true
                );


        medicineCodeLayout
                .setError(
                        message
                );
    }


    private void showSuccess()
    {
        saveProgress
                .setVisibility(
                        View.GONE
                );


        Toast.makeText(
                this,
                editingMode
                        ? "Medicine updated."
                        : "Medicine saved.",
                Toast.LENGTH_SHORT
        ).show();


        finish();
    }


    private void showError(
            String message)
    {
        saveProgress
                .setVisibility(
                        View.GONE
                );


        saveMedicineButton
                .setEnabled(
                        true
                );


        formErrorText
                .setText(
                        message == null
                                ? "Unable to save medicine."
                                : message
                );


        formErrorText
                .setVisibility(
                        View.VISIBLE
                );
    }


    private void clearErrors()
    {
        medicineCodeLayout.setError(
                null
        );


        medicineNameLayout.setError(
                null
        );


        categoryLayout.setError(
                null
        );


        customCategoryLayout.setError(
                null
        );


        dosageFormLayout.setError(
                null
        );


        unitOfMeasureLayout.setError(
                null
        );


        reorderLevelLayout.setError(
                null
        );


        targetStockLevelLayout.setError(
                null
        );


        formErrorText
                .setVisibility(
                        View.GONE
                );
    }


    private void configureFormMode()
    {
        if (editingMode)
        {
            formTitleText
                    .setText(
                            "Edit Medication"
                    );


            saveMedicineButton
                    .setText(
                            "Update Medicine Record"
                    );
        }
        else
        {
            formTitleText
                    .setText(
                            "Add New Medication"
                    );


            saveMedicineButton
                    .setText(
                            "Save Medicine Record"
                    );
        }
    }


    private void observeMedicineToEdit()
    {
        viewModel
                .getMedicineToEdit()
                .observe(
                        this,
                        medicine ->
                        {
                            if (!editingMode
                                    ||
                                    medicine == null
                                    ||
                                    !populateFormFromModel)
                            {
                                return;
                            }


                            populateForm(
                                    medicine
                            );


                            populateFormFromModel =
                                    false;
                        }
                );
    }


    private void populateForm(
            Medicine medicine)
    {
        medicineCodeInput
                .setText(
                        medicine.getMedicineCode()
                );


        medicineNameInput
                .setText(
                        medicine.getMedicineName()
                );


        genericNameInput
                .setText(
                        medicine.getGenericName() == null
                                ? ""
                                : medicine.getGenericName()
                );


        strengthInput
                .setText(
                        medicine.getStrength() == null
                                ? ""
                                : medicine.getStrength()
                );


        dosageFormInput
                .setText(
                        medicine.getDosageForm()
                );


        unitOfMeasureInput
                .setText(
                        medicine.getUnitOfMeasure()
                );


        reorderLevelInput
                .setText(
                        String.valueOf(
                                medicine.getReorderLevel()
                        )
                );


        targetStockLevelInput
                .setText(
                        String.valueOf(
                                medicine.getTargetStockLevel()
                        )
                );


        currentMedicineCategory =
                medicine.getCategory();



        categoryInput.setText(
                currentMedicineCategory == null
                        ? Medicine.DEFAULT_CATEGORY
                        : currentMedicineCategory,
                false
        );


        customCategoryLayout
                .setVisibility(
                        View.GONE
                );


        rebuildCategoryOptions(
                viewModel
                        .getActiveCategories()
                        .getValue()
        );
    }
}