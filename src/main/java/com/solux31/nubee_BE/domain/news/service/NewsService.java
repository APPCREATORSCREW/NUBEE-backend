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

    private final NewsApiService newsApiService; // 💡 여기에 fetchNewsByCategory 기능이 들어있는지 다음 세션에 체크!
    private final GeminiService geminiService;
    private final WordService wordService;
    private final DailyNewsRepository dailyNewsRepository;
    private final QuizRepository quizRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NewsTransactionHelper newsTransactionHelper;
    private final UserRepository userRepository;
    private final UserQuizLogRepository userQuizLogRepository;
    private final KeywordRepository keywordRepository;

    /**
     * [전체 데일리 뉴스 생성 파이프라인]
     * 카테고리별 분할 수집(총 8개) 및 균형 있는 적재를 처리합니다.
     */

    public void executeDailyNewsWorkflow() {
        System.out.println("[배치 시작] 오늘의 뉴스를 위해 기존 뉴스 및 퀴즈 데이터를 정리합니다.");

        // 1. 삭제 처리를 별도 트랜잭션으로 묶어 즉시 Commit 및 락 해제
        cleanOldNewsAndQuizzes();

        String[] categories = {"101", "102", "105", "104"};

        for (String categoryId : categories) {
            String categoryName = convertCategoryName(categoryId);
            System.out.println("[" + categoryName + "] 카테고리 기사 수집 중...");
            List<NaverNewsResponse.NaverNewsItem> naverNewsList = newsApiService.fetchNewsByCategory(categoryId, 2);

            if (naverNewsList == null || naverNewsList.isEmpty()) {
                continue;
            }

            if (naverNewsList.size() > 1) {
                naverNewsList = naverNewsList.subList(0, 1);
            }

            for (NaverNewsResponse.NaverNewsItem naverNews : naverNewsList) {
                try {
                    // REQUIRES_NEW 트랜잭션 안에서 수집 및 저장이 수행됨 (T1 잠금이 풀려있어 즉시 수행 가능!)
                    String mainKeyword = newsTransactionHelper.processSingleNews(naverNews, categoryName);

                    if (mainKeyword != null) {
                        List<String> singleKeywordList = List.of(mainKeyword);
                        // 이 마스터 퀴즈 생성 로직 내부에서도 별개 커밋이 되도록 흐름을 타게 함
                        saveMasterKeywordsAndQuizzesInTransaction(singleKeywordList, categoryName);
                    }
                } catch (Exception e) {
                    System.err.println("❌ [" + categoryName + "] 특정 뉴스 파이프라인 실패로 패스합니다: " + naverNews.getLink());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 기존 데이터를 비우는 쓰기 작업에만 짧게 트랜잭션을 적용하여
     * 메서드 탈출 시 즉시 락을 해제합니다.
     */
    @Transactional
    public void cleanOldNewsAndQuizzes() {
        quizRepository.deleteAllInBatch();
        dailyNewsRepository.deleteAllInBatch();
    }

    /**
     * 마스터 키워드 및 통합 퀴즈 저장을 위한 트랜잭션 분리
     */
    @Transactional
    public void saveMasterKeywordsAndQuizzesInTransaction(List<String> singleKeywordList, String categoryName) {
        List<MainKeywordResult> masterResults = generateMasterKeywordsAndQuizzes(singleKeywordList);

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
                        .category(categoryName)
                        .build();
                quizRepository.save(keywordQuiz);
            } catch (Exception e) {
                System.err.println("키워드 마스터 퀴즈 DB 저장 중 오류 발생");
                e.printStackTrace();
            }
        }
    }

    /**
     * 네이버 카테고리 코드를 서비스 내부에서 사용하는 한글 카테고리명으로 매핑하는 헬퍼 메서드
     */
    private String convertCategoryName(String categoryId) {
        return switch (categoryId) {
            case "101" -> "경제"; // Naver 101: 경제
            case "102" -> "사회"; // Naver 102: 사회
            case "105" -> "과학"; // Naver 105: IT/과학
            case "104" -> "세계"; // Naver 104: 세계 (혹은 기존 100을 "세계"로 대용해서 사용했다면 case "100" -> "세계" 로 일치화 필요)
            default -> "일반상식";
        };
    }

    // 8개 메인 키워드 통합 퀴즈 및 어린이용 단어 뜻 설명 생성 (기존 로직 유지)
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
                        "2. explanation: **[반드시 아래의 5단계 구조와 줄바꿈(\\n), 말투를 유사하게 지켜서 작성해줘]**\n" +
                        "   - [1단계: 한 줄 정의]: '[단어명]는/은 ~예요/이에요!' 형태로 단어의 핵심을 쉽고 명확하게 한 줄로 정의.\n" +
                        "   - [2단계: 일상 예시]: '예를 들어볼게요.'로 시작하여, 아이들이 친구나 가족 사이, 혹은 일상생활에서 쉽게 겪을 수 있는 구체적인 예시나 상황(숫자나 비유 활용)을 넣어 친절하게 설명.\n" +
                        "   - [3단계: 인과 현상 1]: 이 단어가 가진 개념이나 속성이 '높아지거나/강해지거나/많아지면' 일상이나 경제/사회/과학 분야에 어떤 변화나 현상이 생기는지 초등 눈높이로 설명.\n" +
                        "   - [4단계: 인과 현상 2]: 이 단어가 가진 개념이나 속성이 '낮아지거나/약해지거나/적어지면' 어떻게 되는지 반대 상황을 설명하거나, 아이들이 추가로 알아야 할 핵심 연관 현상을 설명.\n" +
                        "   - [5단계: 요약 마감]: '이것만 기억해요!' 문구를 넣은 뒤, 핵심 규칙 3가지를 글머리 기호(•), 화살표(➔), 상태 기호(↑, ↓)를 조합하여 깔끔하게 요약 마무리.\n\n" +

                        "3. keywordQuiz: 단어의 쓰임새와 현상을 묻는 4지선다 객관식 퀴즈\n" +
                        "   - 🚨 [단순 뜻 풀이 절대 금지]: '이 단어의 뜻은 무엇일까요?' 같은 단순 정의 찾기 문제는 절대 출제하지 마.\n" +
                        "   - 반드시 위 [explanation]의 3, 4단계에서 다룬 현상이나 인과관계(예: ~가 어떻게 되면 어떤 일이 일어날까요?)를 질문으로 던져줘.\n" +
                        "   - keywordQuiz.explanation(퀴즈 해설): 최소 2문장 이상으로 왜 그 보기들이 오답이고 정답인지 아이 눈높이에서 원리를 조곤조곤 설명해줘.\n\n" +

                        "[⚠️ 엄격한 예외 처리 및 탈선 방지 규칙]\n" +
                        "- 특정 단어(예: 금리)의 예시 문장이나 숫자를 다른 단어에 그대로 베껴 쓰지 마. 입력된 단어의 본질에 맞는 완전히 새로운 독창적인 일상 예시를 창작해내야 해.\n" +
                        "- 답할 때 앞뒤로 수식어나 인사말, 또는 마크다운 주석(```json ... ```)을 절대 붙이지 말고, 오직 [로 시작해서 ]로 끝나는 순수한 JSON 배열(Array) 데이터만 반환해.\n\n" +

                        "[출력 포맷 (JSON 배열 구조 가이드)]\n" +
                        "[\n" +
                        "  {\n" +
                        "    \"keyword\": \"입력된 단어 이름\",\n" +
                        "    \"explanation\": \"[1단계 한 줄 정의]\\n\\n[2단계 예를 들어볼게요. 로 시작하는 일상 예시 바디]\\n\\n[3단계 개념이 높아지거나 강해질 때의 현상]\\n\\n[4단계 개념이 낮아지거나 약해질 때의 현상]\\n\\n이것만 기억해요!\\n• [핵심요약 1] ➔ [현상 1] ↑\\n• [핵심요약 2] ➔ [현상 2] 쉬워짐\\n• [핵심요약 3]\",\n" +
                        "    \"keywordQuiz\": {\n" +
                        "      \"question\": \"단어의 인과관계를 묻는 맥락 질문\",\n" +
                        "      \"options\": [\"선지1\", \"선지2\", \"선지3\", \"선지4\"],\n" +
                        "      \"answer\": 0,\n" +
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
            return objectMapper.readValue(jsonResponse, new TypeReference<List<MainKeywordResult>>() {});
        } catch (Exception e) {
            System.err.println("마스터 키워드 통합 Gemini 분석 및 파싱 중 에러 발생");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    @Transactional(readOnly = true)
    public TodayNewsResponse getBalancedTodayNewsForUser(Long userId) {
        // 1. 유저 테이블에서 해당 유저의 설정 정보 가져오기
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 2. ERD에 명시된 preferred_keyword_count 컬럼 값 꺼내기
        int userPreferredCount = user.getPreferredKeywordCount();

        // 3~6개 범위를 벗어나는 예외 상황 방어벽
        if (userPreferredCount < 3 || userPreferredCount > 6) {
            userPreferredCount = 6; // 에러 방지용 기본값 스왑
        }

        // 3. 오늘 DB에 저장된 데일리 뉴스 8개 로드
        List<DailyNews> allTodayNews = dailyNewsRepository.findAll();

        if (allTodayNews == null || allTodayNews.isEmpty()) {
            return new TodayNewsResponse(0, new ArrayList<>());
        }

        // 4. 지그재그 배치 알고리즘 실행
        List<DailyNews> balancedList = rearrangeByCategorySequence(allTodayNews);

        // 5. 유저가 설정한 preferred_keyword_count 만큼 정확하게 리스트 슬라이싱
        int targetSize = Math.min(userPreferredCount, balancedList.size());
        List<DailyNews> selectedNews = balancedList.subList(0, targetSize);

        // 6. DTO 변환 및 매핑 (기존 로직 동일)
        List<TodayNewsResponse.NewsDto> newsDtoList = selectedNews.stream().map(news -> {

            // 메인 키워드명(news.getMainKeyword())과 기사 ID를 기반으로 실제 DB에 누적된 단어 엔티티 조회
            Keyword realKeyword = keywordRepository.findByWordAndNewsId(news.getMainKeyword(), news.getNewsId())
                    .orElse(null);

            // 안전장치: 혹시라도 매칭되는 키워드 데이터가 DB에 없다면 기본 템플릿 정보로 복원
            Long keywordId = (realKeyword != null) ? realKeyword.getKeywordId() : 999L;
            String wordName = (realKeyword != null) ? realKeyword.getWord() : news.getMainKeyword();
            String explanation = (realKeyword != null) ? realKeyword.getExplanation() : "이 단어에 대한 알찬 설명이 준비되고 있어요.";

            // 메인 단어 카드에는 '예문'이 무조건 들어가야 하므로 DB에서 꺼내오기
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
                    news.getNewsId(),
                    convertToEngCategory(news.getCategory()),
                    news.getTitle(),
                    news.getSummary(),
                    news.getImageUrl() != null ? news.getImageUrl() : "기본이미지URL", // 하드코딩 걷어내고 실제 기사 이미지 연동
                    keywordDto
            );
        }).toList();

        return new TodayNewsResponse(newsDtoList.size(), newsDtoList);
    }

    /**
     * [명세서 반영] 특정 키워드 ID에 매칭된 KEYWORD 타입 퀴즈 조회 (정답/해설 제외)
     */
    @Transactional(readOnly = true)
    public QuizResponse getKeywordQuizByKeywordId(Long keywordId) {
        // 1. Quiz 레포지토리에서 keywordId와 타입이 "KEYWORD"인 데이터 1건 찾기
        Quiz quiz = quizRepository.findByKeywordIdAndQuizType(keywordId, "KEYWORD")
                .orElseThrow(() -> new IllegalArgumentException("해당 키워드에 연결된 퀴즈 존재하지 않음"));

        // 2. 공통 변환 헬퍼를 사용해 키워드 ID를 포함하여 반환 (includeKeywordId = true)
        return convertToQuizResponse(quiz, true);
    }

    /**
     * [명세서 반영] 특정 뉴스 ID에 매칭된 NEWS 타입 독해 퀴즈 조회 (정답/해설 제외)
     */
    @Transactional(readOnly = true)
    public QuizResponse getNewsQuizByNewsId(Long newsId) {
        // 1. Quiz 레포지토리에서 newsId와 타입이 "NEWS"인 데이터 1건 찾기
        Quiz quiz = quizRepository.findByNewsIdAndQuizType(newsId, "NEWS")
                .orElseThrow(() -> new IllegalArgumentException("해당 뉴스 퀴즈 존재하지 않음"));

        // 2. 공통 변환 헬퍼를 사용하되, 뉴스 퀴즈는 keyword_id가 null로 나가도록 처리 (includeKeywordId = false)
        return convertToQuizResponse(quiz, false);
    }

    /**
     * [공통] DB의 Quiz 엔티티를 프론트엔드 반환용 QuizResponse DTO로 파싱 및 조립
     */
    private QuizResponse convertToQuizResponse(Quiz quiz, boolean includeKeywordId) {
        List<QuizResponse.OptionDto> parsedOptions = new ArrayList<>();

        try {
            // DB에 텍스트(JSON)로 저장된 ["선지1", "선지2", ...] 복원
            List<String> rawOptions = objectMapper.readValue(quiz.getOptionsJson(), new TypeReference<List<String>>() {});

            // 프론트엔드 요구 스펙(1~4번 인덱싱) 변환
            for (int i = 0; i < rawOptions.size(); i++) {
                parsedOptions.add(new QuizResponse.OptionDto(i + 1, rawOptions.get(i)));
            }
        } catch (Exception e) {
            System.err.println("🚨 퀴즈 보기 JSON 파싱 중 오류 발생");
            e.printStackTrace();
        }

        return new QuizResponse(
                quiz.getQuizId(),
                quiz.getNewsId(),
                includeKeywordId ? quiz.getKeywordId() : null, // 분기에 맞춰 ID 노출 혹은 null 세팅
                quiz.getQuizType(),
                quiz.getQuestion(),
                parsedOptions
        );
    }

    /**
     * [명세서 반영] 1. 키워드 퀴즈 채점 및 포인트 지급
     * POST /api/v1/keywords/{keyword_id}/quiz/submit
     */
    @Transactional
    public QuizSubmitResponse submitAndGradeKeywordQuiz(Long userId, Long keywordId, QuizSubmitRequest request) {

        // 1. 404 방어: 해당 퀴즈가 진짜 존재하는지 조회
        Quiz quiz = quizRepository.findById(request.getQuiz_id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 퀴즈 ID로 채점 요청"));

        // 2. 400 방어: 이미 푼 키워드 퀴즈인지 중복 풀이 검증
        boolean alreadySolved = userQuizLogRepository.existsByUserIdAndQuizId(userId, request.getQuiz_id());
        if (alreadySolved) {
            throw new IllegalArgumentException("이미 완료된 키워드 퀴즈에 대한 제출 요청");
        }

        // 3. 채점 진행 및 포인트 정산 (유저 포인트 누적)
        boolean isCorrect = (quiz.getAnswer() == request.getSelected_answer());
        int earnedPoint = isCorrect ? 1 : 0;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        user.updatePoint(earnedPoint);

        // 4. UserQuizLog 기록 적재
        UserQuizLog quizLog = UserQuizLog.builder()
                .userId(userId)
                .quizId(quiz.getQuizId())
                .category(quiz.getCategory())
                .selectedAnswer(request.getSelected_answer())
                .isCorrect(isCorrect)
                .isCompleted(true)
                .build();
        userQuizLogRepository.save(quizLog);

        // 5. 키워드용 피드백 세팅 (learning_result는 null 대입!)
        QuizSubmitResponse.PointResultDto pointResult = new QuizSubmitResponse.PointResultDto(
                earnedPoint,
                user.getPoint()
        );

        return new QuizSubmitResponse(
                quiz.getQuizId(),
                request.getSelected_answer(),
                isCorrect,
                quiz.getAnswer(),
                quiz.getExplanation(),
                pointResult, // 👈 키워드 결과 주입
                null         // 👈 뉴스 결과는 null
        );
    }

    /**
     * [명세서 반영] 2. 뉴스 퀴즈 채점 및 최종 완료 처리
     * POST /api/v1/news/{news_id}/quiz/submit
     */
    @Transactional
    public QuizSubmitResponse submitAndGradeNewsQuiz(Long userId, QuizSubmitRequest request) {

        // 1. 404 방어: 해당 뉴스 퀴즈 존재 여부 조회
        Quiz quiz = quizRepository.findById(request.getQuiz_id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 퀴즈 ID로 채점 요청"));

        // 2. 400 방어: 이미 푼 뉴스 퀴즈인지 중복 풀이 검증
        boolean alreadySolved = userQuizLogRepository.existsByUserIdAndQuizId(userId, request.getQuiz_id());
        if (alreadySolved) {
            throw new IllegalArgumentException("이미 완료된 퀴즈에 대한 제출 요청");
        }

        // 3. 채점 진행 및 포인트 정산
        boolean isCorrect = (quiz.getAnswer() == request.getSelected_answer());
        int earnedPoint = isCorrect ? 1 : 0;

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        user.updatePoint(earnedPoint);

        // 4. UserQuizLog 기록 적재
        UserQuizLog quizLog = UserQuizLog.builder()
                .userId(userId)
                .quizId(quiz.getQuizId())
                .category(quiz.getCategory())
                .selectedAnswer(request.getSelected_answer())
                .isCorrect(isCorrect)
                .isCompleted(true) // 최종 학습 완료 플래그 적용
                .build();
        userQuizLogRepository.save(quizLog);

        // 5. 뉴스용 피드백 세팅 (point_result는 null 대입!)
        QuizSubmitResponse.LearningResultDto learningResult = new QuizSubmitResponse.LearningResultDto(
                earnedPoint,
                user.getPoint(),
                true // is_completed
        );

        return new QuizSubmitResponse(
                quiz.getQuizId(),
                request.getSelected_answer(),
                isCorrect,
                quiz.getAnswer(),
                quiz.getExplanation(),
                null,           // 👈 키워드 결과는 null
                learningResult  // 👈 뉴스 결과 주입
        );
    }

    /**
     * [명세서 반영] 뉴스 상세 정보 및 해당 뉴스에 엮인 모든 키워드(MAIN/SUB) 추출 로직
     */
    @Transactional(readOnly = true)
    public NewsDetailResponse getNewsDetailWithKeywords(Long newsId) {
        // 1. DB에서 특정 뉴스 기사 가져오기 (없으면 404)
        DailyNews news = dailyNewsRepository.findById(newsId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 뉴스 기사 ID"));

        // 2. 💡 [핵심] Keyword 테이블에서 이 newsId를 소유한 단어들을 모조리 긁어오기!
        // (KeywordRepository에 List<Keyword> findByNewsId(Long newsId); 정의 필요)
        List<Keyword> keywordList = keywordRepository.findByNewsId(newsId);

        // 3. 엔티티 리스트를 프론트엔드가 요구한 관련 키워드 DTO 배열로 가공
        List<NewsDetailResponse.RelatedKeywordDto> relatedKeywords = keywordList.stream()
                .map(k -> new NewsDetailResponse.RelatedKeywordDto(
                        k.getKeywordId(),
                        k.getWord(),
                        k.getKeywordType()
                )).toList();

        // 4. 최종 규격으로 조립하여 리턴 (카테고리는 영문 대문자로)
        return new NewsDetailResponse(
                news.getNewsId(),
                convertToEngCategory(news.getCategory()),
                news.getTitle(),
                news.getSummary(),
                news.getImageUrl() != null ? news.getImageUrl() : "기본이미지URL",
                news.getOriginalUrl() != null ? news.getOriginalUrl() : "원문출처없음",
                relatedKeywords
        );
    }

     // [추가] 카테고리별 뉴스를 [경제, 사회, 과학, 세계, 경제, 사회...] 순서로 지그재그 배치하여 균등 분배하는 정렬 헬퍼
    private List<DailyNews> rearrangeByCategorySequence(List<DailyNews> source) {
        List<DailyNews> economy = source.stream().filter(n -> "경제".equals(n.getCategory())).toList();
        List<DailyNews> society = source.stream().filter(n -> "사회".equals(n.getCategory())).toList();
        List<DailyNews> science = source.stream().filter(n -> "과학".equals(n.getCategory())).toList();
        List<DailyNews> world = source.stream().filter(n -> "세계".equals(n.getCategory())).toList();

        List<DailyNews> result = new ArrayList<>();
        int maxSize = Math.max(Math.max(economy.size(), society.size()), Math.max(science.size(), world.size()));

        for (int i = 0; i < maxSize; i++) {
            if (i < economy.size()) result.add(economy.get(i));
            if (i < society.size()) result.add(society.get(i));
            if (i < science.size()) result.add(science.get(i));
            if (i < world.size()) result.add(world.get(i));
        }
        return result;
    }
     // 프론트엔드 API 명세서 규격에 맞게 DB의 한글 카테고리명을 영문 대문자로 변환해주는 매퍼
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
