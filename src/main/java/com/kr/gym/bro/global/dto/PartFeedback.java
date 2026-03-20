package com.kr.gym.bro.global.dto;

public record PartFeedback(
        String body_part,
        String status,           // GOOD | WARNING | CAUTION
        Double angle_measured,
        String angle_ideal,
        String message
) {
}
