package com.kr.gym.bro.global.service;

import com.kr.gym.bro.global.entity.ExerciseFeedback;
import com.kr.gym.bro.global.entity.ExerciseSession;
import com.kr.gym.bro.global.dto.FeedbackResponse;
import com.kr.gym.bro.global.repository.ExerciseSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // 이거 추가

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionSaveService {

    private final ExerciseSessionRepository sessionRepo;

    @Transactional
    public ExerciseSession save(String userId, FeedbackResponse feedback) {
        ExerciseSession session = ExerciseSession.builder()
                .userId(userId)
                // ★ Gemini가 감지한 운동 이름을 그대로 저장
                .exerciseType(feedback.detected_exercise())
                .overallScore(feedback.exercise_score())
                .summary(feedback.summary())
                .build();

        feedback.feedback().forEach(pf -> session.addFeedback(
                ExerciseFeedback.builder()
                        .bodyPart(pf.body_part())
                        .status(pf.status())
                        .angleMeasured(pf.angle_measured())
                        .angleIdeal(pf.angle_ideal())
                        .message(pf.message())
                        .build()
        ));

        return sessionRepo.save(session);
    }

    public List<ExerciseSession> findByUser(String userId) {
        return sessionRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
