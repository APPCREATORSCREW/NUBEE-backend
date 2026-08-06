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
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class NewsTransactionHelper {

    private final GeminiService geminiService;
    private final WordService wordService;
    private final DailyNewsRepository dailyNewsRepository;
    private final QuizRepository quizRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // RFC 1123 규격 포맷터 (예: "Tue, 15 Oct 2024 10:30:00 +0900")
    private static final DateTimeFormatter PUB_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM uuuu HH:mm:ss Z", Locale.ENGLISH)
                    .withResolverStyle(ResolverStyle.STRICT);

    private LocalDateTime parsePubDate(String pubDateStr) {
        // null 또는 빈 문자열 검증 및 1회 trim 처리
        if (pubDateStr == null || pubDateStr.isBlank()) {
            throw new NewsException(NewsErrorCode.INVALID_PUB_DATE);
        }

        String trimmed = pubDateStr.trim();

        try {
            // 오프셋(타임존) 정보를 보존하여 ZonedDateTime으로 파싱
            ZonedDateTime zdt = ZonedDateTime.parse(trimmed, PUB_DATE_FORMATTER);

            // DailyNews.publishedAt 필드가 LocalDateTime인 경우 오프셋 처리 후 변환
            return zdt.toLocalDateTime();
            // 만약 DailyNews.publishedAt 타입이 OffsetDateTime/ZonedDateTime이라면 zdt.toOffsetDateTime() 리턴
        } catch (DateTimeParseException e) {
            // DateTimeParseException만 정확히 포착하여 예외 던짐
            System.err.println("❌ pubDate 파싱 실패: " + trimmed);
            throw new NewsException(NewsErrorCode.INVALID_PUB_DATE);
        }
    }

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
    public String processSingleNews(NaverNewsResDTO.NaverNewsItem naverNews, String categoryName, String recentMainKeywords) throws Exception {
        String articleBody = "";
        String imageUrl = null;
        String publisher = "네이버뉴스";

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

            // 언론사(Publisher) 크롤링 로직 (기자 이름이 아닌 언론사명만 추출)
            var logoImg = document.select(".media_end_head_top_logo img[alt]").first();

            if (logoImg != null && !logoImg.attr("alt").isBlank()) {
                // 1순위: 네이버 뉴스 언론사 로고의 alt 속성 (예: "연합뉴스", "조선일보")
                publisher = logoImg.attr("alt").trim();
                System.out.println(" 언론사 크롤링 성공 (로고 태그): " + publisher);
            } else {
                // 2순위: og:site_name 메타 태그
                var siteMeta = document.select("meta[property=og:site_name]").first();
                if (siteMeta != null && !siteMeta.attr("content").isBlank()) {
                    publisher = siteMeta.attr("content").trim();
                    System.out.println(" 언론사 크롤링 성공 (og:site_name): " + publisher);
                } else {
                    publisher = "네이버뉴스"; // fallback 기본값
                }
            }

            // 예외 방어: 추출된 문자열에 '기자'나 '특파원'이 섞여있을 경우 처리
            if (publisher.contains("기자") || publisher.contains("특파원")) {
                var siteMeta = document.select("meta[property=og:site_name]").first();

                String fallbackPublisher = siteMeta == null
                        ? ""
                        : siteMeta.attr("content").trim();

                if (!fallbackPublisher.isBlank()
                        && !fallbackPublisher.contains("기자")
                        && !fallbackPublisher.contains("특파원")) {
                    publisher = fallbackPublisher;
                } else {
                    publisher = "네이버뉴스";
                }
            }

            if (articleBody == null || articleBody.trim().isEmpty()) {
                throw new NewsException(NewsErrorCode.ARTICLE_BODY_EMPTY);
            }
        } catch (NewsException e) {
            throw e;
        } catch (Exception e) {
            // Jsoup 크롤링 실패 등 기타 일반 예외만 description fallback 처리
            System.err.println("⚠️ 기사 본문 추출 실패, description으로 대체합니다: " + e.getMessage());
            articleBody = naverNews.getDescription();
        }

        if (articleBody == null || articleBody.trim().length() < 100) {
            throw new NewsException(NewsErrorCode.ARTICLE_BODY_EMPTY);
        }

        // 크롤링 실패 시 기본 이미지 셋팅
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = "https://my-service.com/images/default-nubee.png";
        }

        if (articleBody == null || articleBody.trim().isEmpty()) {
            throw new NewsException(NewsErrorCode.ARTICLE_BODY_EMPTY);
        }

        NewsAnalysisResult result = analyzeSingleNews(naverNews, articleBody, categoryName, recentMainKeywords);

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

        LocalDateTime publishedAt = parsePubDate(naverNews.getPubDate());

        // DB VARCHAR(50) 길이 초과 방지용 자르기
        if (publisher.length() > 50) {
            publisher = publisher.substring(0, 50);
        }

        DailyNews news = DailyNews.builder()
                .title(naverNews.getTitle())
                .originalUrl(naverNews.getLink())
                .summary(result.getSummary())
                .category(categoryName)
                .imageUrl(imageUrl)
                .publisher(publisher)
                .publishedAt(publishedAt)
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

    private NewsAnalysisResult analyzeSingleNews(NaverNewsResDTO.NaverNewsItem naverNews, String articleBody, String categoryName, String recentMainKeywords) {
        String excludeSection = (recentMainKeywords != null && !recentMainKeywords.trim().isEmpty())
                ? String.format("\n- 🚨 RECENT MAIN KEYWORDS TO EXCLUDE: [%s] (Avoid selecting these exact words as `mainKeyword` unless no other domain keyword exists).", recentMainKeywords)
                : "";

        String prompt = String.format(
                "You are an AI content generator and a friendly teacher for elementary school students (3rd-4th grade).\n" +
                        "Analyze the provided [News Link] and [Alternative Text] below and reply strictly in the specified JSON format.\n\n" +

                        "[GENERAL RULES]\n" +
                        "- Write ALL values in Korean.\n" +
                        "- Do NOT use Markdown formatting (e.g., **, *, #) inside field values.\n" +
                        "- Output ONLY raw JSON object starting with { and ending with }. Absolute NO markdown block wrapper (do NOT use ```json).\n" +
                        "- Rely strictly on objective facts from the news text and standard educational context suitable for 3rd-4th graders.\n\n" +

                        "[KEYWORD EXTRACTION CRITERIA]\n" +
                        "- Current Category: [%s]\n" +
                        "- Target Domain: `mainKeyword` and `subKeywords` MUST strictly belong to the academic/learning domain of the [%s] category.%s\n" +
                        "- 🚨 FORBIDDEN KEYWORDS:\n" +
                        "  1. Everyday words (e.g., 주말, 휴일, 여행, 날씨, 스마트폰, 음식).\n" +
                        "  2. Specific real persons' names (politicians, celebrities, etc.).\n" +
                        "  3. Sensitive/controversial topics inappropriate for children (political conflicts, crimes, scandals).\n\n" +

                        "[FIELD REQUIREMENTS]\n" +
                        "1. summary: A child-friendly news summary tailored to 3rd-4th graders.\n" +
                        "   - Structure: Split into EXACTLY 2 or 3 short paragraphs.\n" +
                        "   - Subtitle: MUST start each paragraph with an emoji and subtitle (e.g., 🎬 소제목) followed by a line break (\\n).\n" +
                        "   - Paragraph Break: Separate each paragraph with double line breaks (\\n\\n).\n" +
                        "   - Tone & Style: Warm, colloquial storytelling style ('~하는 거예요.', '~와 같아요.'). Include at least one relatable metaphor.\n" +
                        "2. mainKeyword: Exactly 1 core domain-specific vocabulary word representing the article.\n" +
                        "3. subKeywords: Exactly 3 educational vocabulary words from the text.\n" +
                        "   - Each item must have `word` and `explanation` (1-2 sentences in Korean).\n" +
                        "4. newsQuiz: A 4-option multiple-choice comprehension quiz.\n" +
                        "   - Ask about CAUSAL RELATIONSHIPS or core phenomena in the news, NOT simple word definitions.\n" +
                        "   - answer: 🚨 Must be an integer between 1 and 4 (1-based index). (Ensure fair distribution across all 4 options, including Option 4).\n" +
                        "   - Ensure `options[answer - 1]` strictly matches the correct answer.\n" +
                        "   - explanation: Gentle explanation in Korean (at least 2 sentences) in a warm colloquial tone.\n\n" +

                        "[OUTPUT FORMAT EXAMPLE]\n" +
                        "{\n" +
                        "  \"summary\": \"[이모지] [소제목 1]\\n[친절한 설명 1과 비유]\\n\\n[이모지] [소제목 2]\\n[원인과 결과를 쉽게 풀어쓴 설명 2]\",\n" +
                        "  \"mainKeyword\": \"추출된 핵심 메인 키워드 1개\",\n" +
                        "  \"subKeywords\": [\n" +
                        "    { \"word\": \"추천 어휘1\", \"explanation\": \"뜻 설명\" },\n" +
                        "    { \"word\": \"추천 어휘2\", \"explanation\": \"뜻 설명\" },\n" +
                        "    { \"word\": \"추천 어휘3\", \"explanation\": \"뜻 설명\" }\n" +
                        "  ],\n" +
                        "  \"newsQuiz\": {\n" +
                        "    \"question\": \"뉴스 속 인과관계나 핵심 현상을 묻는 질문\",\n" +
                        "    \"options\": [\"오답 선지1\", \"정답 선지\", \"오답 선지3\", \"오답 선지4\"],\n" +
                        "    \"answer\": 2,\n" +
                        "    \"explanation\": \"정답 이유를 풀어주는 2문장 이상의 친절한 구어체 해설.\"\n" +
                        "  }\n" +
                        "}\n\n" +

                        "[News Link]: %s\n" +
                        "[Alternative Text]: %s",
                categoryName, categoryName, excludeSection, naverNews.getLink(), articleBody
        );

        String jsonResponse;
        try {
            jsonResponse = geminiService.callGemini(prompt);
        } catch (NewsException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Gemini API 호출 실패: " + e.getMessage());
            throw new NewsException(NewsErrorCode.GEMINI_ANALYSIS_FAILED);
        }

        // 2. 응답값 기본 null/빈값 검증
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
            throw new NewsException(NewsErrorCode.GEMINI_ANALYSIS_FAILED);
        }

        // 3. JSON 추출 및 역직렬화 (파싱 실패 시 GEMINI_PARSE_ERROR)
        try {
            jsonResponse = jsonResponse.trim();
            int startIndex = jsonResponse.indexOf("{");
            int endIndex = jsonResponse.lastIndexOf("}");

            if (startIndex == -1 || endIndex == -1 || startIndex > endIndex) {
                System.err.println("❌ Gemini 응답에 유효한 JSON 구조가 없음");
                throw new NewsException(NewsErrorCode.GEMINI_PARSE_ERROR);
            }

            String pureJson = jsonResponse.substring(startIndex, endIndex + 1);
            return objectMapper.readValue(pureJson, NewsAnalysisResult.class);

        } catch (NewsException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ Gemini 응답 JSON 파싱 실패: " + e.getMessage());
            throw new NewsException(NewsErrorCode.GEMINI_PARSE_ERROR);
        }
    }
}