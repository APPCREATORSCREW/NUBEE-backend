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
import org.jsoup.nodes.Document;

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
        // -------------------------------------------------------------------------
        // 0. 원래 이름인 NaverNewsItem 리스트를 그대로 받아옵니다.
        // -------------------------------------------------------------------------
        List<NaverNewsResponse.NaverNewsItem> naverNewsList = newsApiService.fetchDailyEightNews();

        List<NewsAnalysisResult> analysisResults = new ArrayList<>();
        List<String> mainKeywords = new ArrayList<>();

        // -------------------------------------------------------------------------
        // 1단계: 뉴스별 본문 확보(크롤링 or Fallback) 및 개별 AI 연성 + DB 저장
        // -------------------------------------------------------------------------
        // 💡 변수 타입을 원래 사용하시던 NaverNewsItem으로 유지합니다!
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
                // 💡 위에서 선언해 뒀기 때문에 catch 블록에서도 안전하게 접근 가능합니다.
                articleBody = naverNews.getDescription();
            }

            // Gemini 연성 실행 (요약, 키워드, 뉴스 퀴즈 생성)
            NewsAnalysisResult result = analyzeSingleNews(articleBody);

            if (result != null) {
                analysisResults.add(result);
                mainKeywords.add(result.getMainKeyword());

                try {
                    // [3단계 선행] 외래키(newsId) 제약조건 충족을 위해 DailyNews 엔티티 먼저 빌드 및 저장
                    DailyNews news = DailyNews.builder()
                            .summary(result.getSummary())
                            .build();
                    DailyNews savedNews = dailyNewsRepository.save(news);

                    // [Word 도메인 협업] 확보된 newsId를 실어서 키워드 중복 체크 및 저장 위임
                    wordService.saveKeywords(result.getMainKeyword(), result.getSubKeywords(), savedNews.getNewsId());

                    // 뉴스 관련 객관식 퀴즈 적재 (options 배열은 JSON String으로 변환)
                    String optionsJson = objectMapper.writeValueAsString(result.getNewsQuiz().getOptions());
                    Quiz newsQuiz = Quiz.builder()
                            .quizType("NEWS")
                            .question(result.getNewsQuiz().getQuestion())
                            .options(optionsJson)
                            .answer(result.getNewsQuiz().getAnswer())
                            .explanation(result.getNewsQuiz().getExplanation())
                            .newsId(savedNews.getNewsId()) // 생성된 뉴스 외래키 연동
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

        for (MasterKeywordResult master : masterResults) {
            // 💡 [Word 도메인 협업] 단어 테이블에 새로 생성된 초등학생용 설명글(뜻) 업데이트 위임
            wordService.updateKeywordExplanations(master.getKeyword(), master.getExplanation());

            try {
                // 마스터 키워드 퀴즈 적재
                String optionsJson = objectMapper.writeValueAsString(master.getKeywordQuiz().getOptions());
                Quiz keywordQuiz = Quiz.builder()
                        .quizType("KEYWORD")
                        .question(master.getKeywordQuiz().getQuestion())
                        .options(optionsJson)
                        .answer(master.getKeywordQuiz().getAnswer())
                        .explanation(master.getKeywordQuiz().getExplanation())
                        // .keywordId(...) // 특정 키워드 고유 ID 연동이 필요하다면 조회 후 세팅 가능
                        .build();
                quizRepository.save(keywordQuiz);
            } catch (Exception e) {
                System.err.println("❌ 키워드 마스터 퀴즈 DB 저장 중 오류 발생");
                e.printStackTrace();
            }
        }
    }
