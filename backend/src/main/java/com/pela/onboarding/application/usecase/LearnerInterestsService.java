package com.pela.onboarding.application.usecase;

import com.pela.onboarding.application.port.in.LearnerInterestsUseCase;
import com.pela.onboarding.application.port.out.LearnerProfilePort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LearnerInterestsService implements LearnerInterestsUseCase {

    private final LearnerProfilePort profiles;

    public LearnerInterestsService(LearnerProfilePort profiles) {
        this.profiles = profiles;
    }

    @Override
    public List<String> interests(UUID userId) {
        return profiles.interests(userId);
    }
}
