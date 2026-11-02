# Figure A-10

*Figure A-10. State diagram for the per-Topic Final Test attempt and lock cycle (BR-20, one attempt per unlock cycle), tracked by TOPIC_PROGRESS.*

Vị trí trong SE121-SRS v4.0: Appendix A, mục A.10.

```mermaid
stateDiagram-v2
    [*] --> Available: Topic unlocked (previous Topic completed, or starting level)
    Available --> Attempt1: all Lessons completed, Final Test started
    Attempt1 --> Completed: score >= 80%
    Attempt1 --> Attempt2: score < 80% (immediate retry, new random set)
    Attempt2 --> Completed: score >= 80%
    Attempt2 --> Attempt3: score < 80% (immediate retry, new random set)
    Attempt3 --> Completed: score >= 80%
    Attempt3 --> Locked: score < 80% (3rd failure, BR-20)
    Locked --> UnlockedAttempt: stars_earned_since_lock >= 30 (AS-5), counter reset to 0
    UnlockedAttempt --> Completed: score >= 80%
    UnlockedAttempt --> Locked: score < 80%
    Completed --> [*]: next Topic unlocked, vocabulary graduates into REVIEW_CARD
    note right of Locked
        Learner keeps earning Stars from Daily/Weekly,
        Writing and Listening: stars_earned_since_lock += stars
    end note
    note right of UnlockedAttempt
        Exactly one attempt per unlock cycle (BR-20)
    end note
    note left of Completed
        Words with error_count >= 2 are labeled weak (EF = 2.18, AS-13)
    end note
```
