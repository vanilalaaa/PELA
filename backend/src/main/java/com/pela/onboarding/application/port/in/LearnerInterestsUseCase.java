package com.pela.onboarding.application.port.in;

import java.util.List;
import java.util.UUID;

/** Sở thích của learner (learner_profile.interests): family, school, travel, food, sport, music, movie, tech, animal, health. */
public interface LearnerInterestsUseCase {

    /** Rỗng nếu learner chưa làm khảo sát hoặc chưa có hồ sơ. */
    List<String> interests(UUID userId);
}
