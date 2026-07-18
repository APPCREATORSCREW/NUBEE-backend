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
import com.solux31.nubee_BE.domain.words.repository.KeywordRepository;
import com.solux31.nubee_BE.domain.words.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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

    // REQUIRES_NEW 트랜잭션과의 데드락 방지를 위해 메인 워크플로우 자체 @Transactional은 제거함
    public void executeDailyNewsWorkflow() {
        // [1. 어제 자 중복 방지] DB를 비우기 전에 어제의 기사 링크와 키워드를 먼저 긁어옴
        List<DailyNews> yesterdayNewsList = dailyNewsRepository.findAll();

        List<String> yesterdayLinks = yesterdayNewsList.stream()
                .map(DailyNews::getOriginalUrl)
                .filter(url -> url != null && !url.isEmpty())
                .toList();

        List<String> yesterdayKeywords = yesterdayNewsList.stream()
                .map(DailyNews::getMainKeyword)
                .filter(kw -> kw != null && !kw.isEmpty())
                .toList();

        // 백업 완료, 기존 데이터를 깨끗하게 지우기
        cleanOldNewsAndQuizzes();

        String[] categories = {"101", "102", "105", "104"};

        // [2. 오늘 자 중복 방지] 오늘 이미 수집 완료된 키워드와 기사 링크를 저장하는 임시 리스트
        List<String> collectedMainKeywords = new ArrayList<>();
        List<String> collectedLinks = new ArrayList<>();

        for (String categoryId : categories) {
            String categoryName = convertCategoryName(categoryId);
            // 중복 필터링으로 패스될 것을 대비해 기사를 조금 더 여유있게 가져오기
            List<NaverNewsResponse.NaverNewsItem> naverNewsList = newsApiService.fetchNewsByCategory(categoryId, 4);

            if (naverNewsList == null || naverNewsList.isEmpty()) {
                continue;
            }

            int savedCount = 0; // 카테고리당 목표 수집 개수 (1개)
            for (NaverNewsResponse.NaverNewsItem naverNews : naverNewsList) {
                if (savedCount >= 1) break; // 카테고리별로 1개만 성공하면 다음 카테고리로

                // 필터 A: 어제 혹은 오늘 이미 수집한 기사 링크와 겹치면 패스
                if (yesterdayLinks.contains(naverNews.getLink()) || collectedLinks.contains(naverNews.getLink())) {
                    System.out.println("⚠️ 중복 기사 링크 패스 [" + naverNews.getTitle() + "]");
                    continue;
                }

                try {
                    String mainKeyword = newsTransactionHelper.processSingleNews(naverNews, categoryName);

                    if (mainKeyword != null) {
                        // 필터 B: 어제 풀었거나 오늘 이미 수집 리스트에 추가된 키워드라면 패스
                        if (yesterdayKeywords.contains(mainKeyword) || collectedMainKeywords.contains(mainKeyword)) {
                            System.out.println("⚠️ 중복 키워드 감지 [" + mainKeyword + "] -> 패스하고 다음 기사를 처리합니다.");
                            continue;
                        }

                        DailyNews currentNews = dailyNewsRepository.findTopByMainKeywordOrderByIdDesc(mainKeyword).orElse(null);
                        Long newsId = (currentNews != null) ? currentNews.getId() : null;

                        List<String> singleKeywordList = List.of(mainKeyword);

                        // 레코드 저장이 완벽하게 완료된 후에 수집 리스트와 저장 개수를 갱신하도록 위치 변경
                        saveMasterKeywordsAndQuizzesInTransaction(singleKeywordList, categoryName, newsId);

                        // 예외 없이 트랜잭션이 성공한 경우에만 컬렉션 반영 및 카운트 증가 처리
                        collectedMainKeywords.add(mainKeyword);
                        collectedLinks.add(naverNews.getLink()); // 성공한 기사 링크 바구니에 저장
                        savedCount++;
                    }
                } catch (Exception e) {
                    System.err.println("❌ [" + categoryName + "] 파이프라인 패스: " + naverNews.getLink());
                }
            }
        }
    }

    // 개별 영속성 유지를 위한 트랜잭션 선언
    @Transactional
    public void cleanOldNewsAndQuizzes() {
        quizRepository.deleteAllInBatch();
        dailyNewsRepository.deleteAllInBatch();
    }

    // 개별 영속성 유지를 위한 트랜잭션 선언
    @Transactional
    public void saveMasterKeywordsAndQuizzesInTransaction(List<String> singleKeywordList, String categoryName, Long newsId) {
        List<MainKeywordResult> masterResults = generateMasterKeywordsAndQuizzes(singleKeywordList);

        if (masterResults == null || masterResults.isEmpty()) {
            throw new IllegalArgumentException("Gemini 분석 결과가 비어있어 데이터를 적재할 수 없어.");
        }

        for (MainKeywordResult master : masterResults) {
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

    private List<MainKeywordResult> generateMasterKeywordsAndQuizzes(List<String> mainKeywords) {
        System.out.println("🔑 핵심 키워드 통합 퀴즈 및 뜻 설명 연성 중... -> " + mainKeywords);

        if (mainKeywords == null || mainKeywords.isEmpty()) {
            return new ArrayList<>();
        }

        String promptTemplate =
                "너는 초등학생을 위한 어휘 교육 전문가이자, 친절한 캐릭터 '누비(꿀벌)'야.\n" +
                        "제공된 [키워드 리스트]에 속한 각 단어들에 대해, 초등학교 3~4학년이 완벽히 이해할 수 있도록 **설명 구조와 말투를 규격화하여** 생성해줘.\n\n" +

                        "요구사항 및 텍스트 구조 제약 (매우 중요):\n" +
                        "1. keyword: 제공된 리스트에 있는 단어 이름 그대로 출력\n" +
                        "2. explanation: **[반드시 아래의 4단계 구조와 줄바꿈(\\n), 말투를 유사하게 지켜서 작성해줘]**\n" +
                        "   - [1단계: 한 줄 정의]: '[단어명]는/은 ~예요/이에요!' 형태로 단어의 핵심을 쉽고 명확하게 한 줄로 정의.\n" +
                        "   - [2단계: 인과 현상 1]: 이 단어가 가진 개념이나 속성이 '높아지거나/강해지거나/많아지면' 일상이나 경제/사회/과학 분야에 어떤 변화나 현상이 생기는지 초등 눈높이로 설명.\n" +
                        "   - [3단계: 인과 현상 2]: 이 단어가 가진 개념이나 속성이 '낮아지거나/약해지거나/적어지면' 어떻게 되는지 반대 상황을 설명하거나, 아이들이 추가로 알아야 할 핵심 연관 현상을 설명.\n" +
                        "   - [4단계: 요약 마감]: '이것만 기억해요!' 문구를 넣은 뒤, 핵심 규칙 3가지를 글머리 기호(•), 화살표(➔), 상태 기호(↑, ↓)를 조합하여 깔끔하게 요약 마무리.\n\n" +

                        "3. example_sentence: [반드시 필수로 작성] 해당 메인 단어가 일상생활이나 실제 뉴스 상황 속에서 어떻게 구체적으로 쓰이는지 보여주는 초등학생 눈높이의 친절하고 완결된 예시 문장 딱 1개만 생성해줘.\n" +
                        "   - 🚨 (주의) 다른 필드에 들어가는 중복 설명을 베껴 쓰지 말고, 이 문장 단독으로 읽어도 상황이 완벽히 그려지는 독창적인 일상 대화나 행동 위주의 예문을 창작해내야 해.\n\n" +

                        "4. keywordQuiz: 단어의 쓰임새 and 현상을 묻는 4지선다 객관식 퀴즈\n" +
                        "   - 🚨 [단순 뜻 풀이 절대 금지]: '이 단어의 뜻은 무엇일까요?' 같은 단순 정의 찾기 문제는 절대 출제하지 마.\n" +
                        "   - 반드시 위 [explanation]의 2, 3단계에서 다룬 현상이나 인과관계(예: ~가 어떻게 되면 어떤 일이 일어날까요?)를 질문으로 던져줘.\n" +
                        "   - keywordQuiz.answer (정답): 🚨 **[주의 - 매우 중요]** 정답은 인덱스가 아닌 **실제 선지 번호인 1, 2, 3, 4 중 하나**로만 정확히 지정해줘. (0-based 인덱스 금지!)\n" +
                        "   - keywordQuiz.explanation(퀴즈 해설): 최소 2문장 이상으로 왜 그 보기들이 오답이고 정답인지 아이 눈높이에서 원리를 조곤조곤 설명해줘.\n\n" +

                        "[⚠️ 엄격한 예외 처리 및 탈선 방지 규칙]\n" +
                        "- 특정 단어(예: 금리)의 예시 문장이나 숫자를 다른 단어에 그대로 베껴 쓰지 마. 입력된 단어의 본질에 맞는 완전히 새로운 독창적인 일상 예시를 창작해내야 해.\n" +
                        "- 답할 때 앞뒤로 설명이나 마크다운 주석(```json ... ```)을 절대 붙이지 말고, 오직 [로 시작해서 ]로 끝나는 순수한 JSON 배열(Array) 데이터만 반환해.\n\n" +

                        "[출력 포맷 (JSON 배열 구조 가이드)]\n" +
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
                        "[키워드 리스트]: %s";

        String finalPrompt = String.format(promptTemplate, mainKeywords.toString());

        try {
            String jsonResponse = geminiService.callGemini(finalPrompt);
            if (jsonResponse == null || jsonResponse.trim().isEmpty()) {
                return new ArrayList<>();
            }

            jsonResponse = jsonResponse.replaceAll("```json|```", "").trim();

            return objectMapper.readValue(jsonResponse, new TypeReference<List<MainKeywordResult>>() {});
        } catch (Exception e) {
            System.err.println("마스터 키워드 통합 Gemini 분석 및 파싱 중 에러 발생");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Transactional(readOnly = true)
    public TodayNewsResponse getBalancedTodayNewsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 1. 유저가 설정한 선호 키워드/뉴스 개수 가져오기 (기본값 및 범위 안전장치)
        int userPreferredCount = user.getPreferredKeywordCount();
        if (userPreferredCount < 3 || userPreferredCount > 6) {
            userPreferredCount = 6;
        }

        List<DailyNews> allTodayNews = dailyNewsRepository.findAll();
        if (allTodayNews == null || allTodayNews.isEmpty()) {
            return new TodayNewsResponse(0, new ArrayList<>());
        }

        // 2. 유저의 학습 이력을 분석해서 가장 적게 푼(가장 취약한) 카테고리 1개 알아내기
        List<String> leastSolved = userQuizLogRepository.findLeastSolvedCategories(userId, org.springframework.data.domain.PageRequest.of(0, 1));
        String targetCategory = leastSolved.isEmpty() ? "경제" : leastSolved.get(0);

        // 3. [핵심 필터링 고도화] 전체 뉴스 중 취약 카테고리 뉴스들을 먼저 최우선으로 수집함
        List<DailyNews> priorityFilteredList = new ArrayList<>();

        List<DailyNews> weakCategoryNews = allTodayNews.stream()
                .filter(news -> targetCategory.equals(news.getCategory()))
                .toList();
        priorityFilteredList.addAll(weakCategoryNews);

        // 4. 나머지 카테고리 뉴스를 가져와서 뒤에 붙여주기 (라운드로빈 방식 헬퍼 활용)
        List<DailyNews> remainBalancedList = rearrangeByWeakCategorySequence(allTodayNews, targetCategory);
        java.util.Collections.shuffle(remainBalancedList);

        for (DailyNews remainNews : remainBalancedList) {
            // 이미 위에서 들어간 취약 카테고리 뉴스는 중복 투입 방지
            if (!priorityFilteredList.contains(remainNews)) {
                priorityFilteredList.add(remainNews);
            }
        }

        // 5. 유저가 설정한 개수(userPreferredCount)만큼만 최종 필터링하여 자르기
        int targetSize = Math.min(userPreferredCount, priorityFilteredList.size());
        List<DailyNews> selectedNews = priorityFilteredList.subList(0, targetSize);

        // 6. Response DTO 변환 및 반환
        List<TodayNewsResponse.NewsDto> newsDtoList = selectedNews.stream().map(news -> {
            Keyword realKeyword = keywordRepository.findByWordAndNewsId(news.getMainKeyword(), news.getId())
                    .orElse(null);

            Long keywordId = (realKeyword != null) ? realKeyword.getId() : 999L;
            String wordName = (realKeyword != null) ? realKeyword.getWord() : news.getMainKeyword();
            String explanation = (realKeyword != null) ? realKeyword.getExplanation() : "이 단어에 대한 설명이 준비되고 있어요.";
            String exampleSentence = (realKeyword != null && realKeyword.getExampleSentence() != null)
                    ? realKeyword.getExampleSentence()
                    : "예시 상황이 아직 입력되지 않았어요.";

            TodayNewsResponse.MainKeywordDto keywordDto = new TodayNewsResponse.MainKeywordDto(
                    keywordId,
                    wordName,
                    explanation,
                    exampleSentence,
                    "MAIN"
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
        try {
            return convertToQuizResponse(quiz, false);
        } catch (Exception e) {
            throw new RuntimeException("퀴즈 조회 처리 중 파싱 오류 발생", e);
        }
    }

    private QuizResponse convertToQuizResponse(Quiz quiz, boolean includeKeywordId) throws Exception {
        List<QuizResponse.OptionDto> parsedOptions = new ArrayList<>();
        List<String> rawOptions = objectMapper.readValue(quiz.getOptionsJson(), new TypeReference<List<String>>() {});
        for (int i = 0; i < rawOptions.size(); i++) {
            parsedOptions.add(new QuizResponse.OptionDto(i + 1, rawOptions.get(i)));
        }

        return new QuizResponse(
                quiz.getId(),
                quiz.getNewsId(),
                includeKeywordId ? quiz.getKeywordId() : null,
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
                earnedPoint,
                user.getPoint()
        );

        QuizSubmitResponse.LearningResultDto learningResult = new QuizSubmitResponse.LearningResultDto(
                earnedPoint,
                user.getPoint(),
                true
        );

        return new QuizSubmitResponse(
                quiz.getId(),
                request.getSelected_answer(),
                isCorrect,
                quiz.getAnswer(),
                quiz.getExplanation(),
                pointResult,
                learningResult
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
            System.out.println("[제출 기록 저장 완료] 유저: " + userId + ", 퀴즈: " + quiz.getId() + ", 획득 포인트: " + earnedPoint);
        } else {
            System.out.println("⚠️ 이미 풀었던 퀴즈 재제출 감지 -> 포인트 지급을 스킵합니다. 유저: " + userId);
        }

        QuizSubmitResponse.PointResultDto pointResult = new QuizSubmitResponse.PointResultDto(
                earnedPoint,
                user.getPoint()
        );

        QuizSubmitResponse.LearningResultDto learningResult = new QuizSubmitResponse.LearningResultDto(
                earnedPoint,
                user.getPoint(),
                true
        );

        return new QuizSubmitResponse(
                quiz.getId(),
                request.getSelected_answer(),
                isCorrect,
                quiz.getAnswer(),
                quiz.getExplanation(),
                pointResult,
                learningResult
        );
    }

    @Transactional(readOnly = true)
    public QuizResponse getKeywordQuizByKeywordId(Long keywordId) {
        Quiz quiz = quizRepository.findByKeyword_IdAndQuizType(keywordId, "KEYWORD")
                .orElseThrow(() -> new IllegalArgumentException("해당 키워드 퀴즈가 존재하지 않습니다."));

        try {
            return convertToQuizResponse(quiz, true);
        } catch (Exception e) {
            throw new RuntimeException("퀴즈 조회 처리 중 파싱 오류가 발생했습니다.", e);
        }
    }

    @Transactional(readOnly = true)
    public NewsDetailResponse getNewsDetailWithKeywords(Long newsId) {
        DailyNews news = dailyNewsRepository.findById(newsId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 뉴스 기사 ID"));

        List<Keyword> keywordList = keywordRepository.findByNewsId(newsId);

        List<NewsDetailResponse.RelatedKeywordDto> relatedKeywords = keywordList.stream()
                .map(k -> new NewsDetailResponse.RelatedKeywordDto(
                        k.getId(),
                        k.getWord(),
                        k.getKeywordType(),
                        k.getExplanation()
                )).toList();

        return new NewsDetailResponse(
                news.getId(),
                convertToEngCategory(news.getCategory()),
                news.getTitle(),
                news.getSummary(),
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
}