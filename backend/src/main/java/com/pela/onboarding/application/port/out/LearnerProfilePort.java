package com.pela.onboarding.application.port.out;

import java.util.List;
import java.util.UUID;

/** learner_profile: kết quả khảo sát định hướng (UC-02). */
public interface LearnerProfilePort {

    List<String> interests(UUID userId);
}
