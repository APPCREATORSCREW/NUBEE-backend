package com.solux31.nubee_BE.domain.news.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.news.dto.*;
import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import com.solux31.nubee_BE.domain.news.entity.Quiz;
import com.solux31.nubee_BE.domain.news.entity.UserQuizLog;
import com.solux31.nubee_BE.domain.news.repository.DailyNewsRepository;
import com.solux31.nubee_BE.domain.news.repository.QuizRepository;
import com.solux31.nubee_BE.domain.news.repository.UserQuizLogRepository;
import com.solux31.nubee_BE.domain.words.entity.Keyword;
import com.solux31.nubee_BE.domain.words.entity.mapping.UserKeyword;
import com.solux31.nubee_BE.domain.words.repository.KeywordRepository;
import com.solux31.nubee_BE.domain.words.repository.UserKeywordRepository;
import com.solux31.nubee_BE.domain.words.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
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
    private final UserKeywordRepository userKeywordRepository;

    private static final Long DEFAULT_KEYWORD_ID = 999L;
    private static final String DEFAULT_WORD_NAME = "알 수 없음";
    private static final String DEFAULT_EXPLANATION = "이 단어에 대한 설명이 준비되고 있어요.";
    private static final String DEFAULT_EXAMPLE = "뉴스 본문을 읽으며 단어의 맥락을 파악해 보세요.";

    public void executeDailyNewsWorkflow() {
        String[] categories = {"101", "102", "105", "104"};

        List<String> collectedLinks = new ArrayList<>();

        for (String categoryId : categories) {
            String categoryName = convertCategoryName(categoryId);
            List<NaverNewsResponse.NaverNewsItem> naverNewsList = newsApiService.fetchNewsByCategory(categoryId, 4);

            if (naverNewsList == null || naverNewsList.isEmpty()) {
                continue;
            }

            int savedCount = 0;
            for (NaverNewsResponse.NaverNewsItem naverNews : naverNewsList) {
                if (savedCount >= 1) break;

                // 1. 기사 중복 필터링
                if (collectedLinks.contains(naverNews.getLink()) || dailyNewsRepository.existsByOriginalUrl(naverNews.getLink())) {
                    System.out.println("⚠️ 중복 기사 링크 패스 [" + naverNews.getTitle() + "]");
                    continue;
                }

                try {
                    String mainKeyword = newsTransactionHelper.processSingleNews(naverNews, categoryName);
                    System.out.println("🔍 추출된 메인 키워드: " + mainKeyword);

                    if (mainKeyword != null && !mainKeyword.trim().isEmpty()) {

                        // 2. 방금 생성된 기사 엔티티 역추적 (URL 기준으로 찾아오는 방법이 가장 안전)
                        DailyNews currentNews = dailyNewsRepository.findByOriginalUrl(naverNews.getLink())
                                .orElseThrow(() -> new IllegalStateException("방금 저장된 기사를 찾을 수 없습니다."));

                        // 3. 중복 키워드 분기 처리 (이제 더 이상 continue로 흐름을 파괴하지 않음)
                        if (keywordRepository.existsByWord(mainKeyword)) {
                            System.out.println("⚠️ 기존 마스터 키워드 발견 [" + mainKeyword + "] -> 현재 기사와 연동 및 퀴즈 추가 프로세스 진행");

                            // 이미 존재하는 키워드라면 뜻 설명 생성(Gemini) 과정을 생략하고 매핑만 진행
                            Keyword existingKeyword = keywordRepository.findByWord(mainKeyword)
                                    .orElseThrow(() -> new IllegalStateException("존재한다고 했으나 조회에 실패했습니다."));

                            // 기존 saveQuizForExistingKeyword 대신 Gemini 호출 없는 재사용 메서드 호출
                            reuseExistQuizForKeyword(existingKeyword, currentNews, categoryName);
                        } else {
                            System.out.println("🌱 새로운 마스터 키워드 발견 [" + mainKeyword + "] -> Gemini 통합 연성 시작");
                            // 처음 발견된 신규 키워드라면 마스터 풀 등록 및 퀴즈 생성
                            saveMasterKeywordsAndQuizzesInTransaction(mainKeyword, categoryName, currentNews.getId());
                        }

                        collectedLinks.add(naverNews.getLink());
                        savedCount++;
                    }
                } catch (Exception e) {
                    System.err.println("❌ [" + categoryName + "] 파이프라인 오류로 인한 패스: " + naverNews.getLink());
                    e.printStackTrace();
                }
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
            throw new IllegalArgumentException("Gemini 분석 결과가 비어있어 데이터를 적재할 수 없어.");
        }

        for (MainKeywordResult master : masterResults) {
            // 마스터 단어장에 정보 업데이트 후 키워드 ID 반환받기
            Long savedKeywordId = wordService.updateKeywordExplanations(
                    master.getKeyword(),
                    master.getExplanation(),
                    master.getExampleSentence(),
                    newsId
            );

            try {
                Keyword keywordEntity = keywordRepository.findById(savedKeywordId)
                        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 키워드 ID입니다."));

                DailyNews newsEntity = (newsId != null) ? dailyNewsRepository.findById(newsId).orElse(null) : null;

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
            } catch (Exception e) {
                System.err.println("키워드 마스터 퀴즈 DB 저장 중 오류 발생");
                throw new RuntimeException("마스터 퀴즈 영속화 실패로 인해 저장을 중단해.", e);
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
                "For each word provided in the [Keyword List], generate standardized educational content for 3rd-4th grade. You must output strictly in JSON Array format.\n\n" +

                "[REQUIREMENTS FOR OUTPUT FIELDS - WRITE ALL VALUES IN KOREAN]\n" +
                "1. keyword: The exact input word.\n" +
                "2. explanation: Follow this EXACT 4-step structure and tone with line breaks (\\n):\n" +
                "   - Step 1 (Definition): Define the core concept clearly in one line: '[단어명]는/은 ~예요/이에요!'\n" +
                "   - Step 2 (Causal Effect 1): Explain in child-friendly terms what happens to the economy/society/science when this concept increases/strengthens.\n" +
                "   - Step 3 (Causal Effect 2): Explain the opposite case (when it decreases/weakens) or additional critical linked phenomena.\n" +
                "   - Step 4 (Summary Wrap-up): Write '이것만 기억해요!' and provide 3 key takeaway rules using bullet points (•), arrows (➔), and signs (↑, ↓).\n" +
                "3. example_sentence: Provide EXACTLY 1 unique, highly realistic and complete example sentence showing how the word is used in daily life or real news contexts.\n" +
                "   - Do not reuse the text from the explanation field; make it a creative standalone example.\n" +
                "4. keywordQuiz: A 4-option multiple-choice quiz testing the usage or causal effect of the word.\n" +
                "   - 🚨 ABSOLUTELY NO simple dictionary definition questions (e.g., 'What is the definition of this word?').\n" +
                "   - Question must ask about the causal relationships or phenomena described in Steps 2 or 3 of the explanation.\n" +
                "   - answer: 🚨 Must be an integer between 1 and 4 (1-based index).\n" +
                "   - explanation: Provide a gentle, thorough explanation in Korean (at least 2 sentences) on why the options are correct/incorrect.\n\n" +

                "[⚠️ NO DUPLICATION & STRICT JSON ARRAY RULE]\n" +
                "- Create original examples tailored to the specific word's nature.\n" +
                "- Output ONLY the pure raw JSON array starting with [ and ending with ]. Do not include markdown tags (```json).\n\n" +

                "[OUTPUT FORMAT GUIDE]\n" +
                "[\n" +
                "  {\n" +
                "    \"keyword\": \"입력된 단어 이름\",\n" +
                "    \"explanation\": \"[1단계 한 줄 정의]\\n\\n[2단계 개념이 높아지거나 강해질 때의 현상]\\n\\n[3단계 개념이 낮아지거나 약해질 때의 현상]\\n\\n이것만 기억해요!\\n• [핵심요약 1] ➔ [현상 1] ↑\\n• [핵심요약 2] ➔ [현상 2] 쉬워짐\\n• [핵심요약 3]\",\n" +
                "    \"example_sentence\": \"아이들이 일상에서 단어의 뜻과 쓰임새를 단번에 체감할 수 있는 완결된 예시 문장 한 줄\",\n" +
                "    \"keywordQuiz\": {\n" +
                "      \"question\": \"단어의 인과관계를 묻는 맥락 질문\",\n" +
                "      \"options\": [\"선지1\", \"선지2\", \"선지3\", \"선지4\"],\n" +
                "      \"answer\": 1,\n" +
                "      \"explanation\": \"정답과 오답의 원리를 친절하게 설명하는 2문장 이상의 해설\"\n" +
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
    public TodayNewsResponse getBalancedTodayNewsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        int userPreferredCount = user.getPreferredKeywordCount();
        if (userPreferredCount < 3 || userPreferredCount > 6) {
            userPreferredCount = 6;
        }

        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime endOfToday = LocalDate.now().atTime(LocalTime.MAX);
        List<DailyNews> allTodayNews = dailyNewsRepository.findByCreatedAtBetween(startOfToday, endOfToday);

        if (allTodayNews == null || allTodayNews.isEmpty()) {
            return new TodayNewsResponse(0, new ArrayList<>());
        }

        // 1. 시스템에서 다루는 4대 뉴스 카테고리 기준선 정의
        List<String> baseCategories = List.of("경제", "사회", "과학", "세계");

        // 2. 사용자가 풀이한 이력이 있는 카테고리 순위 가져오기 (전체 가져오도록 페이징 제거 또는 넉넉하게 조회)
        List<String> leastSolved = userQuizLogRepository.findLeastSolvedCategories(userId, org.springframework.data.domain.PageRequest.of(0, 4));

        // 3. 전체 기준 카테고리 중, 풀이 이력(leastSolved)에 아예 존재하지 않는 '미학습 카테고리'를 우선 선별
        String targetCategory = baseCategories.stream()
                .filter(category -> !leastSolved.contains(category))
                .findFirst()
                // 4. 만약 모든 카테고리를 한 번씩은 다 풀었다면, 기존의 풀이 횟수가 가장 적은 순(leastSolved의 첫 번째 항목)을 선택하고, 그것도 비어있다면 "경제"로 롤백
                .orElseGet(() -> leastSolved.isEmpty() ? "경제" : leastSolved.get(0));

        List<DailyNews> priorityFilteredList = new ArrayList<>();

        List<DailyNews> weakCategoryNews = allTodayNews.stream()
                .filter(news -> targetCategory.equals(news.getCategory()))
                .toList();
        priorityFilteredList.addAll(weakCategoryNews);

        List<DailyNews> remainBalancedList = rearrangeByWeakCategorySequence(allTodayNews, targetCategory);
        java.util.Collections.shuffle(remainBalancedList);

        for (DailyNews remainNews : remainBalancedList) {
            if (!priorityFilteredList.contains(remainNews)) {
                priorityFilteredList.add(remainNews);
            }
        }

        int targetSize = Math.min(userPreferredCount, priorityFilteredList.size());
        List<DailyNews> selectedNews = priorityFilteredList.subList(0, targetSize);

        List<TodayNewsResponse.NewsDto> newsDtoList = selectedNews.stream().map(news -> {
            Keyword realKeyword = news.getRelatedKeywords().isEmpty() ? null : news.getRelatedKeywords().get(0);

            Long keywordId = (realKeyword != null) ? realKeyword.getId() : DEFAULT_KEYWORD_ID;
            String wordName = (realKeyword != null) ? realKeyword.getWord() : DEFAULT_WORD_NAME;
            String explanation = (realKeyword != null) ? realKeyword.getExplanation() : DEFAULT_EXPLANATION;
            String exampleSentence = (realKeyword != null && realKeyword.getExampleSentence() != null)
                    ? realKeyword.getExampleSentence()
                    : DEFAULT_EXAMPLE;

            TodayNewsResponse.MainKeywordDto keywordDto = new TodayNewsResponse.MainKeywordDto(
                    keywordId, wordName, explanation, exampleSentence, "MAIN"
            );

            return new TodayNewsResponse.NewsDto(
                    news.getId(),
                    convertToEngCategory(news.getCategory()),
                    news.getTitle(),
                    news.getSummary(),
                    news.getImageUrl() != null ? news.getImageUrl() : "기본이미지URL",
                    keywordDto
            );
        }).toList();

        return new TodayNewsResponse(newsDtoList.size(), newsDtoList);
    }

    @Transactional(readOnly = true)
    public QuizResponse getNewsQuizByNewsId(Long newsId) {
        Quiz quiz = quizRepository.findByDailyNewsIdAndQuizType(newsId, "NEWS")
                .orElseThrow(() -> new IllegalArgumentException("해당 뉴스 퀴즈 존재하지 않음"));
        return convertToQuizResponse(quiz, false);
    }

    private QuizResponse convertToQuizResponse(Quiz quiz, boolean includeKeywordId) {
        List<QuizResponse.OptionDto> parsedOptions = new ArrayList<>();

        try {
            List<String> rawOptions = objectMapper.readValue(quiz.getOptionsJson(), new TypeReference<List<String>>() {});
            for (int i = 0; i < rawOptions.size(); i++) {
                parsedOptions.add(new QuizResponse.OptionDto(i + 1, rawOptions.get(i)));
            }
        } catch (Exception e) {
            System.err.println("🚨 [데이터 에러] Quiz ID " + quiz.getId() + "의 optionsJson 파싱 실패. 더미 선지로 대체합니다.");
            parsedOptions = List.of(
                    new QuizResponse.OptionDto(1, "데이터를 불러오는 중입니다."),
                    new QuizResponse.OptionDto(2, "데이터를 불러오는 중입니다."),
                    new QuizResponse.OptionDto(3, "데이터를 불러오는 중입니다."),
                    new QuizResponse.OptionDto(4, "데이터를 불러오는 중입니다.")
            );
        }

        Long extractedNewsId = (quiz.getDailyNews() != null) ? quiz.getDailyNews().getId() : null;
        Long extractedKeywordId = (quiz.getKeyword() != null) ? quiz.getKeyword().getId() : null;

        return new QuizResponse(
                quiz.getId(),
                extractedNewsId,
                includeKeywordId ? extractedKeywordId : null,
                quiz.getQuizType(),
                quiz.getQuestion(),
                parsedOptions
        );
    }

    @Transactional
    public QuizSubmitResponse submitAndGradeKeywordQuiz(Long userId, Long keywordId, QuizSubmitRequest request) {
        Quiz quiz = quizRepository.findById(request.getQuiz_id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 퀴즈 ID로 채점 요청"));

        boolean alreadySolved = userQuizLogRepository.existsByUserIdAndQuizId(userId, request.getQuiz_id());
        boolean isCorrect = (quiz.getAnswer() == request.getSelected_answer());

        int earnedPoint = 0;
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        if (!alreadySolved) {
            earnedPoint = isCorrect ? 1 : 0;
            user.updatePoint(earnedPoint);

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

        QuizSubmitResponse.PointResultDto pointResult = new QuizSubmitResponse.PointResultDto(
                earnedPoint, user.getPoint()
        );

        QuizSubmitResponse.LearningResultDto learningResult = new QuizSubmitResponse.LearningResultDto(
                earnedPoint, user.getPoint(), true
        );

        return new QuizSubmitResponse(
                quiz.getId(), request.getSelected_answer(), isCorrect, quiz.getAnswer(),
                quiz.getExplanation(), pointResult, learningResult
        );
    }

    @Transactional
    public QuizSubmitResponse submitAndGradeNewsQuiz(Long userId, Long newsId, QuizSubmitRequest request) {
        Quiz quiz = quizRepository.findById(request.getQuiz_id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 퀴즈 ID로 채점 요청"));

        if (quiz.getDailyNews() == null || !quiz.getDailyNews().getId().equals(newsId)) {
            throw new IllegalArgumentException("해당 퀴즈는 지정된 뉴스 기사에 속하지 않은 퀴즈입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        boolean alreadySolved = userQuizLogRepository.existsByUserIdAndQuizId(userId, request.getQuiz_id());
        boolean isCorrect = (quiz.getAnswer() == request.getSelected_answer());

        int earnedPoint = 0;

        if (!alreadySolved) {
            earnedPoint = isCorrect ? 1 : 0;
            user.updatePoint(earnedPoint);

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

        QuizSubmitResponse.PointResultDto pointResult = new QuizSubmitResponse.PointResultDto(
                earnedPoint, user.getPoint()
        );

        QuizSubmitResponse.LearningResultDto learningResult = new QuizSubmitResponse.LearningResultDto(
                earnedPoint, user.getPoint(), true
        );

        return new QuizSubmitResponse(
                quiz.getId(), request.getSelected_answer(), isCorrect, quiz.getAnswer(),
                quiz.getExplanation(), pointResult, learningResult
        );
    }

    @Transactional(readOnly = true)
    public QuizResponse getKeywordQuizByKeywordId(Long keywordId) {
        Quiz quiz = quizRepository.findByKeyword_IdAndQuizType(keywordId, "KEYWORD")
                .orElseThrow(() -> new IllegalArgumentException("해당 키워드 퀴즈가 존재하지 않습니다."));
        return convertToQuizResponse(quiz, true);
    }

    @Transactional(readOnly = true)
    public NewsDetailResponse getNewsDetailWithKeywords(Long newsId) {
        DailyNews news = dailyNewsRepository.findById(newsId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 뉴스 기사 ID"));

        List<Keyword> keywordList = keywordRepository.findByDailyNewsId(newsId);

        List<NewsDetailResponse.RelatedKeywordDto> relatedKeywords = keywordList.stream()
                .map(k -> new NewsDetailResponse.RelatedKeywordDto(
                        k.getId(), k.getWord(), k.getKeywordType(), k.getExplanation()
                )).toList();

        return new NewsDetailResponse(
                news.getId(), convertToEngCategory(news.getCategory()), news.getTitle(), news.getSummary(),
                news.getImageUrl() != null ? news.getImageUrl() : "기본이미지URL",
                news.getOriginalUrl() != null ? news.getOriginalUrl() : "원문출처없음",
                relatedKeywords
        );
    }

    private List<DailyNews> rearrangeByWeakCategorySequence(List<DailyNews> source, String weakCategory) {
        List<DailyNews> weakList = source.stream().filter(n -> weakCategory.equals(n.getCategory())).toList();

        List<DailyNews> economy = source.stream().filter(n -> "경제".equals(n.getCategory()) && !weakCategory.equals("경제")).toList();
        List<DailyNews> society = source.stream().filter(n -> "사회".equals(n.getCategory()) && !weakCategory.equals("사회")).toList();
        List<DailyNews> science = source.stream().filter(n -> "과학".equals(n.getCategory()) && !weakCategory.equals("과학")).toList();
        List<DailyNews> world = source.stream().filter(n -> "세계".equals(n.getCategory()) && !weakCategory.equals("세계")).toList();

        List<DailyNews> result = new ArrayList<>();
        result.addAll(weakList);

        int maxSize = Math.max(Math.max(economy.size(), society.size()), Math.max(science.size(), world.size()));
        for (int i = 0; i < maxSize; i++) {
            if (i < economy.size()) result.add(economy.get(i));
            if (i < society.size()) result.add(society.get(i));
            if (i < science.size()) result.add(science.get(i));
            if (i < world.size()) result.add(world.get(i));
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

        // 1. 아이 이름 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 2. 오늘 키워드 퀴즈 로그 조회
        List<UserQuizLog> keywordLogs = userQuizLogRepository
                .findTodayLogsByUserIdAndQuizType(userId, "KEYWORD");

        // 3. 키워드 퀴즈 로그에서 키워드 목록 + 뉴스 원문 링크 추출
        List<NewsResDTO.KeywordInfo> keywordInfos = keywordLogs.stream()
                .map(log -> {
                    Quiz quiz = quizRepository.findById(log.getQuizId())
                            .orElse(null);
                    if (quiz == null || quiz.getKeyword() == null) return null;

                    Keyword keyword = quiz.getKeyword();
                    String originalUrl = keyword.getDailyNews() != null
                            ? keyword.getDailyNews().getOriginalUrl()
                            : null;

                    return NewsResDTO.KeywordInfo.builder()
                            .word(keyword.getWord())
                            .originalUrl(originalUrl)
                            .build();
                })
                .filter(info -> info != null)
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