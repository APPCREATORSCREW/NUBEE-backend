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
import java.util.List;
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

    /**
     * [전체 데일리 뉴스 생성 파이프라인]
     * 매일 특정 시간 배치 스케줄러나 컨트롤러에 의해 호출됩니다.
     */
    @Transactional
    public void executeDailyNewsWorkflow() {
        // 0. 원래 이름인 NaverNewsItem 리스트를 그대로 받아옵니다.
        List<NaverNewsResponse.NaverNewsItem> naverNewsList = newsApiService.fetchDailyEightNews();

        if (naverNewsList != null && naverNewsList.size() > 1) {
            naverNewsList = naverNewsList.subList(0, 1);
        }

        List<NewsAnalysisResult> analysisResults = new ArrayList<>();
        List<String> mainKeywords = new ArrayList<>();

        // 1단계: 뉴스별 본문 확보(크롤링 or Fallback) 및 개별 AI 연성 + DB 저장
        // 변수 타입을 NaverNewsItem으로 유지
        for (NaverNewsResponse.NaverNewsItem naverNews : naverNewsList) {
            String articleBody = "";

            try {
                // [1순위] 기사 원문 URL 웹 크롤링 시도 (타임아웃 5초)
                var document = Jsoup.connect(naverNews.getLink()).timeout(5000).get();
                articleBody = document.body().text();

                if (articleBody == null || articleBody.trim().isEmpty()) {
                    throw new Exception("크롤링된 본문이 비어있습니다.");
                }
            } catch (Exception e) {
                // [2순위] 크롤링 실패 시 네이버 description으로 우아하게 대체
                System.out.println("⚠️ 기사 크롤링 실패로 description 대체 실행: " + naverNews.getLink());
                articleBody = naverNews.getDescription();
            }

            // Gemini 연성 실행 (요약, 키워드, 뉴스 퀴즈 생성)
            NewsAnalysisResult result = analyzeSingleNews(naverNews, articleBody);

            if (result != null) {
                analysisResults.add(result);
                mainKeywords.add(result.getMainKeyword());

                try {
                    // [3단계 선행] 외래키(newsId) 제약조건 충족을 위해 DailyNews 엔티티 먼저 빌드 및 저장
                    DailyNews news = DailyNews.builder()
                            .title(naverNews.getTitle())
                            .originalUrl(naverNews.getLink())
                            .summary(result.getSummary())
                            .mainKeyword(result.getMainKeyword())
                            .category(naverNews.getCategory())
                            .build();
                    DailyNews savedNews = dailyNewsRepository.save(news);

                    // [Word 도메인 협업] 확보된 newsId를 실어서 키워드 중복 체크 및 저장 위임
                    wordService.saveKeywords(result.getMainKeyword(), result.getSubKeywords(), savedNews.getNewsId());

                    // 뉴스 관련 객관식 퀴즈 적재 (options 배열은 JSON String으로 변환)
                    String optionsJson = objectMapper.writeValueAsString(result.getNewsQuiz().getOptions());
                    Quiz newsQuiz = Quiz.builder()
                            .quizType("NEWS")
                            .question(result.getNewsQuiz().getQuestion())
                            .optionsJson(optionsJson)
                            .answer(result.getNewsQuiz().getAnswer())
                            .explanation(result.getNewsQuiz().getExplanation())
                            .newsId(savedNews.getNewsId())
                            .build();
                    quizRepository.save(newsQuiz);

                } catch (Exception e) {
                    System.err.println("❌ 뉴스 데이터 및 퀴즈 DB 저장 중 오류 발생");
                    e.printStackTrace();
                }
            }
        }

        // -------------------------------------------------------------------------
        // 2단계: 마스터 퀴즈 & 설명 생성 (8개 추출된 Main 키워드 통합)
        // -------------------------------------------------------------------------
        List<MainKeywordResult> masterResults = generateMasterKeywordsAndQuizzes(mainKeywords);

        for (MainKeywordResult master : masterResults) {
            // [Word 도메인 협업] 단어 테이블에 새로 생성된 초등학생용 설명글(뜻) 업데이트 위임
            wordService.updateKeywordExplanations(master.getKeyword(), master.getExplanation());

            try {
                // 마스터 키워드 퀴즈 적재
                String optionsJson = objectMapper.writeValueAsString(master.getKeywordQuiz().getOptions());
                Quiz keywordQuiz = Quiz.builder()
                        .quizType("KEYWORD")
                        .question(master.getKeywordQuiz().getQuestion())
                        .optionsJson(optionsJson)
                        .answer(master.getKeywordQuiz().getAnswer())
                        .explanation(master.getKeywordQuiz().getExplanation())
                        // .keywordId(...) // 특정 키워드 고유 ID 연동이 필요하다면 조회 후 세팅 가능
                        .build();
                quizRepository.save(keywordQuiz);
            } catch (Exception e) {
                System.err.println("❌ 키워드 마스터 퀴즈 DB 저장 중 오류 발생");
            }
        }
    }

    private NewsAnalysisResult analyzeSingleNews(NaverNewsResponse.NaverNewsItem naverNews, String articleBody) {
        String prompt = String.format(
                "너는 초등학생을 위한 뉴스 교육 서비스의 AI 콘텐츠 생성기야.\n" +
                        "제공되는 [뉴스 링크]에 접속하여 전체 본문을 읽고, 다음 요구사항에 맞춰 반드시 JSON 포맷으로만 답변해줘.\n\n" +

                        "요구사항:\n" +
                        "1. summary: 기사 전체 본문을 읽고 초등학교 3~4학년 눈높이에 맞춘 쉽고 친절한 3~5줄 요약문\n" +
                        "2. mainKeyword: 기사의 핵심이 되는 메인 키워드 딱 1개 (단어만 출력)\n" +
                        "3. subKeywords: 기사 내에서 초등학생이 추가로 알면 좋은 중요 어휘/시사용어 3개 리스트\n" +
                        "4. newsQuiz: 기사 내용을 잘 읽었는지 확인할 수 있는 독해력 확인용 4지선다 객관식 퀴즈 1개 (문제, 선지 리스트, 정답 번호, 초등용 쉬운 해설 포함)\n\n" +

                        "[⚠️ 엄격한 예외 처리 및 탈선 방지 규칙]\n" +
                        "- 만약 보안이나 사이트 차단 등으로 인해 [뉴스 링크]의 본문을 읽을 수 없다면, 절대 에러를 뱉지 말고 함께 제공한 [대체 텍스트]를 바탕으로 요구사항을 완성해줘.\n" +
                        "- 기사 본문 외의 웹페이지 광고, 댓글, 다른 추천 기사 목록은 철저히 무시해줘.\n" +
                        "- 답할 때 앞뒤로 '여기 JSON 결과입니다' 같은 설명이나 마크다운 주석(```json ... ```)을 절대 붙이지 말고, 오직 {로 시작해서 }로 끝나는 순수한 JSON 데이터만 반환해.\n\n" +

                        "[출력 포맷 (JSON 구조)]\n" +
                        "{\n" +
                        "  \"summary\": \"초등학생용 요약 내용\",\n" +
                        "  \"mainKeyword\": \"메인키워드단어\",\n" +
                        "  \"subKeywords\": [\"단어1\", \"단어2\", \"단어3\"],\n" +
                        "  \"newsQuiz\": {\n" +
                        "    \"question\": \"뉴스 내용과 일치하는 문제는 무엇일까요?\",\n" +
                        "    \"options\": [\"보기1\", \"보기2\", \"보기3\", \"보기4\"],\n" +
                        "    \"answer\": 1,\n" +
                        "    \"explanation\": \"초등학생이 이해하기 쉬운 정답 해설\"\n" +
                        "  }\n" +
                        "}\n\n" +

                        "[뉴스 링크]: %s\n" +
                        "[대체 텍스트]: %s",
                naverNews.getLink(),
                articleBody
        );

        try {
            // 💡 존재하지 않던 메서드 대신 만들어둔 .callGemini()로 통일하여 API 호출을 처리합니다.
            String jsonResponse = geminiService.callGemini(prompt);

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                return null;
            }

            // 파싱해서 반환 (만약 뉴스 결과 맵핑 구조에 맞춰 DTO 세부 필드명이 다르면 일부 수정 가능)
            return objectMapper.readValue(jsonResponse, NewsAnalysisResult.class);
        } catch (Exception e) {
            System.err.println("❌ 개별 뉴스 Gemini 연성 및 파싱 중 에러 발생");
            e.printStackTrace();
            return null;
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
            System.err.println("❌ 마스터 키워드 통합 Gemini 분석 및 파싱 중 에러 발생");
            e.printStackTrace();
            // 에러가 나더라도 상위 배치 프로세스가 튕겨서 터지지 않도록 빈 바구니 반환
            return new ArrayList<>();
        }
    }
}
