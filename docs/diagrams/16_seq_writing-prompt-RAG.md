# Figure A-16

*Figure A-16. Sequence diagram for "Let AI create a new prompt": cache, Top-5 retrieval, generation and fallback (UC-24, BR-7, BR-8, BR-24, BR-25).*

Vị trí trong SE121-SRS v4.0: Appendix A, mục A.16.

```mermaid
sequenceDiagram
    autonumber
    actor L as Learner
    participant App as Mobile App
    participant RAG as GenerateWritingPrompt (UC-24)
    participant EMB as Gemini Embedding
    participant VDB as pgvector (question_bank, rag_question_cache)
    participant LLM as Gemini Flash
    participant SAF as Content Safety Filter
    participant QB as Question Bank (curated prompts)
    L->>App: Choose a topic, tap "Let AI create a new prompt"
    App->>RAG: POST /v1/practice/writing/prompts/generate {topic}
    RAG->>RAG: Check daily limit (default 10)
    RAG->>EMB: embed(topic + learner CEFR level + interests)
    alt Embedding error or timeout
        RAG->>QB: Active prompt of same topic + level, not yet attempted (BR-7)
        RAG-->>App: prompt_source = bank
    else Embedding ready
        RAG->>VDB: Cache lookup: active, same topic + level + type,<br/>similarity ≥ 0.92, not served to learner in 14 days
        alt Cache hit
            VDB-->>RAG: cached prompt
            RAG->>VDB: hit_count + 1, INSERT rag_cache_serve
            RAG-->>App: prompt_source = ai_generated (cache)
        else Cache miss
            RAG->>VDB: Top-5 active Writing prompts, filtered by topic + level (iterative scan)
            VDB-->>RAG: similar prompts + similarity
            alt Top-1 < 0.65
                RAG->>QB: curated prompt of same topic + level (BR-7)
                RAG-->>App: prompt_source = bank
            else Top-1 ≥ 0.65
                RAG->>LLM: constrained prompt + 5 examples + CEFR (keep task type, word range)
                alt Timeout or invalid JSON
                    RAG->>QB: curated prompt of same topic + level (BR-7)
                    RAG-->>App: prompt_source = bank
                else Valid JSON
                    LLM-->>RAG: {prompt, task_type, min_words, max_words}
                    RAG->>SAF: toxicity / profanity check (BR-15)
                    alt Rejected
                        RAG->>QB: curated prompt of same topic + level
                        RAG-->>App: prompt_source = bank
                    else Passed
                        RAG->>VDB: INSERT rag_question_cache (expires in 30 days) + rag_cache_serve
                        RAG-->>App: prompt_source = ai_generated
                    end
                end
            end
        end
    end
    App-->>L: Show the prompt (UC-10 step 2)
```
