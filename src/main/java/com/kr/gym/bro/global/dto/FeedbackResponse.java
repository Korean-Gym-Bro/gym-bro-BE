package com.kr.gym.bro.global.dto;

import java.util.List;

public record FeedbackResponse(
        String detected_exercise,   // ★ Gemini가 직접 판단한 운동 이름
        int exercise_score,
        String summary,
        List<PartFeedback> feedback
) {
}
