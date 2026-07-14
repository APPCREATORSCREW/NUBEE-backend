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

import java.util.List;

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
        // subKeywords 객체 리스트에서 'word' 텍스트만 뽑아서 List<String>으로 변환
        List<String> subKeywordNames = result.getSubKeywords().stream()
                .map(NewsAnalysisResult.SubKeyword::getWord)
                .toList();

        wordService.saveKeywords(result.getMainKeyword(), subKeywordNames, savedNews.getNewsId());
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
                .category(categoryName)
                .build();
        quizRepository.save(newsQuiz);
        System.out.println("[DB 저장 완료] 뉴스 독해 퀴즈 매핑 완료");

        // 성공하면 다음 마스터 퀴즈를 위해 메인 키워드명을 반환
        return result.getMainKeyword();
    }

    // 기존 NewsService에 있던 analyzeSingleNews 로직을 이쪽으로 옮겨오기
    private NewsAnalysisResult analyzeSingleNews(NaverNewsResponse.NaverNewsItem naverNews, String articleBody) {
        String prompt = String.format(
                "너는 초등학생을 위한 뉴스 교육 서비스의 AI 콘텐츠 생성기이자, 친절한 선생님이야.\n" +
                        "제공되는 [뉴스 링크]에 접속하여 전체 본문을 읽고, 제시된 예시 화면의 말투와 구조를 참고하여 요구사항에 맞춰 반드시 JSON 포맷으로만 답변해줘.\n\n" +

                        "[메인 및 서브 키워드 추출 시 엄격한 금지 규칙 - 필독]\n" +
                        "- **사람 이름(예: 대통령 이름, 정치인, 연예인, 범죄자 등 구체적인 인명)은 절대 메인/서브 키워드로 추출하지 마.** 인물이 중심이 되는 단어는 무조건 제외해야 해.\n" +
                        "- 정치적 쟁점, 범죄 사건, 종교적 갈등, 연예계 가십 등 **초등학생에게 교육적으로 부적절하거나 논란이 될 수 있는 민감한 키워드는 철저히 배제해.**\n" +
                        "- 반드시 '개념', '현상', '시사 상식', '교양 원리'에 해당하는 건강하고 교육적인 명사 단어만 키워드로 선정해줘.\n" +
                        "  *(좋은 예시)* 금리, 반도체, 인플레이션, 우주선, 인공지능, 탄소배출권\n" +
                        "  *(나쁜 예시)* 홍길동(인명), 탄핵(정치 논란), 음주운전(사건/사고)\n\n" +

                        "요구사항 및 분량 제한 규칙:\n" +
                        "1. summary: 기사 전체 본문을 바탕으로 초등학교 3~4학년 눈높이에 맞춰 친절하게 요약한 요약문.\n" +
                        "   - [텍스트 구조 및 분량 제약]: **반드시 딱 2개 또는 3개의 소제목 문단 구조**로만 나누어 작성해줘. (너무 길어지지 않게 조절)\n" +
                        "   - 각 문단은 반드시 **'소제목'**으로 시작해야 해. (예: 🎬 반도체가 잘 팔리고 있어요)\n" +
                        "   - 소제목 아래의 본문 내용은 문단당 3~4문장 내외로 작성해줘.\n" +
                        "   - [말투 규칙]: '~하는 거예요.', '~와 같아요.', '~처럼요!' 같은 다정하고 친근한 초등용 구어체 스토리텔링 말투를 사용해줘.\n" +
                        "   - [비유 규칙]: 뉴스에 나오는 어려운 경제/사회 수치나 개념은 아이들이 상상할 수 있는 일상적인 비유( 예: 냄비 불을 줄이는 것, 지폐를 쌓는 것 등)를 1개 이상 섞어줘.\n" +
                        "2. mainKeyword: 기사의 핵심이 되는 메인 키워드 딱 1개 (단어 이름만 출력)\n" +
                        "3. subKeywords: 기사 내에서 초등학생이 추가로 알면 좋은 중요 어휘/시사용어 3개 세트\n" +
                        "   - 🚨 **[서브 단어 필수 요구사항]**: 본문 팝업 전용이므로 각 서브 단어의 이름(`word`)과 초등학생 눈높이에 맞춘 쉽고 명확한 **1~2문장 이내의 뜻 설명(`explanation`)**을 함께 생성해줘. (서브 키워드는 예문이나 퀴즈를 만들지 마)\n" +
                        "4. newsQuiz: 뉴스 본문 내용을 잘 이해했는지 확인하는 독해력 확인용 4지선다 객관식 퀴즈\n" +
                        "   - 🚨 [퀴즈 출제 규칙]: 단순히 단어 뜻을 묻지 말고, **뉴스 본문 속 현상의 인과관계(예: ~를 하려는 이유는 무엇인가요?)**를 묻는 질문을 생성해줘.\n" +
                        "   - newsQuiz.explanation(퀴즈 해설): '~처럼요!' 같은 구어체 말투를 유지하면서, 본문의 핵심 맥락을 짚어주는 친절한 해설을 2문장 이상 작성해줘.\n\n" +

                        "[⚠️ 팩트 기반 및 할루시네이션 방지 규칙]\n" +
                        "- 절대 제공된 [뉴스 본문]과 [대체 텍스트]에 없는 사실을 임의로 지어내거나 추측해서 꾸며내지 마. 예시를 들거나 분량을 채우기 위해 소설을 쓰지 말고 본문의 팩트만 친절하게 풀어써.\n" +
                        "- 답할 때 앞뒤로 설명이나 마크다운 주석(```json ... ```)을 절대 붙이지 말고, 오직 {로 시작해서 }로 끝나는 순수한 JSON 데이터만 반환해.\n\n" +

                        "[출력 포맷 가이드 (JSON 구조)]\n" +
                        "{\n" +
                        "  \"summary\": \"[이모지] [소제목 1]\\n[초등학생 눈높이에 맞춘 구어체와 비유를 섞은 친절한 설명 1]\\n\\n[이모지] [소제목 2]\\n[원인과 결과를 쉽게 풀어쓴 설명 2]\",\n" +
                        "  \"mainKeyword\": \"추출된 핵심 메인 키워드 단어 1개\",\n" +
                        "  \"subKeywords\": [\n" +
                        "    {\n" +
                        "      \"word\": \"추천 시사어휘1\",\n" +
                        "      \"explanation\": \"초등 눈높이로 쉽게 풀어쓴 시사어휘1의 2~3문장 뜻 설명이에요.\"\n" +
                        "    },\n" +
                        "    {\n" +
                        "      \"word\": \"추천 시사어휘2\",\n" +
                        "      \"explanation\": \"어린이가 이해하기 좋게 다정하게 설명한 뜻 풀이예요.\"\n" +
                        "    },\n" +
                        "    {\n" +
                        "      \"word\": \"추천 시사어휘3\",\n" +
                        "      \"explanation\": \"단어의 핵심을 짚어주는 짧고 명확한 설명이에요.\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"newsQuiz\": {\n" +
                        "    \"question\": \"뉴스 본문 속 인과관계나 핵심 현상을 묻는 맥락 질문\",\n" +
                        "    \"options\": [\"확실한 오답 선지1\", \"뉴스 팩트에 기반한 정답 선지\", \"그럴듯한 오답 선지3\", \"헷갈리는 오답 선지4\"],\n" +
                        "    \"answer\": 1,\n" +
                        "    \"explanation\": \"왜 그것이 정답이고 오답인지 뉴스 맥락을 짚어주는 2문장 이상의 친절한 구어체 해설\"\n" +
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