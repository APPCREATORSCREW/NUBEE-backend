package com.solux31.nubee_BE.domain.news.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solux31.nubee_BE.domain.news.dto.MainKeywordResult;
import com.solux31.nubee_BE.domain.news.dto.NewsAnalysisResult;
import com.solux31.nubee_BE.domain.news.dto.NaverNewsResponse;
import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import com.solux31.nubee_BE.domain.news.entity.Quiz;
import com.solux31.nubee_BE.domain.news.repository.DailyNewsRepository;
import com.solux31.nubee_BE.domain.news.repository.QuizRepository;
import com.solux31.nubee_BE.domain.words.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsApiService newsApiService; // 💡 여기에 fetchNewsByCategory 기능이 들어있는지 다음 세션에 체크!
    private final GeminiService geminiService;
    private final WordService wordService;
    private final DailyNewsRepository dailyNewsRepository;
    private final QuizRepository quizRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NewsTransactionHelper newsTransactionHelper;

    /**
     * [전체 데일리 뉴스 생성 파이프라인]
     * 카테고리별 분할 수집(총 8개) 및 균형 있는 적재를 처리합니다.
     */
    @Transactional
    public void executeDailyNewsWorkflow() {
        Set<String> uniqueKeywords = new HashSet<>(); // 추출된 메인 키워드 중복 제거 보관함

        // 1. 수집할 네이버 뉴스 카테고리 ID 배열 (정치/국제, 경제, 사회, IT/과학)
        String[] categories = {"100", "101", "102", "105"};

        System.out.println(" 카테고리별 네이버 API 분할 호출 시작...");

        // 2. 카테고리별로 루프 돌기
        for (String categoryId : categories) {
            String categoryName = convertCategoryName(categoryId); // 한글 카테고리명 변환 ("101" -> "경제")

            System.out.println("[" + categoryName + "] 카테고리 기사 수집 중...");
            List<NaverNewsResponse.NaverNewsItem> naverNewsList = newsApiService.fetchNewsByCategory(categoryId, 2);

            if (naverNewsList == null || naverNewsList.isEmpty()) {
                System.out.println("⚠️ " + categoryName + " 카테고리에 수집된 기사가 없습니다.");
                continue;
            }

            // 테스트 기간 크레딧 세이브를 위한 subList 장치 (원하면 주석 해제하여 사용)
            // if (naverNewsList.size() > 1) { naverNewsList = naverNewsList.subList(0, 1); }

            // 3단계: 해당 카테고리의 뉴스별 본문 확보 및 1단계 연성
            for (NaverNewsResponse.NaverNewsItem naverNews : naverNewsList) {
                try {
                    String mainKeyword = newsTransactionHelper.processSingleNews(naverNews, categoryName);
                    if (mainKeyword != null) {
                        uniqueKeywords.add(mainKeyword);
                    }
                } catch (Exception e) {
                    System.err.println("❌ [" + categoryName + "] 특정 뉴스 파이프라인 실패로 패스합니다: " + naverNews.getLink());
                    e.printStackTrace();
                }
            }
        }

        // Set을 다시 원래 메서드가 요구하는 List 형태로 변환
        List<String> mainKeywords = new ArrayList<>(uniqueKeywords);

        // -------------------------------------------------------------------------
        // 4단계: 마스터 퀴즈 & 설명 생성 (추출된 모든 Main 키워드 통합 연성)
        // -------------------------------------------------------------------------
        if (mainKeywords.isEmpty()) {
            System.out.println("성공한 메인 키워드가 없어 마스터 퀴즈 생성을 건너뜁니다.");
            return;
        }

        List<MainKeywordResult> masterResults = generateMasterKeywordsAndQuizzes(mainKeywords);

        for (MainKeywordResult master : masterResults) {
            Long savedKeywordId = wordService.updateKeywordExplanations(master.getKeyword(), master.getExplanation());

            try {
                String optionsJson = objectMapper.writeValueAsString(master.getKeywordQuiz().getOptions());
                Quiz keywordQuiz = Quiz.builder()
                        .quizType("KEYWORD")
                        .question(master.getKeywordQuiz().getQuestion())
                        .optionsJson(optionsJson)
                        .answer(master.getKeywordQuiz().getAnswer())
                        .explanation(master.getKeywordQuiz().getExplanation())
                        .keywordId(savedKeywordId)
                        .build();
                quizRepository.save(keywordQuiz);
            } catch (Exception e) {
                System.err.println("키워드 마스터 퀴즈 DB 저장 중 오류 발생");
                e.printStackTrace();
            }
        }
    }

    /**
     * 네이버 카테고리 코드를 서비스 내부에서 사용하는 한글 카테고리명으로 매핑하는 헬퍼 메서드
     */
    private String convertCategoryName(String categoryId) {
        return switch (categoryId) {
            case "100" -> "경제";
            case "101" -> "사회";
            case "102" -> "과학";
            case "105" -> "세계";
            default -> "일반상식";
        };
    }

    // 8개 메인 키워드 통합 퀴즈 및 어린이용 단어 뜻 설명 생성 (기존 로직 유지)
    private List<MainKeywordResult> generateMasterKeywordsAndQuizzes(List<String> mainKeywords) {
        System.out.println("🔑 핵심 키워드 통합 퀴즈 및 뜻 설명 연성 중... -> " + mainKeywords);

        if (mainKeywords == null || mainKeywords.isEmpty()) {
            return new ArrayList<>();
        }

        String promptTemplate =
                "너는 초등학생을 위한 어휘 교육 전문가야.\n" +
                        "제공된 [키워드 리스트]에 속한 각 단어들에 대해, 초등학교 3~4학년이 이해하기 쉬운 친절한 설명과 4지선다 객관식 퀴즈를 각각 1개씩 생성해줘.\n\n" +
                        "요구사항:\n" +
                        "1. keyword: 제공된 리스트에 있는 단어 이름 그대로 출력\n" +
                        "2. explanation: 초등학생 맞춤형 단어 뜻 설명 (예: '금리'라면 '돈을 빌릴 때 내는 이자의 비율이에요' 처럼 쉽고 친절하게)\n" +
                        "3. keywordQuiz: 단어의 정확한 의미나 쓰임을 잘 이해했는지 확인할 수 있는 4지선다 객관식 퀴즈\n\n" +
                        "[⚠️ 엄격한 예외 처리 및 탈선 방지 규칙]\n" +
                        "- 답할 때 앞뒤로 수식어나 인사말, 또는 마크다운 주석(```json ... ```)을 절대 붙이지 말고, 오직 [로 시작해서 ]로 끝나는 순수한 JSON 배열(Array) 데이터만 반환해.\n\n" +
                        "[출력 포맷 (JSON 배열 구조)]\n" +
                        "[\n" +
                        "  {\n" +
                        "    \"keyword\": \"단어명1\",\n" +
                        "    \"explanation\": \"초등학생 맞춤형 단어 뜻 설명1\",\n" +
                        "    \"keywordQuiz\": {\n" +
                        "      \"question\": \"이 단어의 알맞은 뜻은 무엇일까요?\",\n" +
                        "      \"options\": [\"보기1\", \"보기2\", \"보기3\", \"보기4\"],\n" +
                        "      \"answer\": 0,\n" +
                        "      \"explanation\": \"초등학생 눈높이에 맞춘 쉽고 명쾌한 정답 해설\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "]\n\n" +
                        "[키워드 리스트]: %s";

        String finalPrompt = String.format(promptTemplate, mainKeywords.toString());

        try {
            String jsonResponse = geminiService.callGemini(finalPrompt);
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(jsonResponse, new TypeReference<List<MainKeywordResult>>() {});
        } catch (Exception e) {
            System.err.println("마스터 키워드 통합 Gemini 분석 및 파싱 중 에러 발생");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
