package com.pela.rag.adapter.out.learner;

import com.pela.identity.application.port.in.GetCurrentUserUseCase;
import com.pela.onboarding.application.port.in.LearnerInterestsUseCase;
import com.pela.rag.application.port.out.LearnerContextPort;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Lấy sở thích từ module onboarding và múi giờ từ module identity, chỉ qua port/in của hai module đó. */
@Component
public class ModuleLearnerContextAdapter implements LearnerContextPort {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LearnerInterestsUseCase interests;
    private final GetCurrentUserUseCase users;

    public ModuleLearnerContextAdapter(LearnerInterestsUseCase interests, GetCurrentUserUseCase users) {
        this.interests = interests;
        this.users = users;
    }

    @Override
    public LearnerContext context(UUID userId) {
        return new LearnerContext(interests.interests(userId), zoneOf(users.currentUser(userId).timezone()));
    }

    private static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (Exception e) {
            return DEFAULT_ZONE;
        }
    }
}
