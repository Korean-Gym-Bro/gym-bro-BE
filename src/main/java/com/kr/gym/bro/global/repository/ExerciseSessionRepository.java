package com.kr.gym.bro.global.repository;

import com.kr.gym.bro.global.entity.ExerciseSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseSessionRepository extends JpaRepository<ExerciseSession, Long> {
    List<ExerciseSession> findByUserIdOrderByCreatedAtDesc(String userId);
}
