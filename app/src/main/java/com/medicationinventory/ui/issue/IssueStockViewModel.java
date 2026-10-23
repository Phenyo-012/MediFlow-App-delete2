package com.medicationinventory.ui.issue;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.repository.IssueStockRepository;
import com.medicationinventory.service.FefoAllocationService;



/**
 * Owns observable screen state and user actions for Issue Stock.
 */
public class IssueStockViewModel
        extends ViewModel {

    private final IssueStockRepository
            issueStockRepository;


    private final MutableLiveData<State>
            state =
            new MutableLiveData<>(
                    State.idle()
            );



    public IssueStockViewModel(
            IssueStockRepository issueStockRepository
    ) {

        this.issueStockRepository =
                issueStockRepository;
    }


    public LiveData<State> getState() {
        return state;
    }



    public void calculateFefo(
            String medicineId,
            int requestedQuantity
    ) {

        state.setValue(
                State.loading()
        );


        issueStockRepository.calculateFefo(
                medicineId,
                requestedQuantity,
                new IssueStockRepository.Callback() {

                    @Override

                    public void onSuccess(
                            FefoAllocationService.Result result
                    ) {

                        if (result.hasSufficientStock()) {

                            state.postValue(
                                    State.success(
                                            result
                                    )
                            );

                        } else {

                            state.postValue(
                                    State.insufficient(
                                            result
                                    )
                            );
                        }
                    }


                    @Override

                    public void onError(
                            Exception exception
                    ) {

                        state.postValue(
                                State.error(
                                        "Unable to calculate FEFO allocation."
                                )
                        );
                    }
                }
        );
    }



    public void reset() {

        state.setValue(
                State.idle()
        );
    }


    public static class State {

        public enum Status {
            IDLE,
            LOADING,
            SUCCESS,
            INSUFFICIENT,
            ERROR
        }


        private final Status status;

        private final FefoAllocationService.Result result;

        private final String message;



        private State(
                Status status,
                FefoAllocationService.Result result,
                String message
        ) {

            this.status =
                    status;

            this.result =
                    result;

            this.message =
                    message;
        }



        public static State idle() {

            return new State(
                    Status.IDLE,
                    null,
                    null
            );
        }



        public static State loading() {

            return new State(
                    Status.LOADING,
                    null,
                    null
            );
        }



        public static State success(
                FefoAllocationService.Result result
        ) {

            return new State(
                    Status.SUCCESS,
                    result,
                    null
            );
        }



        public static State insufficient(
                FefoAllocationService.Result result
        ) {

            return new State(
                    Status.INSUFFICIENT,
                    result,
                    null
            );
        }



        public static State error(
                String message
        ) {

            return new State(
                    Status.ERROR,
                    null,
                    message
            );
        }


        public Status getStatus() {
            return status;
        }


        public FefoAllocationService.Result getResult() {
            return result;
        }


        public String getMessage() {
            return message;
        }
    }
}