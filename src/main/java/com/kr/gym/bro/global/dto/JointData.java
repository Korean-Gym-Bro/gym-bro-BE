package com.kr.gym.bro.global.dto;

import java.util.List;
import java.util.Map;

public record JointData(
        int frame,
        Map<String, List<Double>> joints   // "left_knee": [x, y, visibility]
) {
}
