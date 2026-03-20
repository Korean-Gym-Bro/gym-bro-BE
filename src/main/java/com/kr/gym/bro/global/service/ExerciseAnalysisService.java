package com.kr.gym.bro.global.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kr.gym.bro.global.dto.FeedbackResponse;
import com.kr.gym.bro.global.dto.JointData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExerciseAnalysisService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent";

    // ─────────────────────────────────────────────────────────────────
    // 1. 메인 진입점: 전처리 → Gemini 호출
    //    - exerciseType 파라미터 제거: Gemini가 스스로 판단
    // ─────────────────────────────────────────────────────────────────

    public FeedbackResponse analyze(List<JointData> frames) {
        String preprocessedJson = preprocess(frames);
        log.debug("[전처리 결과]\n{}", preprocessedJson);
        return callGemini(preprocessedJson);
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. 범용 전처리
    //    - 운동 종류에 무관하게 모든 주요 관절 각도를 계산
    //    - 여러 시점(Start / Peak / End)의 스냅샷 포함
    //    - Gemini가 이 데이터만 보고 운동을 판단하도록 설계
    // ─────────────────────────────────────────────────────────────────

    public String preprocess(List<JointData> frames) {
        if (frames == null || frames.isEmpty()) return "{}";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_frames", frames.size());

        // ── 대표 3개 스냅샷 추출 ────────────────────────────────────
        // • start  : 첫 번째 유효 프레임 (운동 시작 자세)
        // • peak   : 힙 Y가 가장 높은(화면상 아래) 프레임 (최대 수축/하강 지점)
        // • end    : 마지막 유효 프레임 (복귀 자세)
        JointData startFrame = getValidFrame(frames, 0);
        JointData peakFrame  = findPeakFrame(frames, "left_hip");
        JointData endFrame   = getValidFrame(frames, frames.size() - 1);

        data.put("snapshot_start", buildSnapshot(startFrame));
        data.put("snapshot_peak",  buildSnapshot(peakFrame));
        data.put("snapshot_end",   buildSnapshot(endFrame));

        // ── 움직임 범위(ROM) 요약 ────────────────────────────────────
        // 힙·어깨·손목의 Y 이동량 → 어떤 관절이 얼마나 움직였는지 힌트
        data.put("range_of_motion", buildRangeOfMotion(frames));

        return toJson(data);
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. 스냅샷 빌더: 한 프레임의 모든 관절 각도 계산
    // ─────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSnapshot(JointData frame) {
        Map<String, Object> snap = new LinkedHashMap<>();
        Map<String, Object> joints = (Map<String, Object>)(Map<?,?>) frame.joints();

        snap.put("frame_index", frame.frame());

        // ── 상체 ──────────────────────────────────────────────────
        // 왼쪽 팔꿈치 (어깨-팔꿈치-손목)
        snap.put("left_elbow_angle",   safeAngle(joints, "left_shoulder",  "left_elbow",  "left_wrist"));
        snap.put("right_elbow_angle",  safeAngle(joints, "right_shoulder", "right_elbow", "right_wrist"));

        // 어깨 각도 (엉덩이-어깨-팔꿈치)
        snap.put("left_shoulder_angle",  safeAngle(joints, "left_hip",  "left_shoulder",  "left_elbow"));
        snap.put("right_shoulder_angle", safeAngle(joints, "right_hip", "right_shoulder", "right_elbow"));

        // ── 몸통 ──────────────────────────────────────────────────
        // 등 기울기 (어깨-힙 벡터가 수직에서 몇 도 기울었는지)
        snap.put("back_lean_deg", calcBackLean(joints));

        // 몸통 정렬 (어깨-힙-발목 직선도, 180 = 완전 직선)
        snap.put("torso_alignment_left",  safeAngle(joints, "left_shoulder",  "left_hip",  "left_ankle"));
        snap.put("torso_alignment_right", safeAngle(joints, "right_shoulder", "right_hip", "right_ankle"));

        // ── 하체 ──────────────────────────────────────────────────
        // 힙 각도 (어깨-힙-무릎)
        snap.put("left_hip_angle",   safeAngle(joints, "left_shoulder",  "left_hip",  "left_knee"));
        snap.put("right_hip_angle",  safeAngle(joints, "right_shoulder", "right_hip", "right_knee"));

        // 무릎 각도 (힙-무릎-발목)
        snap.put("left_knee_angle",  safeAngle(joints, "left_hip",  "left_knee",  "left_ankle"));
        snap.put("right_knee_angle", safeAngle(joints, "right_hip", "right_knee", "right_ankle"));

        // 발목 각도 (무릎-발목-발끝 - visibility 낮을 수 있으므로 null 허용)
        snap.put("left_ankle_angle",  safeAngle(joints, "left_knee",  "left_ankle",  "left_foot_index"));
        snap.put("right_ankle_angle", safeAngle(joints, "right_knee", "right_ankle", "right_foot_index"));

        // ── 좌우 대칭성 ───────────────────────────────────────────
        // 어깨 수평도 (Y 차이, 0 = 완전 수평)
        if (joints.containsKey("left_shoulder") && joints.containsKey("right_shoulder")) {
            double dy = Math.abs(
                getY(joints, "left_shoulder") - getY(joints, "right_shoulder")
            );
            snap.put("shoulder_level_diff", Math.round(dy * 1000.0) / 1000.0);
        }
        // 엉덩이 수평도
        if (joints.containsKey("left_hip") && joints.containsKey("right_hip")) {
            double dy = Math.abs(
                getY(joints, "left_hip") - getY(joints, "right_hip")
            );
            snap.put("hip_level_diff", Math.round(dy * 1000.0) / 1000.0);
        }

        // ── 무릎 발끝 초과 ─────────────────────────────────────────
        // 무릎 x - 발목 x > 0 이면 무릎이 발 앞으로 나온 것
        if (joints.containsKey("left_knee") && joints.containsKey("left_ankle")) {
            double diff = getX(joints, "left_knee") - getX(joints, "left_ankle");
            snap.put("left_knee_over_toe", Math.round(diff * 1000.0) / 1000.0);
        }
        if (joints.containsKey("right_knee") && joints.containsKey("right_ankle")) {
            double diff = getX(joints, "right_knee") - getX(joints, "right_ankle");
            snap.put("right_knee_over_toe", Math.round(diff * 1000.0) / 1000.0);
        }

        // ── 손목-힙 거리 (데드리프트, 바벨 위치 추정용) ──────────────
        if (joints.containsKey("left_wrist") && joints.containsKey("left_hip")) {
            double dx = getX(joints, "left_wrist") - getX(joints, "left_hip");
            snap.put("left_wrist_hip_dist_x", Math.round(dx * 1000.0) / 1000.0);
        }

        // ── 손 너비 vs 어깨 너비 (푸시업 그립 확인용) ────────────────
        if (joints.containsKey("left_wrist") && joints.containsKey("right_wrist")
            && joints.containsKey("left_shoulder") && joints.containsKey("right_shoulder")) {
            double handW     = Math.abs(getX(joints, "left_wrist") - getX(joints, "right_wrist"));
            double shoulderW = Math.abs(getX(joints, "left_shoulder") - getX(joints, "right_shoulder"));
            if (shoulderW > 0)
                snap.put("hand_width_vs_shoulder", Math.round((handW / shoulderW) * 100.0) / 100.0);
        }

        return snap;
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. 움직임 범위(ROM) 요약
    //    - 전체 프레임에서 각 관절의 Y 최대-최소 변화량 계산
    //    - 어떤 관절이 크게 움직였는지 Gemini에게 힌트 제공
    // ─────────────────────────────────────────────────────────────────

    private Map<String, Object> buildRangeOfMotion(List<JointData> frames) {
        String[] targets = {
            "left_hip", "right_hip",
            "left_knee", "right_knee",
            "left_shoulder", "right_shoulder",
            "left_wrist", "right_wrist",
            "left_elbow", "right_elbow"
        };

        Map<String, Object> rom = new LinkedHashMap<>();
        for (String joint : targets) {
            double maxY = Double.MIN_VALUE, minY = Double.MAX_VALUE;
            boolean found = false;
            for (JointData f : frames) {
                if (f.joints().containsKey(joint)) {
                    double y = f.joints().get(joint).get(1);
                    if (y > maxY) maxY = y;
                    if (y < minY) minY = y;
                    found = true;
                }
            }
            if (found) {
                rom.put(joint + "_range_y", Math.round((maxY - minY) * 1000.0) / 1000.0);
            }
        }
        return rom;
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. Gemini API 호출
    // ─────────────────────────────────────────────────────────────────

    private FeedbackResponse callGemini(String processedJson) {
        String prompt = buildPrompt(processedJson);

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
            )),
            "generationConfig", Map.of(
                "temperature", 0.2,
                "responseMimeType", "application/json"
            )
        );

        try {
            Map<?, ?> raw = webClientBuilder.build()
                .post()
                .uri(GEMINI_URL + "?key=" + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

            String jsonText = extractGeminiText(raw);
            log.debug("[Gemini 응답]\n{}", jsonText);

            return objectMapper.readValue(jsonText, FeedbackResponse.class);

        } catch (Exception e) {
            log.error("Gemini 호출 실패", e);
            throw new RuntimeException("AI 분석 중 오류 발생: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 6. 범용 프롬프트
    //    - 운동 종류를 명시하지 않고, Gemini가 스스로 판단하도록 설계
    //    - detected_exercise 필드를 응답에 포함하도록 요구
    // ─────────────────────────────────────────────────────────────────

    private String buildPrompt(String dataJson) {
        return """
            당신은 10년 경력의 전문 퍼스널 트레이너입니다.
            아래는 운동 영상에서 추출한 관절 각도 데이터입니다.
            
            ──────────── 데이터 설명 ────────────
            • snapshot_start : 운동 시작 자세의 관절 각도
            • snapshot_peak  : 가장 많이 수축/하강한 시점의 관절 각도
            • snapshot_end   : 운동 복귀 자세의 관절 각도
            • range_of_motion: 각 관절이 전체 영상에서 움직인 Y축 범위
            • *_angle        : 세 관절이 이루는 각도(도), null은 감지 안 됨
            • back_lean_deg  : 등이 수직에서 기울어진 각도 (0=직립, 90=수평)
            • knee_over_toe  : 무릎이 발끝보다 앞으로 나온 정도 (양수=초과)
            • hand_width_vs_shoulder: 손 너비 / 어깨 너비 비율
            ──────────────────────────────────────
            
            ──────────── 관절 각도 데이터 ────────────
            %s
            ──────────────────────────────────────────
            
            ──────────── 분석 지침 ────────────
            1. 위 데이터를 보고 어떤 운동인지 먼저 판단하세요.
               (스쿼트, 푸시업, 런지, 데드리프트, 플랭크, 버피, 숄더프레스 등 무엇이든)
            2. 판단한 운동의 올바른 수행 기준에 따라 각 부위를 평가하세요.
            3. 운동을 특정할 수 없다면 detected_exercise를 "UNKNOWN"으로 하고
               관절 상태만 평가하세요.
            ──────────────────────────────────────
            
            ──────────── 응답 규칙 ────────────
            • 반드시 아래 JSON 형식으로만 응답 (다른 텍스트 절대 금지)
            • status는 반드시 GOOD / WARNING / CAUTION 중 하나
            • message는 구체적인 한국어 교정 조언 (1~2문장)
            • body_part는 한국어 신체 부위명
            ──────────────────────────────────────
            
            {
              "detected_exercise": "<감지된 운동 이름, 예: SQUAT / PUSHUP / LUNGE / DEADLIFT / UNKNOWN>",
              "exercise_score": <0~100 정수>,
              "summary": "<전체 운동 자세 종합 평가, 2~3문장 한국어>",
              "feedback": [
                {
                  "body_part": "<신체 부위명>",
                  "status": "<GOOD|WARNING|CAUTION>",
                  "angle_measured": <측정된 각도 숫자, 없으면 null>,
                  "angle_ideal": "<이상적인 각도 범위 문자열, 예: 80~100도>",
                  "message": "<구체적 교정 조언>"
                }
              ]
            }
            """.formatted(dataJson);
    }

    // ─────────────────────────────────────────────────────────────────
    // 7. My Page용 Gemini 총평 생성
    // ─────────────────────────────────────────────────────────────────

    public String generateUserSummary(String userId, int totalSessions,
                                      double avgScore, List<Map<String, Object>> weakPoints) {
        String weakStr = weakPoints.stream()
            .map(w -> "  - %s: %s회 지적됨 (불량률 %s%%)".formatted(
                w.get("bodyPart"), w.get("badCount"), w.get("badRatio")))
            .collect(java.util.stream.Collectors.joining("\n"));

        String prompt = """
            당신은 전문 퍼스널 트레이너입니다.
            아래는 사용자의 누적 운동 자세 분석 통계입니다.
            
            - 총 분석 세션 수: %d회
            - 평균 자세 점수: %d점
            - 자주 지적된 취약 부위:
            %s
            
            이 데이터를 바탕으로 사용자에게 종합적인 피드백을 한국어 3~5문장으로 작성하세요.
            어떤 부위를 집중적으로 개선해야 하는지, 전반적인 자세 수준은 어떤지 포함하세요.
            JSON이나 마크다운 없이 순수 텍스트로만 응답하세요.
            """.formatted(totalSessions, Math.round(avgScore), weakStr);

        Map<String, Object> requestBody = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
            )),
            "generationConfig", Map.of("temperature", 0.4)
        );

        try {
            Map<?, ?> raw = webClientBuilder.build()
                .post()
                .uri(GEMINI_URL + "?key=" + apiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            return extractGeminiText(raw).trim();
        } catch (Exception e) {
            log.error("총평 생성 실패", e);
            return "총평을 생성하는 중 오류가 발생했습니다.";
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 8. 유틸리티
    // ─────────────────────────────────────────────────────────────────

    public double calcAngle(List<Double> a, List<Double> b, List<Double> c) {
        double rad = Math.atan2(c.get(1) - b.get(1), c.get(0) - b.get(0))
                   - Math.atan2(a.get(1) - b.get(1), a.get(0) - b.get(0));
        double deg = Math.abs(Math.toDegrees(rad));
        return deg > 180.0 ? 360.0 - deg : deg;
    }

    @SuppressWarnings("unchecked")
    private Object safeAngle(Map<String, Object> joints, String a, String b, String c) {
        if (!joints.containsKey(a) || !joints.containsKey(b) || !joints.containsKey(c)) return null;
        double angle = calcAngle(
            (List<Double>) joints.get(a),
            (List<Double>) joints.get(b),
            (List<Double>) joints.get(c)
        );
        return Math.round(angle * 10.0) / 10.0;
    }

    @SuppressWarnings("unchecked")
    private Object calcBackLean(Map<String, Object> joints) {
        if (!joints.containsKey("left_shoulder") || !joints.containsKey("left_hip")) return null;
        List<Double> sh = (List<Double>) joints.get("left_shoulder");
        List<Double> hp = (List<Double>) joints.get("left_hip");
        double deg = Math.toDegrees(
            Math.atan2(Math.abs(sh.get(0) - hp.get(0)), Math.abs(sh.get(1) - hp.get(1)))
        );
        return Math.round(deg * 10.0) / 10.0;
    }

    private JointData findPeakFrame(List<JointData> frames, String joint) {
        return frames.stream()
            .filter(f -> f.joints().containsKey(joint))
            .max(Comparator.comparingDouble(f -> f.joints().get(joint).get(1)))
            .orElse(frames.get(frames.size() / 2));
    }

    private JointData getValidFrame(List<JointData> frames, int idx) {
        JointData f = frames.get(idx);
        return f.joints().isEmpty()
            ? frames.stream().filter(fr -> !fr.joints().isEmpty()).findFirst().orElse(f)
            : f;
    }

    @SuppressWarnings("unchecked")
    private double getX(Map<String, Object> joints, String name) {
        return ((List<Double>) joints.get(name)).get(0);
    }

    @SuppressWarnings("unchecked")
    private double getY(Map<String, Object> joints, String name) {
        return ((List<Double>) joints.get(name)).get(1);
    }

    @SuppressWarnings("unchecked")
    private String extractGeminiText(Map<?, ?> response) {
        List<Map<?, ?>> candidates = (List<Map<?, ?>>) response.get("candidates");
        Map<?, ?> content = (Map<?, ?>) candidates.get(0).get("content");
        List<Map<?, ?>> parts = (List<Map<?, ?>>) content.get("parts");
        return (String) parts.get(0).get("text");
    }

    private String toJson(Object obj) {
        try { return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj); }
        catch (Exception e) { return obj.toString(); }
    }
}
