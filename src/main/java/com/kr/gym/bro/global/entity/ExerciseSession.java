package com.kr.gym.bro.global.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exercise_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    // ★ Gemini가 감지한 운동 이름 저장 (SQUAT, PUSHUP, UNKNOWN 등)
    private String exerciseType;

    private int overallScore;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<com.kr.gym.bro.global.entity.ExerciseFeedback> feedbacks = new ArrayList<>();

    public void addFeedback(com.kr.gym.bro.global.entity.ExerciseFeedback fb) {
        feedbacks.add(fb);
        fb.setSession(this);
    }
}
