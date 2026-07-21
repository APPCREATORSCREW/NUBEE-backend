package com.solux31.nubee_BE.domain.news.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solux31.nubee_BE.domain.news.dto.NewsAnalysisResult;
import com.solux31.nubee_BE.domain.news.dto.Response.NaverNewsResDTO;
import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import com.solux31.nubee_BE.domain.news.entity.Quiz;
import com.solux31.nubee_BE.domain.news.exception.NewsException;
import com.solux31.nubee_BE.domain.news.exception.code.NewsErrorCode;
import com.solux31.nubee_BE.domain.news.repository.DailyNewsRepository;
import com.solux31.nubee_BE.domain.news.repository.QuizRepository;
import com.solux31.nubee_BE.domain.words.service.WordService;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

@Component
@RequiredArgsConstructor
public class NewsTransactionHelper {

    private final GeminiService geminiService;
    private final WordService wordService;
    private final DailyNewsRepository dailyNewsRepository;
    private final QuizRepository quizRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SSLSocketFactory createTrustAllSslSocketFactory() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLSv1.3");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String processSingleNews(NaverNewsResDTO.NaverNewsItem naverNews, String categoryName) throws Exception {
        String articleBody = "";
        String imageUrl = null;

        try {
            System.out.println("기사 링크로 크롤링 시도 중... URL: " + naverNews.getLink());

            String targetUrl = validateAndGetSafeUrl(naverNews.getLink());

            var document = Jsoup.connect(targetUrl)
                    .timeout(3000) // 타임아웃 최적화 값 유지
                    .followRedirects(false) // true에서 false로 변경하여 SSRF 및 우회 차단
                    .sslSocketFactory(createTrustAllSslSocketFactory())
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36") // 봇 차단 회피 유지
                    .get(); //SSL 인증서 검증 우회, 원래대로라면 검증해야하지만 기사 크롤링만 하기 때문에 우회 설정

            articleBody = document.body().text();

            var imgMeta = document.select("meta[property=og:image]").first();
            if (imgMeta != null) {
                imageUrl = imgMeta.attr("content");
                System.out.println("📸 기사 썸네일 이미지 크롤링 성공!: " + imageUrl);
            }

            if (articleBody == null || articleBody.trim().isEmpty()) {
                throw new NewsException(NewsErrorCode.ARTICLE_BODY_EMPTY);
            }
            System.out.println("기사 크롤링 성공! (본문 글자 수: " + articleBody.length() + "자)");
        } catch (Exception e) {
            System.out.println("⚠️ 기사 크롤링 실패로 description 대체 실행: " + naverNews.getLink() + " | 사유: " + e.getMessage());
            articleBody = naverNews.getDescription();
        }

        // 크롤링 실패 시 기본 이미지 셋팅
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = "https://my-service.com/images/default-nubee.png";
        }

        if (articleBody == null || articleBody.trim().isEmpty()) {
            throw new NewsException(NewsErrorCode.ARTICLE_BODY_EMPTY);
        }

        NewsAnalysisResult result = analyzeSingleNews(naverNews, articleBody, categoryName);

        if (result.getNewsQuiz() == null || result.getNewsQuiz().getOptions() == null) {
            throw new NewsException(NewsErrorCode.GEMINI_PARSE_ERROR);
        }
        if (result.getNewsQuiz().getOptions().size() != 4) {
            throw new NewsException(NewsErrorCode.GEMINI_PARSE_ERROR);
        }
        int quizAnswer = result.getNewsQuiz().getAnswer();
        if (quizAnswer < 1 || quizAnswer > 4) {
            throw new NewsException(NewsErrorCode.GEMINI_PARSE_ERROR);
        }

        System.out.println("데이터베이스(MySQL) 적재 시작");
        DailyNews news = DailyNews.builder()
                .title(naverNews.getTitle())
                .originalUrl(naverNews.getLink())
                .summary(result.getSummary())
                .category(categoryName)
                .imageUrl(imageUrl)
                .build();

        DailyNews savedNews = dailyNewsRepository.save(news);
        System.out.println("[DB 저장 완료] DailyNews ID: " + savedNews.getId());

        wordService.saveKeywords(result.getMainKeyword(), result.getSubKeywords(), savedNews.getId());
        System.out.println("[DB 저장 완료] 메인/서브 키워드 동기화 완료");

        String optionsJson = objectMapper.writeValueAsString(result.getNewsQuiz().getOptions());

        Quiz newsQuiz = Quiz.builder()
                .quizType("NEWS")
                .question(result.getNewsQuiz().getQuestion())
                .optionsJson(optionsJson)
                .answer(quizAnswer)
                .explanation(result.getNewsQuiz().getExplanation())
                .dailyNews(savedNews)
                .category(categoryName)
                .build();
        quizRepository.save(newsQuiz);
        System.out.println("[DB 저장 완료] 뉴스 독해 퀴즈 매핑 완료");

        return result.getMainKeyword();
    }

    private String validateAndGetSafeUrl(String urlString) throws Exception {
        int redirectCount = 0;
        while (redirectCount < 3) {
            URL url = new URL(urlString);

            // 변경: http와 https 프로토콜을 둘 다 허용하도록 조건문 수정
            String protocol = url.getProtocol();
            if (!"https".equalsIgnoreCase(protocol) && !"http".equalsIgnoreCase(protocol)) {
                throw new NewsException(NewsErrorCode.INVALID_URL_PROTOCOL);
            }

            String host = url.getHost();
            if (host == null || host.trim().isEmpty()) {
                throw new NewsException(NewsErrorCode.INVALID_URL_HOST);
            }
            InetAddress inetAddress = InetAddress.getByName(host);
            if (inetAddress.isLoopbackAddress() || inetAddress.isSiteLocalAddress() || inetAddress.isLinkLocalAddress()) {
                throw new NewsException(NewsErrorCode.INVALID_URL_HOST);
            }

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // 만약 HTTPS 연결이라면, HttpURLConnection 내부의 SSL 검증도 우회
            if (conn instanceof HttpsURLConnection) {
                HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
                httpsConn.setSSLSocketFactory(createTrustAllSslSocketFactory());
                // 호스트네임 검증도 함께 통과시킵니다.
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            }

            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                String loc = conn.getHeaderField("Location");
                if (loc == null) {
                    break;
                }
                if (!loc.startsWith("http://") && !loc.startsWith("https://")) {
                    loc = new URL(url, loc).toString();
                }
                urlString = loc;
                redirectCount++;
            } else {
                break;
            }
        }
        return urlString;
    }

    private NewsAnalysisResult analyzeSingleNews(NaverNewsResDTO.NaverNewsItem naverNews, String articleBody, String categoryName) {
        String prompt = String.format(
                "You are an AI content generator and a friendly teacher for elementary school students (3rd-4th grade).\n" +
                        "Analyze the provided [News Link] and [Alternative Text] below. You must reply strictly in the specified JSON format.\n\n" +

                        "[CRITICAL CRITERIA FOR KEYWORD EXTRACTION]\n" +
                        "- 🚨 Current Category: [%s]\n" +
                        "- Target Vocabulary: Main (`mainKeyword`) and sub-keywords (`subKeywords`) MUST strictly belong to the academic/academic domain of this [%s] field.\n" +
                        "- 🚨 DO NOT extract common everyday words (e.g., weekend, holiday, travel, weather, smartphone, food) even if they appear frequently.\n" +
                        "- 🚨 DO NOT extract any specific real persons' names (e.g., politician, celebrity, criminal names).\n" +
                        "- 🚨 DO NOT choose controversial or sensitive topics inappropriate for children (e.g., political conflicts, crimes, scandals).\n\n" +

                        "[REQUIREMENTS FOR OUTPUT FIELDS - WRITE ALL VALUES IN KOREAN]\n" +
                        "1. summary: A child-friendly news summary tailored to 3rd-4th grade level.\n" +
                        "   - Structure: Split into EXACTLY 2 or 3 short paragraphs.\n" +
                        "   - Constraint: Each paragraph MUST start with a subtitle accompanied by a relevant emoji (e.g., 🎬 소제목).\n" +
                        "   - Tone & Style: Use a very warm, friendly colloquial storytelling style ('~하는 거예요.', '~와 같아요.', '~처럼요!'). Include at least one relatable metaphor for difficult concepts.\n" +
                        "2. mainKeyword: Exactly 1 core word representing the article (plain text in Korean).\n" +
                        "3. subKeywords: A list of 3 educational vocabulary words found in the text.\n" +
                        "   - Each item must have `word` and `explanation` (a clear definition in Korean within 1-2 sentences).\n" +
                        "4. newsQuiz: A 4-option multiple-choice reading comprehension quiz.\n" +
                        "   - Rule: Question should ask about the causal relationships or core phenomena in the news, NOT just a simple word definition.\n" +
                        "   - newsQuiz.answer: 🚨 CRITICAL. Must be an integer between 1 and 4 (1-based index). Do NOT use 0-based index.\n" +
                        "   - Make sure the actual correct sentence matches the `options[answer - 1]` position perfectly.\n" +
                        "   - newsQuiz.explanation: Provide a friendly explanation in Korean (at least 2 sentences) in the same colloquial tone.\n\n" +

                        "[⚠️ NO HALLUCINATION & STRICT JSON RULE]\n" +
                        "- Do not fabricate facts. Rely ONLY on the provided news text.\n" +
                        "- Output ONLY the pure raw JSON object starting with { and ending with }. Do not include markdown code block syntax (```json).\n\n" +
                        "- Ensure all content is generated based on universally accepted and accurate economic, social, or scientific facts related to the provided core keyword(s) (e.g., 'Inflation', 'Interest Rate').\n" +
                        "- Use the provided news summary strictly as a reference for the 'real-world context' in which the keyword is used." +
                        "- The core definitions and cause-and-effect relationships must be written based entirely on objective facts suitable for South Korean elementary school 3rd and 4th-grade curriculum levels."+

                        "[OUTPUT FORMAT GUIDE]\n" +
                        "{\n" +
                        "  \"summary\": \"[이모지] [소제목 1]\\n[초등학생 눈높이에 맞춘 구어체와 비유를 섞은 친절한 설명 1]\\n\\n[이모지] [소제목 2]\\n[원인과 결과를 쉽게 풀어쓴 설명 2]\",\n" +
                        "  \"mainKeyword\": \"추출된 핵심 메인 키워드 단어 1개\",\n" +
                        "  \"subKeywords\": [\n" +
                        "    { \"word\": \"추천 시사어휘1\", \"explanation\": \"뜻 설명\" },\n" +
                        "    { \"word\": \"추천 시사어휘2\", \"explanation\": \"뜻 설명\" },\n" +
                        "    { \"word\": \"추천 시사어휘3\", \"explanation\": \"뜻 설명\" }\n" +
                        "  ],\n" +
                        "  \"newsQuiz\": {\n" +
                        "    \"question\": \"뉴스 본문 속 인과관계나 핵심 현상을 묻는 맥락 질문\",\n" +
                        "    \"options\": [\"오답 선지1\", \"정답 선지\", \"오답 선지3\", \"오답 선지4\"],\n" +
                        "    \"answer\": 2,\n" +
                        "    \"explanation\": \"정답 원리를 짚어주는 2문장 이상의 친절한 구어체 해설\"\n" +
                        "  }\n" +
                        "}\n\n" +
                        "[News Link]: %s\n" +
                        "[Alternative Text]: %s",
                categoryName, categoryName, naverNews.getLink(), articleBody
        );

        try {
            String jsonResponse = geminiService.callGemini(prompt);

            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                throw new NewsException(NewsErrorCode.GEMINI_ANALYSIS_FAILED);
            }

            jsonResponse = jsonResponse.trim();
            int startIndex = jsonResponse.indexOf("{");
            int endIndex = jsonResponse.lastIndexOf("}");

            if (startIndex == -1 || endIndex == -1 || startIndex > endIndex) {
                System.err.println("❌ Gemini 응답에 유효한 JSON 구조가 포함되어 있지 않습니다.");
                throw new NewsException(NewsErrorCode.GEMINI_PARSE_ERROR);
            }

            // {로 시작해서 }로 끝나는 알맹이만 파싱
            String pureJson = jsonResponse.substring(startIndex, endIndex + 1);

            return objectMapper.readValue(pureJson, NewsAnalysisResult.class);
        } catch (NewsException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ 개별 뉴스 Gemini 연성 및 파싱 중 에러 발생");
            throw new NewsException(NewsErrorCode.GEMINI_PARSE_ERROR);
        }
    }
}