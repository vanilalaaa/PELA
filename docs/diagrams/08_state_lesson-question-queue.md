# Figure A-8

*Figure A-8. State diagram for the short-term, in-Lesson question queue, distinct from the long-term SM-2 review card lifecycle (Figure A-7).*

Vị trí trong SE121-SRS v4.0: Appendix A, mục A.8.

```mermaid
stateDiagram-v2
    [*] --> Pending: item added to Lesson session queue
    Pending --> Correct: answered correctly
    Pending --> Requeued: answered incorrectly (attempt 1 or 2, BR-21)
    Requeued --> Pending: moved to back of queue
    Pending --> PassedWithError: 3rd consecutive incorrect attempt (error_count +1, correct answer shown, BR-21)
    Correct --> [*]
    PassedWithError --> [*]
```
