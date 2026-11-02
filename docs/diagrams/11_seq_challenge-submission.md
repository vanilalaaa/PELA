# Figure A-11

*Figure A-11. Sequence diagram for submitting a Daily or Weekly Challenge: server grading, SM-2 update, Stars and streak (BR-3, BR-10, BR-11, BR-23).*

Vị trí trong SE121-SRS v4.0: Appendix A, mục A.11.

```mermaid
sequenceDiagram
    autonumber
    actor L as Learner
    participant App as Mobile App
    participant API as SubmitChallengeAnswers
    participant SM2 as SM-2 Calculator
    participant GAM as Star / Streak / Badge policies
    participant DB as Supabase PostgreSQL
    L->>App: Finish all items
    App->>API: POST /v1/challenges/{id}/complete (raw answers, client-generated ids)
    API->>DB: Load session and items, check ownership (SEC-4)
    alt Session already completed
        API-->>App: Previous summary, nothing recomputed (UC-08 E1)
    else In progress
        API->>API: Grade every item on the server from the stored payload (BR-23)
        loop Each reviewed card
            API->>SM2: apply(card, correct → q = 5, incorrect → q = 2)
            SM2-->>API: EF (≥ 1.3), interval, repetitions, next review date, reinforcement flag
        end
        API->>DB: UPDATE review_card, challenge_item
        API->>GAM: rate(accuracy, session type) (BR-16, BR-17)
        GAM-->>API: Stars (0-5, or 0-6 for Weekly)
        API->>DB: Session completed, INSERT star_event (unique per session)
        opt A Topic is locked (BR-20)
            API->>DB: stars_earned_since_lock += Stars, grant one attempt at 30
        end
        API->>GAM: updateStreak(learner, local date) (BR-11, BR-27)
        GAM-->>API: New streak, newly earned badges
        API->>DB: UPDATE streak, INSERT user_badge, user_daily_activity
        API-->>App: Result (Stars, streak, badges)
    end
    App-->>L: Show results
```
