package com.solux31.nubee_BE.domain.news.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.news.dto.*;
import com.solux31.nubee_BE.domain.news.dto.Request.QuizSubmitReqDTO;
import com.solux31.nubee_BE.domain.news.dto.Response.*;
import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import com.solux31.nubee_BE.domain.news.entity.Quiz;
import com.solux31.nubee_BE.domain.news.entity.mapping.UserQuizLog;
import com.solux31.nubee_BE.domain.news.exception.NewsException;
import com.solux31.nubee_BE.domain.news.exception.code.NewsErrorCode;
import com.solux31.nubee_BE.domain.news.repository.DailyNewsRepository;
import com.solux31.nubee_BE.domain.news.repository.QuizRepository;
import com.solux31.nubee_BE.domain.news.repository.UserQuizLogRepository;
import com.solux31.nubee_BE.domain.review.entity.UserNewsHistory;
import com.solux31.nubee_BE.domain.review.repository.ReviewRepository;
import com.solux31.nubee_BE.domain.words.entity.Keyword;
import com.solux31.nubee_BE.domain.words.exception.WordsException;
import com.solux31.nubee_BE.domain.words.exception.code.WordsErrorCode;
import com.solux31.nubee_BE.domain.words.repository.KeywordRepository;
import com.solux31.nubee_BE.domain.words.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.solux31.nubee_BE.domain.profile.service.StreakService;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final UserRepository userRepository;
    private final UserQuizLogRepository userQuizLogRepository;
    private final KeywordRepository keywordRepository;
    private final ReviewRepository reviewRepository;
    private final StreakService streakService;

    private static final Long DEFAULT_KEYWORD_ID = 999L;
    private static final String DEFAULT_WORD_NAME = "알 수 없음";
    private static final String DEFAULT_EXPLANATION = "이 단어에 대한 설명이 준비되고 있어요.";
    private static final String DEFAULT_EXAMPLE = "뉴스 본문을 읽으며 단어의 맥락을 파악해 보세요.";

    private static final List<String> BASE_CATEGORIES = List.of("경제", "사회", "과학", "세계");

    public void executeDailyNewsWorkflow() {
        String[] categories = {"101", "102", "105", "104"};

        List<String> collectedLinks = new ArrayList<>();

        // 전체 카테고리 수집 루프 시작 전, 최근 2일간 DailyNews와 연관된 MAIN 키워드 목록 조회
        LocalDateTime threeDaysAgo = LocalDate.now().minusDays(2).atStartOfDay();
        List<String> recentMainKeywords = keywordRepository.findRecentMainKeywordsByNewsDate(threeDaysAgo);

        // Gemini 프롬프트 전달용 문자열 생성 (예: "인공지능, 금리, 반도체")
        String recentMainKeywordsStr = (recentMainKeywords != null && !recentMainKeywords.isEmpty())
                ? String.join(", ", recentMainKeywords)
                : "";

        System.out.println("📋 [최근 수집된 MAIN 제외 키워드 목록]: " + recentMainKeywordsStr);

        for (String categoryId : categories) {
            String categoryName = convertCategoryName(categoryId);
            List<NaverNewsResDTO.NaverNewsItem> naverNewsList = newsApiService.fetchNewsByCategory(categoryId, 20);

            if (naverNewsList == null || naverNewsList.isEmpty()) {
                continue;
            }

            int savedCount = 0;
            for (NaverNewsResDTO.NaverNewsItem naverNews : naverNewsList) {
                // 목표 수량(2개) 달성 시 탈출 안내 출력 후 break
                if (savedCount >= 2) {
                    System.out.println("🎉 [" + categoryName + "] 목표 수량(" + savedCount + "개) 저장 완료, 해당 카테고리를 수집을 종료합니다.");
                    break;
                }

                // 1. 기사 중복 필터링
                if (collectedLinks.contains(naverNews.getLink()) || dailyNewsRepository.existsByOriginalUrl(naverNews.getLink())) {
                    System.out.println("⚠️ 중복 기사 링크 패스 [" + naverNews.getTitle() + "]");
                    continue;
                }

                try {
                    String mainKeyword = newsTransactionHelper.processSingleNews(naverNews, categoryName, recentMainKeywordsStr);
                    System.out.println("🔍 추출된 메인 키워드: " + mainKeyword);

                    if (mainKeyword != null && !mainKeyword.trim().isEmpty()) {

                        // 2. 방금 생성된 기사 엔티티 역추적
                        DailyNews currentNews = dailyNewsRepository.findByOriginalUrl(naverNews.getLink())
                                .orElseThrow(() -> new NewsException(NewsErrorCode.NEWS_NOT_FOUND));

                        // 3. 중복 키워드 분기 처리
                        if (keywordRepository.existsByWord(mainKeyword)) {
                            System.out.println("⚠️ 기존 마스터 키워드 발견 [" + mainKeyword + "] -> 현재 기사와 연동 및 퀴즈 추가 프로세스 진행");

                            Keyword existingKeyword = keywordRepository.findFirstByWord(mainKeyword)
                                    .orElseThrow(() -> new WordsException(WordsErrorCode.KEYWORD_NOT_FOUND));

                            reuseExistQuizForKeyword(existingKeyword, currentNews, categoryName);
                        } else {
                            System.out.println("🌱 새로운 마스터 키워드 발견 [" + mainKeyword + "] -> Gemini 통합 연성 시작");
                            saveMasterKeywordsAndQuizzesInTransaction(mainKeyword, categoryName, currentNews.getId());
                        }

                        collectedLinks.add(naverNews.getLink());
                        savedCount++;

                        if (recentMainKeywordsStr.isEmpty()) {
                            recentMainKeywordsStr = mainKeyword;
                        } else {
                            recentMainKeywordsStr += ", " + mainKeyword;
                        }

                        System.out.println("✅ [" + categoryName + "] " + savedCount + "번째 뉴스 수집/저장 성공!"); // 저장 성공 시점 출력
                    }
                } catch (Exception e) {
                    System.err.println("❌ [" + categoryName + "] 파이프라인 오류로 인한 패스: " + naverNews.getLink());
                    e.printStackTrace();
                }
            }

            // ⚠️ 반복문이 끝났는데도 2개를 못 채웠을 때만 출력되는 알림 (반복문 밖)
            if (savedCount < 2) {
                System.out.println("⚠️ [" + categoryName + "] 카테고리는 후보 부족 또는 오류로 인해 " + savedCount + "개만 저장되었습니다.");
            }
        }
    }

    @Transactional
    public void cleanOldNewsAndQuizzes() {
        quizRepository.deleteAllInBatch();
        dailyNewsRepository.deleteAllInBatch();
    }

    @Transactional
    public void reuseExistQuizForKeyword(Keyword keyword, DailyNews news, String categoryName) {
        try {
            // 해당 키워드로 이미 생성된 KEYWORD 타입의 퀴즈를 조회 (리스트로 받아 첫 번째 항목 안전하게 선택)
            Quiz existingQuiz = quizRepository.findByKeyword_IdAndQuizType(keyword.getId(), "KEYWORD")
                    .stream().findFirst()
                    .orElse(null);

            if (existingQuiz != null) {
                // 기존 퀴즈 데이터를 그대로 사용하되, 이번에 저장된 새로운 뉴스 기사(news)만 연결하여 영속화
                Quiz reusedQuiz = Quiz.builder()
                        .quizType("KEYWORD")
                        .question(existingQuiz.getQuestion())
                        .optionsJson(existingQuiz.getOptionsJson())
                        .answer(existingQuiz.getAnswer())
                        .explanation(existingQuiz.getExplanation())
                        .keyword(keyword)
                        .dailyNews(news)
                        .category(categoryName)
                        .build();
                quizRepository.save(reusedQuiz);
                System.out.println("✅ [퀴즈 재사용 완료] 키워드: " + keyword.getWord() + " -> 기사 ID: " + news.getId());
            } else {
                System.out.println("⚠️ 기존 퀴즈를 찾지 못해 예외적으로 퀴즈를 새로 생성합니다.");
                saveMasterKeywordsAndQuizzesInTransaction(keyword.getWord(), categoryName, news.getId());
            }
        } catch (Exception e) {
            System.err.println("❌ 기존 퀴즈 재사용 프로세스 중 오류 발생: " + keyword.getWord());
            e.printStackTrace();
        }
    }

    @Transactional
    public void saveMasterKeywordsAndQuizzesInTransaction(String mainKeyword, String categoryName, Long newsId) {
        List<MainKeywordResult> masterResults = generateMasterKeywordsAndQuizzes(mainKeyword);

        if (masterResults == null || masterResults.isEmpty()) {
            throw new WordsException(WordsErrorCode.GEMINI_EMPTY_RESULT);
        }

        for (MainKeywordResult master : masterResults) {
            try {
                // 1. 마스터 단어장에 정보 업데이트 후 키워드 ID 반환받기
                Long savedKeywordId = wordService.updateKeywordExplanations(
                        master.getKeyword(),
                        master.getExplanation(),
                        master.getExampleSentence(),
                        newsId
                );

                // 2. 단어를 찾을 수 없거나 업데이트 실패하는 경우
                if (savedKeywordId == null) {
                    System.err.println("⚠️ 마스터 키워드 업데이트 실패 (단어를 찾을 수 없음): "
                            + master.getKeyword() + " (newsId: " + newsId + ")");
                    throw new WordsException(WordsErrorCode.KEYWORD_NOT_FOUND);
                }

                Keyword keywordEntity = keywordRepository.findById(savedKeywordId)
                        .orElseThrow(() -> new WordsException(WordsErrorCode.KEYWORD_NOT_FOUND));

                DailyNews newsEntity = (newsId != null) ? dailyNewsRepository.findById(newsId).orElse(null) : null;

                // 3. 퀴즈 JSON 직렬화 및 엔티티 빌드
                String optionsJson = objectMapper.writeValueAsString(master.getKeywordQuiz().getOptions());
                Quiz keywordQuiz = Quiz.builder()
                        .quizType("KEYWORD")
                        .question(master.getKeywordQuiz().getQuestion())
                        .optionsJson(optionsJson)
                        .answer(master.getKeywordQuiz().getAnswer())
                        .explanation(master.getKeywordQuiz().getExplanation())
                        .keyword(keywordEntity)
                        .dailyNews(newsEntity)
                        .category(categoryName)
                        .build();

                quizRepository.save(keywordQuiz);

            } catch (WordsException e) {
                throw e;
            } catch (NewsException e) {
                throw e;
            } catch (JsonProcessingException e) {
                // 퀴즈 옵션 JSON 직렬화 실패 시 GEMINI_PARSE_ERROR로 던짐
                System.err.println("Failed to serialize quiz options to JSON: " + e.getMessage());
                throw new NewsException(NewsErrorCode.GEMINI_PARSE_ERROR);
            } catch (Exception e) {
                // 기타 DB 영속화 실패 및 예상치 못한 예외 처리
                System.err.println("Failed to save master keywords and quizzes: newsId=" + newsId);
                e.printStackTrace();
                throw new WordsException(WordsErrorCode.GEMINI_EMPTY_RESULT);
            }
        }
    }

    private String convertCategoryName(String categoryId) {
        return switch (categoryId) {
            case "101" -> "경제";
            case "102" -> "사회";
            case "105" -> "과학";
            case "104" -> "세계";
            default -> "일반상식";
        };
    }

    private List<MainKeywordResult> generateMasterKeywordsAndQuizzes(String keyword) {
        System.out.println("🔑 핵심 키워드 퀴즈 및 뜻 설명 연성 중... -> [" + keyword + "]");

        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String promptTemplate = "You are an educational vocabulary expert and a friendly character named 'Nubee(honeybee)' for kids.\n" +
                "For each word provided in the [Keyword List], generate standardized educational content for 3rd-4th grade in strict JSON Array format.\n\n" +

                "[GENERAL RULES]\n" +
                "- Write ALL values in Korean.\n" +
                "- Do NOT use Markdown formatting (e.g., **, *, #) inside field values.\n" +
                "- Output ONLY raw JSON array starting with [ and ending with ]. Absolute NO markdown block wrapper (do NOT use ```json).\n\n" +

                "[FIELD REQUIREMENTS]\n" +
                "1. keyword: The exact input word.\n" +
                "2. explanation: Follow this EXACT 4-step structure. You MUST separate each step with double line breaks (\\n\\n):\n" +
                "   - Step 1 (Definition): '[단어명]는/은 ~예요/이에요!' (One-line core definition)\n" +
                "   - Step 2 (Causal Effect 1): Friendly explanation of what happens when this concept increases/strengthens.\n" +
                "   - Step 3 (Causal Effect 2): Explanation of the opposite case (when it decreases/weakens) or linked phenomena.\n" +
                "   - Step 4 (Summary): '이것만 기억해요!' followed by 3 takeaway rules using bullets (•), arrows (➔), and signs (↑, ↓).\n" +
                "3. example_sentence: Exactly 1 standalone, highly realistic example sentence for kids showing how the word is used in daily life.\n" +
                "   - Do NOT repeat sentences from the explanation.\n" +
                "4. keywordQuiz: A 4-option multiple-choice quiz testing the CAUSAL EFFECT (Steps 2 & 3) of the word.\n" +
                "   - Do NOT ask simple dictionary definitions (e.g., 'What is the definition of X?').\n" +
                "   - answer: An integer between 1 and 4. (Ensure fair distribution across all 4 options, including Option 4).\n" +
                "   - explanation: Gentle explanation in Korean (at least 2 sentences) on why the answer is correct.\n\n" +

                "[OUTPUT FORMAT EXAMPLE]\n" +
                "[\n" +
                "  {\n" +
                "    \"keyword\": \"단어\",\n" +
                "    \"explanation\": \"[1단계 정의]\\n\\n[2단계 강화/상승 현상]\\n\\n[3단계 약화/하락 현상]\\n\\n이것만 기억해요!\\n• [핵심요약 1] ➔ [현상 1] ↑\\n• [핵심요약 2] ➔ [현상 2]\\n• [핵심요약 3]\",\n" +
                "    \"example_sentence\": \"어린이가 이해하기 쉬운 실생활 예문 한 줄\",\n" +
                "    \"keywordQuiz\": {\n" +
                "      \"question\": \"인과관계를 묻는 질문\",\n" +
                "      \"options\": [\"선지1\", \"선지2\", \"선지3\", \"선지4\"],\n" +
                "      \"answer\": 1,\n" +
                "      \"explanation\": \"정답 이유와 오답 이유를 설명하는 2문장 이상의 친절한 해설.\"\n" +
                "    }\n" +
                "  }\n" +
                "]\n\n" +
                "[Keyword List]: [\"%s\"]";

        String finalPrompt = String.format(promptTemplate, keyword);

        try {
            String jsonResponse = geminiService.callGemini(finalPrompt);
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                return new ArrayList<>();
            }

            jsonResponse = jsonResponse.replaceAll("```json|```", "").trim();

            return objectMapper.readValue(jsonResponse, new TypeReference<List<MainKeywordResult>>() {});
        } catch (Exception e) {
            System.err.println("❌ 마스터 키워드 통합 Gemini 분석 및 파싱 중 에러 발생");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Transactional(readOnly = true)
    public TodayNewsResDTO getBalancedTodayNewsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NewsException(NewsErrorCode.INVALID_USER_INFO));

        int userPreferredCount = user.getPreferredKeywordCount();
        if (userPreferredCount < 3 || userPreferredCount > 6) {
            userPreferredCount = 6;
        }

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime endOfToday = LocalDate.now().atTime(LocalTime.MAX);

        // 1. DB 조회 시 ID 순으로 오름차순 정렬하여 기본 순서 고정
        List<DailyNews> allTodayNews = dailyNewsRepository.findByCreatedAtBetweenOrderByIdAsc(startOfToday, endOfToday);

        if (allTodayNews == null || allTodayNews.isEmpty()) {
            return new TodayNewsResDTO(0, new ArrayList<>());
        }

        // 2. 유저의 취약 카테고리 선별
        String targetCategory = determineWeakCategory(userId);

        // 3. 취약 카테고리 우선 배치 + 라운드 로빈 재정렬
        List<DailyNews> priorityFilteredList = rearrangeByWeakCategorySequence(allTodayNews, targetCategory);

        // 4. 퀴즈를 풀어 취약 카테고리가 바뀌더라도 전체 리스트 순서가 뒤흔들리지 않도록
        // id 기준 또는 작성일 기준으로 일관된 순서를 유지
        // 선호 개수만큼 슬라이싱
        int targetSize = Math.min(userPreferredCount, priorityFilteredList.size());
        List<DailyNews> selectedNews = priorityFilteredList.stream()
                .limit(targetSize)
                .sorted(Comparator.comparing(DailyNews::getId))
                .toList();

        // 5. DTO 변환 (기존과 동일)
        List<TodayNewsResDTO.NewsDto> newsDtoList = selectedNews.stream().map(news -> {
            List<Keyword> keywords = news.getRelatedKeywords();
            Keyword realKeyword = (keywords != null && !keywords.isEmpty()) ? keywords.get(0) : null;

            Long keywordId = (realKeyword != null) ? realKeyword.getId() : DEFAULT_KEYWORD_ID;
            String wordName = (realKeyword != null) ? realKeyword.getWord() : DEFAULT_WORD_NAME;
            String explanation = (realKeyword != null) ? realKeyword.getExplanation() : DEFAULT_EXPLANATION;
            String exampleSentence = (realKeyword != null && realKeyword.getExampleSentence() != null)
                    ? realKeyword.getExampleSentence()
                    : DEFAULT_EXAMPLE;

            TodayNewsResDTO.MainKeywordDto keywordDto = new TodayNewsResDTO.MainKeywordDto(
                    keywordId, wordName, explanation, exampleSentence, "MAIN"
            );

            return new TodayNewsResDTO.NewsDto(
                    news.getId(),
                    convertToEngCategory(news.getCategory()),
                    news.getTitle(),
                    news.getSummary(),
                    news.getImageUrl() != null ? news.getImageUrl() : "기본이미지URL",
                    keywordDto
            );
        }).toList();

        return new TodayNewsResDTO(newsDtoList.size(), newsDtoList);
    }

    @Transactional(readOnly = true)
    public QuizResDTO getNewsQuizByNewsId(Long newsId) {
        Quiz quiz = quizRepository.findByDailyNewsIdAndQuizType(newsId, "NEWS")
                .orElseThrow(() -> new NewsException(NewsErrorCode.NEWS_QUIZ_NOT_FOUND));
        return convertToQuizResponse(quiz, false);
    }

    private QuizResDTO convertToQuizResponse(Quiz quiz, boolean includeKeywordId) {
        List<QuizResDTO.OptionDto> parsedOptions = new ArrayList<>();

        try {
            List<String> rawOptions = objectMapper.readValue(quiz.getOptionsJson(), new TypeReference<List<String>>() {});
            for (int i = 0; i < rawOptions.size(); i++) {
                parsedOptions.add(new QuizResDTO.OptionDto(i + 1, rawOptions.get(i)));
            }
        } catch (Exception e) {
            System.err.println("🚨 [데이터 에러] Quiz ID " + quiz.getId() + "의 optionsJson 파싱 실패. 더미 선지로 대체합니다.");
            parsedOptions = List.of(
                    new QuizResDTO.OptionDto(1, "데이터를 불러오는 중입니다."),
                    new QuizResDTO.OptionDto(2, "데이터를 불러오는 중입니다."),
                    new QuizResDTO.OptionDto(3, "데이터를 불러오는 중입니다."),
                    new QuizResDTO.OptionDto(4, "데이터를 불러오는 중입니다.")
            );
        }

        Long extractedNewsId = (quiz.getDailyNews() != null) ? quiz.getDailyNews().getId() : null;
        Long extractedKeywordId = (quiz.getKeyword() != null) ? quiz.getKeyword().getId() : null;

        return new QuizResDTO(
                quiz.getId(),
                extractedNewsId,
                includeKeywordId ? extractedKeywordId : null,
                quiz.getQuizType(),
                quiz.getQuestion(),
                parsedOptions
        );
    }

    @Transactional
    public QuizSubmitResDTO submitAndGradeKeywordQuiz(Long userId, Long keywordId, QuizSubmitReqDTO request) {
        Quiz quiz = quizRepository.findById(request.getQuiz_id())
                .orElseThrow(() -> new WordsException(WordsErrorCode.KEYWORD_QUIZ_NOT_FOUND));

        if (quiz.getKeyword() == null || !quiz.getKeyword().getId().equals(keywordId)) {
                   throw new WordsException(WordsErrorCode.INVALID_QUIZ_REQUEST);
        }

        boolean alreadySolved = userQuizLogRepository.existsByUserIdAndQuizId(userId, request.getQuiz_id());
        boolean isCorrect = Objects.equals(quiz.getAnswer(), request.getSelected_answer());

        int earnedPoint = 0;
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NewsException(NewsErrorCode.INVALID_USER_INFO));

        if (!alreadySolved) {
            boolean isTodayQuiz = quiz.getCreatedAt() != null
                    && quiz.getCreatedAt().toLocalDate().isEqual(LocalDate.now());

            earnedPoint = (isCorrect && isTodayQuiz) ? 1 : 0;

            if (earnedPoint > 0) {
                user.updatePoint(earnedPoint);
            }

            streakService.updateStreak(user);

            UserQuizLog quizLog = UserQuizLog.builder()
                    .userId(userId)
                    .quizId(quiz.getId())
                    .category(quiz.getCategory())
                    .selectedAnswer(request.getSelected_answer())
                    .isCorrect(isCorrect)
                    .isCompleted(true)
                    .build();
            userQuizLogRepository.save(quizLog);
        }

        QuizSubmitResDTO.PointResultDto pointResult = new QuizSubmitResDTO.PointResultDto(
                earnedPoint, user.getPoint()
        );

        return new QuizSubmitResDTO(
                quiz.getId(),
                request.getSelected_answer(),
                quiz.getAnswer(),       // correct_answer (int)
                isCorrect,              // is_correct (boolean)
                quiz.getExplanation(),
                false,
                pointResult
        );
    }

    @Transactional
    public QuizSubmitResDTO submitAndGradeNewsQuiz(Long userId, Long newsId, QuizSubmitReqDTO request) {
        Quiz quiz = quizRepository.findById(request.getQuiz_id())
                .orElseThrow(() -> new NewsException(NewsErrorCode.NEWS_QUIZ_NOT_FOUND));

        if (quiz.getDailyNews() == null || !quiz.getDailyNews().getId().equals(newsId)) {
            throw new NewsException(NewsErrorCode.INVALID_QUIZ_REQUEST);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NewsException(NewsErrorCode.INVALID_USER_INFO));

        boolean alreadySolved = userQuizLogRepository.existsByUserIdAndQuizId(userId, request.getQuiz_id());
        boolean isCorrect = Objects.equals(quiz.getAnswer(), request.getSelected_answer());

        int earnedPoint = 0;

        if (!alreadySolved) {
            boolean isTodayQuiz = quiz.getCreatedAt() != null
                    && quiz.getCreatedAt().toLocalDate().isEqual(LocalDate.now());

            earnedPoint = (isCorrect && isTodayQuiz) ? 1 : 0;

            if (earnedPoint > 0) {
                user.updatePoint(earnedPoint);
            }

            streakService.updateStreak(user);

            UserQuizLog quizLog = UserQuizLog.builder()
                    .userId(userId)
                    .quizId(quiz.getId())
                    .category(quiz.getCategory())
                    .selectedAnswer(request.getSelected_answer())
                    .isCorrect(isCorrect)
                    .isCompleted(true)
                    .build();
            userQuizLogRepository.save(quizLog);
        }

        DailyNews news = quiz.getDailyNews();
        boolean hasHistory = reviewRepository.existsByUserIdAndNewsId(userId, news.getId());

        if (!hasHistory) {
            DailyNews newsProxy = dailyNewsRepository.getReferenceById(quiz.getDailyNews().getId());

            UserNewsHistory newsHistory = UserNewsHistory.builder()
                    .user(user)
                    .news(newsProxy) // 프록시 객체 전달
                    .viewedAt(LocalDateTime.now())
                    .build();

            reviewRepository.save(newsHistory);
        }

        QuizSubmitResDTO.PointResultDto pointResult = new QuizSubmitResDTO.PointResultDto(
                earnedPoint, user.getPoint()
        );

        return new QuizSubmitResDTO(
                quiz.getId(),
                request.getSelected_answer(),
                quiz.getAnswer(),       // correct_answer (int)
                isCorrect,              // is_correct (boolean)
                quiz.getExplanation(),
                true,
                pointResult
        );
    }

    @Transactional(readOnly = true)
    public QuizResDTO getKeywordQuizByKeywordId(Long keywordId) {
        Quiz quiz = quizRepository.findByKeyword_IdAndQuizType(keywordId, "KEYWORD")
                .orElseThrow(() -> new WordsException(WordsErrorCode.KEYWORD_QUIZ_NOT_FOUND));
        return convertToQuizResponse(quiz, true);
    }

    @Transactional(readOnly = true)
    public NewsDetailResDTO getNewsDetailWithKeywords(Long newsId) {
        DailyNews news = dailyNewsRepository.findById(newsId)
                .orElseThrow(() -> new NewsException(NewsErrorCode.NEWS_NOT_FOUND));

        List<Keyword> keywordList = keywordRepository.findByDailyNewsId(newsId);

        List<NewsDetailResDTO.RelatedKeywordDto> relatedKeywords = keywordList.stream()
                .map(k -> new NewsDetailResDTO.RelatedKeywordDto(
                        k.getId(), k.getWord(), k.getKeywordType(), k.getExplanation()
                )).toList();

        return new NewsDetailResDTO(
                news.getId(), convertToEngCategory(news.getCategory()), news.getTitle(), news.getSummary(),
                news.getImageUrl() != null ? news.getImageUrl() : "기본이미지URL",
                news.getOriginalUrl() != null ? news.getOriginalUrl() : "원문출처없음",
                (news.getPublisher() != null && !news.getPublisher().isBlank()) ? news.getPublisher().trim() : "네이버뉴스",
                news.getPublishedAt(),
                relatedKeywords
        );
    }

    private String determineWeakCategory(Long userId) {
        // 1. DB 로그상 가장 풀이 수가 적은 카테고리 1개 조회
        List<String> leastSolved = userQuizLogRepository.findLeastSolvedCategories(userId, PageRequest.of(0, 1));

        // 2. [Edge Case 1] 신규 유저 (로그 0개) -> 기본 카테고리 "경제" 반환
        if (leastSolved.isEmpty()) {
            return "경제";
        }

        // 3. [Edge Case 2] DB 로그에 한 번도 기록되지 않은 (0개 푼) 카테고리가 있다면 최우선 선택
        List<String> userSolvedCategories = userQuizLogRepository.findAllCategoriesByUserId(userId);
        for (String category : BASE_CATEGORIES) {
            if (!userSolvedCategories.contains(category)) {
                return category; // 0개 푼 카테고리를 최우선 선정
            }
        }

        // 4. 모든 카테고리를 1번 이상 풀었다면 가장 적게 푼 카테고리 반환
        return leastSolved.get(0);
    }

    private List<DailyNews> rearrangeByWeakCategorySequence(List<DailyNews> source, String weakCategory) {
        if (source == null || source.isEmpty()) return Collections.emptyList();

        // 1. 취약 카테고리 뉴스 먼저 추출
        List<DailyNews> weakList = source.stream()
                .filter(n -> n.getCategory() != null && n.getCategory().equals(weakCategory))
                .toList();

        // 2. 취약 카테고리를 제외한 나머지 뉴스들을 카테고리별로 그룹화 (LinkedHashMap 사용으로 순서 고정)
        Map<String, List<DailyNews>> otherCategoryMap = source.stream()
                .filter(n -> n.getCategory() != null && !n.getCategory().equals(weakCategory))
                .collect(Collectors.groupingBy(
                        DailyNews::getCategory,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<DailyNews> result = new ArrayList<>(weakList);

        // 3. 나머지 카테고리들에서 하나씩 라운드 로빈으로 추가
        boolean hasMore = true;
        int index = 0;
        while (hasMore) {
            hasMore = false;
            for (List<DailyNews> categoryList : otherCategoryMap.values()) {
                if (index < categoryList.size()) {
                    result.add(categoryList.get(index));
                    hasMore = true;
                }
            }
            index++;
        }

        return result;
    }

    private String convertToEngCategory(String korCategory) {
        return switch (korCategory) {
            case "경제" -> "ECONOMY";
            case "사회" -> "SOCIETY";
            case "과학" -> "SCIENCE";
            case "세계" -> "WORLD";
            default -> "GENERAL";
        };
    }

    @Transactional(readOnly = true)
    public NewsResDTO getLearningResult(Long userId) {

        // 1. 유저 이름 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NewsException(NewsErrorCode.INVALID_USER_INFO));

        // 2. 오늘 키워드 퀴즈 로그 조회
        List<UserQuizLog> keywordLogs = userQuizLogRepository
                .findTodayLogsByUserIdAndQuizType(userId, "KEYWORD");

        // 3. 키워드 퀴즈 로그에서 키워드 목록 + 뉴스 원문 링크 추출
        // quizId 목록 일괄 추출
        List<Long> quizIds = keywordLogs.stream()
                .map(UserQuizLog::getQuizId)
                .distinct()
                .collect(Collectors.toList());

        // 한 번에 Quiz 조회
        List<Quiz> quizzes = quizRepository.findAllById(quizIds);

        // 키워드 목록 + 뉴스 원문 링크 추출
        List<NewsResDTO.KeywordInfo> keywordInfos = quizzes.stream()
                .filter(quiz -> quiz.getKeyword() != null)
                .map(quiz -> {
                    Keyword keyword = quiz.getKeyword();
                    String originalUrl = keyword.getDailyNews() != null
                            ? keyword.getDailyNews().getOriginalUrl()
                            : null;
                    return NewsResDTO.KeywordInfo.builder()
                            .word(keyword.getWord())
                            .originalUrl(originalUrl)
                            .build();
                })
                .distinct()
                .collect(Collectors.toList());

        // 4. 키워드 퀴즈 정답률 계산
        double keywordAccuracy = calculateAccuracy(keywordLogs);

        // 5. 뉴스 퀴즈 정답률 계산
        List<UserQuizLog> newsLogs = userQuizLogRepository
                .findTodayLogsByUserIdAndQuizType(userId, "NEWS");
        double newsAccuracy = calculateAccuracy(newsLogs);

        return NewsResDTO.builder()
                .username(user.getUsername())
                .learnedKeywords(keywordInfos)
                .keywordQuizAccuracy(keywordAccuracy)
                .newsQuizAccuracy(newsAccuracy)
                .build();
    }

    private double calculateAccuracy(List<UserQuizLog> logs) {
        if (logs.isEmpty()) return 0.0;
        long correctCount = logs.stream().filter(UserQuizLog::isCorrect).count();
        return Math.round((double) correctCount / logs.size() * 100.0);
    }
}