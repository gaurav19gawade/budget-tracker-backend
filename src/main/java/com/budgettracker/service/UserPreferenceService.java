package com.budgettracker.service;

import com.budgettracker.dto.UserPreferenceDtos.UserPreferenceRequest;
import com.budgettracker.dto.UserPreferenceDtos.UserPreferenceResponse;
import com.budgettracker.model.User;
import com.budgettracker.model.UserPreference;
import com.budgettracker.repository.UserPreferenceRepository;
import com.budgettracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public UserPreferenceResponse getPreference(Long userId) {
        UserPreference pref = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> UserPreference.builder()
                        .reportFrequency(UserPreference.ReportFrequency.NONE)
                        .build());

        return UserPreferenceResponse.builder()
                .reportFrequency(pref.getReportFrequency())
                .build();
    }

    @Transactional
    public UserPreferenceResponse updatePreference(Long userId, UserPreferenceRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        UserPreference pref = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> UserPreference.builder().user(user).build());

        pref.setUser(user);
        pref.setReportFrequency(request.getReportFrequency());
        preferenceRepository.save(pref);

        return UserPreferenceResponse.builder()
                .reportFrequency(pref.getReportFrequency())
                .build();
    }
}
