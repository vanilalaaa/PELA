package com.pela.listening.application.usecase;

import com.pela.listening.application.port.in.ListeningDrafts.ClipDraft;
import com.pela.listening.application.port.in.ListeningDrafts.Issue;
import com.pela.listening.application.port.in.ListeningDrafts.LineDraft;
import com.pela.listening.application.port.in.ListeningDrafts.QuestionDraft;
import com.pela.listening.domain.ClipSpec;
import com.pela.listening.domain.ImportIssue;
import java.util.List;

/** Chuyển giữa kiểu của port/in và domain. */
final class ListeningDraftMapper {

    private ListeningDraftMapper() {
    }

    static ClipSpec toSpec(ClipDraft d) {
        return new ClipSpec(d.clipCode(), d.row(), d.title(), d.topic(), d.cefrLevel(), d.voices(),
                lines(d.lines()),
                d.questions().stream().map(q -> new ClipSpec.Question(q.row(), q.type(), q.text(), q.options(),
                        q.answer(), q.explanation())).toList());
    }

    static List<ClipSpec.Line> lines(List<LineDraft> lines) {
        return lines.stream().map(l -> new ClipSpec.Line(l.row(), l.speaker(), l.en(), l.vi())).toList();
    }

    static ClipDraft toDraft(ClipSpec s) {
        return new ClipDraft(s.clipCode(), s.row(), s.title(), s.topic(), s.cefrLevel(), s.voices(),
                s.lines().stream().map(l -> new LineDraft(l.row(), l.speaker(), l.en(), l.vi())).toList(),
                s.questions().stream().map(q -> new QuestionDraft(q.row(), q.type(), q.text(), q.options(),
                        q.answer(), q.explanation())).toList());
    }

    static Issue toIssue(ImportIssue i) {
        return new Issue(i.clipIndex(), i.row(), i.column(), i.code(), i.message());
    }
}
