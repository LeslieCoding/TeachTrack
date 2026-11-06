package com.leslie.teachtrack.question;

import java.util.List;

public record QuestionDetail(
        QuestionRow question,
        List<QuestionOption> options,
        QuestionAnswer answer,
        List<QuestionKnowledge> knowledge,
        List<String> abilityTags
) {
}
