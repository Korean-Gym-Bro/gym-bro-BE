package com.kr.gym.bro.global.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exercise_feedbacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ExerciseSession session;

    private String bodyPart;
    private String status;
    private Double angleMeasured;
    private String angleIdeal;

    @Column(columnDefinition = "TEXT")
    private String message;
}
