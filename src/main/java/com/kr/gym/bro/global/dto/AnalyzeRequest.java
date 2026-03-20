package com.kr.gym.bro.global.dto;

import java.util.List;

public record AnalyzeRequest(
        String userId,
        List<JointData> frames
) {
}
