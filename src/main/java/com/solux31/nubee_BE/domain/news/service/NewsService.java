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

import org.jsoup.Jsoup;

@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsApiService newsApiService;
    private final GeminiService geminiService;
    private final WordService wordService;
    private final DailyNewsRepository dailyNewsRepository;
    private final QuizRepository quizRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NewsTransactionHelper newsTransactionHelper;

    /**
     * [전체 데일리 뉴스 생성 파이프라인]
     * 매일 특정 시간 배치 스케줄러나 컨트롤러에 의해 호출됩니다.
     */
    @Transactional
    public void executeDailyNewsWorkflow() {
        // 0. 네이버 뉴스 리스트를 받아옵니다.
        System.out.println("네이버 오픈 API 호출 중...");
        List<NaverNewsResponse.NaverNewsItem> naverNewsList = newsApiService.fetchDailyEightNews();
        Set<String> uniqueKeywords = new HashSet<>(); //중복 해결하기
        System.out.println("네이버에서 기사 " + (naverNewsList != null ? naverNewsList.size() : 0) + "개를 수집했습니다.");

        // 💡 테스트 기간 동안 크레딧을 아끼려면 여기에 subList(0, 1) 적용 가능!
        if (naverNewsList != null && naverNewsList.size() > 1) {
            naverNewsList = naverNewsList.subList(0, 1);
        }

        // 1단계: 뉴스별 본문 확보 및 개별 AI 연성 + DB 저장
        for (NaverNewsResponse.NaverNewsItem naverNews : naverNewsList) {
            try {
                // 외부 빈(Bean)인 헬퍼를 통해 호출하므로 @Transactional(REQUIRES_NEW)가 작동
                String mainKeyword = newsTransactionHelper.processSingleNews(naverNews);
                if (mainKeyword != null) {
                    uniqueKeywords.add(mainKeyword);
                }
            } catch (Exception e) {
                // 특정 뉴스가 터져도 전체 판이 깨지지 않고, 로그만 찍은 뒤 다음 뉴스로 넘어감
                System.err.println("❌ 특정 뉴스 파이프라인 실패로 패스합니다: " + naverNews.getLink());
                e.printStackTrace();
            }
        }

        // Set을 다시 원래 메서드가 요구하는 List 형태로 변환해서 넘겨줌
        List<String> mainKeywords = new ArrayList<>(uniqueKeywords);

        // -------------------------------------------------------------------------
        // 2단계: 마스터 퀴즈 & 설명 생성 (8개 추출된 Main 키워드 통합)
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

    // 8개 매인 키워드 통합 퀴즈 및 어린이용 단어 뜻 설명 생성
    private List<MainKeywordResult> generateMasterKeywordsAndQuizzes(List<String> mainKeywords) {
        System.out.println(" 8개 메인 키워드 통합 퀴즈 연성 중... -> " + mainKeywords);

        // 1. 방어 코드: 혹시 들어온 키워드 리스트가 비어있다면 AI를 호출하지 않고 빈 리스트 반환
        if (mainKeywords == null || mainKeywords.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 2단계 통합 연성 전용 프롬프트 템플릿 작성
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
                        "      \"answer\": 0,\n" + // 정답 번호 (0~3 인덱스)
                        "      \"explanation\": \"초등학생 눈높이에 맞춘 쉽고 명쾌한 정답 해설\"\n" +
                        "    }\n" +
                        "  }\n" +
                        "]\n\n" +

                        "[키워드 리스트]: %s";

        // 3. %s 자리에 List<String>을 주입 (자바 List는 toString() 시 자동으로 ["경제", "사회", ...] 바인딩)
        String finalPrompt = String.format(promptTemplate, mainKeywords.toString());

        try {
            // 4. Gemini API를 호출하여 결과 문자열 획득
            String jsonResponse = geminiService.callGemini(finalPrompt);

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                return new ArrayList<>();
            }

            // 5. 중요: JSON Array 구조(`[...]`)를 자바의 List<MainKeywordResult> 구조로 안전하게 역직렬화 파싱
            return objectMapper.readValue(jsonResponse, new TypeReference<List<MainKeywordResult>>() {
            });

        } catch (Exception e) {
            System.err.println("마스터 키워드 통합 Gemini 분석 및 파싱 중 에러 발생");
            e.printStackTrace();
            // 에러가 나더라도 상위 배치 프로세스가 튕겨서 터지지 않도록 빈 바구니 반환
            return new ArrayList<>();
        }
    }
}
