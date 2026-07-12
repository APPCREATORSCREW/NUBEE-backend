package com.solux31.nubee_BE.domain.news.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solux31.nubee_BE.domain.news.dto.NewsAnalysisResult;
import com.solux31.nubee_BE.domain.news.dto.NaverNewsResponse;
import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import com.solux31.nubee_BE.domain.news.entity.Quiz;
import com.solux31.nubee_BE.domain.news.repository.DailyNewsRepository;
import com.solux31.nubee_BE.domain.news.repository.QuizRepository;
import com.solux31.nubee_BE.domain.words.service.WordService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NewsTransactionHelper {

    private final GeminiService geminiService;
    private final WordService wordService;
    private final DailyNewsRepository dailyNewsRepository;
    private final QuizRepository quizRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * [핵심] REQUIRES_NEW를 통해 기존 트랜잭션과 완전히 분리된
     * 새 독립 트랜잭션 주머니에서 실행됨. 실패 시 이 안의 내용만 롤백
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String processSingleNews(NaverNewsResponse.NaverNewsItem naverNews, String categoryName) throws Exception {
        String articleBody = "";

        // 1. 크롤링 및 Fallback 처리
        try {
            System.out.println("기사 링크로 크롤링 시도 중... URL: " + naverNews.getLink());
            var document = Jsoup.connect(naverNews.getLink()).timeout(5000).get();
            articleBody = document.body().text();

            if (articleBody == null || articleBody.trim().isEmpty()) {
                throw new Exception("본문이 비어있습니다.");
            }
            System.out.println("기사 크롤링 성공! (본문 글자 수: " + articleBody.length() + "자)");
        } catch (Exception e) {
            System.out.println("⚠️ 기사 크롤링 실패로 description 대체 실행: " + naverNews.getLink());
            articleBody = naverNews.getDescription();
        }

        // 2. Gemini 호출
        NewsAnalysisResult result = analyzeSingleNews(naverNews, articleBody);
        if (result == null) {
            throw new RuntimeException("Gemini 분석 결과가 null입니다.");
        }

        // 3. DB 적재 (Category 에러 방어 포함)
        System.out.println("데이터베이스(MySQL) 적재 시작");
        DailyNews news = DailyNews.builder()
                .title(naverNews.getTitle())
                .originalUrl(naverNews.getLink())
                .summary(result.getSummary())
                .mainKeyword(result.getMainKeyword())
                .category(categoryName)
                .build();
        DailyNews savedNews = dailyNewsRepository.save(news);
        System.out.println("[DB 저장 완료] DailyNews ID: " + savedNews.getNewsId());

        // Word 도메인 위임
        wordService.saveKeywords(result.getMainKeyword(), result.getSubKeywords(), savedNews.getNewsId());
        System.out.println("[DB 저장 완료] 메인/서브 키워드 동기화 완료");

        // 뉴스 관련 객관식 퀴즈 적재
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
        System.out.println("[DB 저장 완료] 뉴스 독해 퀴즈 매핑 완료");

        // 성공하면 다음 마스터 퀴즈를 위해 메인 키워드명을 반환
        return result.getMainKeyword();
    }

    // 기존 NewsService에 있던 analyzeSingleNews 로직을 이쪽으로 옮겨옵니다.
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
}