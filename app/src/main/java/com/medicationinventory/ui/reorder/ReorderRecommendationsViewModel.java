package com.medicationinventory.ui.reorder;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.medicationinventory.data.repository.AuthenticationRepository;
import com.medicationinventory.data.repository.ReorderRecommendationsRepository;
import com.medicationinventory.data.repository.UserRepository;

import com.medicationinventory.model.domain.ReorderRecommendationListItem;
import com.medicationinventory.model.domain.UserProfile;

import java.util.List;



/**
 * Exposes active user profile, locally calculated reorder recommendations, and repository errors to
 * the replenishment screen. Recommendation quantities come from the shared reorder service and
 * usable stock rules; this ViewModel displays guidance and does not create purchase orders or adjust
 * stock.
 */
public class ReorderRecommendationsViewModel
        extends ViewModel
{
    private final ReorderRecommendationsRepository
            reorderRecommendationsRepository;


    private final LiveData<UserProfile>
            userProfile;


    private final LiveData<
            List<ReorderRecommendationListItem>>
            recommendations;


    private final LiveData<String>
            error;



    public ReorderRecommendationsViewModel(
            AuthenticationRepository authenticationRepository,
            UserRepository userRepository,
            ReorderRecommendationsRepository
                    reorderRecommendationsRepository)
    {
        if (authenticationRepository == null
                ||
                userRepository == null
                ||
                reorderRecommendationsRepository == null)
        {
            throw new IllegalArgumentException(
                    "Reorder recommendation dependencies are required."
            );
        }


        this.reorderRecommendationsRepository =
                reorderRecommendationsRepository;


        String uid =
                authenticationRepository
                        .getCurrentUserId();


        if (uid == null)
        {
            userProfile =
                    new MutableLiveData<>(
                            null
                    );
        }
        else
        {
            userProfile =
                    userRepository
                            .observeUser(
                                    uid
                            );
        }


        recommendations =
                reorderRecommendationsRepository
                        .observeRecommendations();


        error =
                reorderRecommendationsRepository
                        .observeError();
    }


    public LiveData<UserProfile>
    getUserProfile()
    {
        return userProfile;
    }


    public LiveData<List<ReorderRecommendationListItem>>
    getRecommendations()
    {
        return recommendations;
    }


    public LiveData<String>
    getError()
    {
        return error;
    }



    public void retry()
    {
        reorderRecommendationsRepository
                .refresh();
    }
}