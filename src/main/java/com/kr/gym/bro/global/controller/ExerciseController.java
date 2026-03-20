package com.kr.gym.bro.global.controller;
import com.kr.gym.bro.global.dto.*;
import com.kr.gym.bro.global.service.*;
import com.kr.gym.bro.global.dto.AnalyzeRequest;
import com.kr.gym.bro.global.dto.FeedbackResponse;
import com.kr.gym.bro.global.service.ExerciseAnalysisService;
import com.kr.gym.bro.global.service.SessionSaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exercise")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ExerciseController {

    private final ExerciseAnalysisService analysisService;
    private final SessionSaveService saveService;

    /**
     * POST /api/exercise/analyze
     * Body: { "userId": "user1", "frames": [...] }
     * ★ exerciseType 파라미터 없음 - Gemini가 자동 감지
     */
    @PostMapping("/analyze")
    public ResponseEntity<FeedbackResponse> analyze(@RequestBody AnalyzeRequest req) {

        if (req.frames() == null || req.frames().size() < 5) {
            return ResponseEntity.badRequest().build();
        }

        // 1. 전처리 + Gemini 분석 (운동 종류 자동 감지)
        FeedbackResponse feedback = analysisService.analyze(req.frames());

        // 2. MySQL 저장 (감지된 운동 이름 포함)
        saveService.save(req.userId(), feedback);

        return ResponseEntity.ok(feedback);
    }

    /**
     * GET /api/exercise/history?userId=user1
     */
    @GetMapping("/history")
    public ResponseEntity<?> history(@RequestParam String userId) {
        return ResponseEntity.ok(saveService.findByUser(userId));
    }
}
