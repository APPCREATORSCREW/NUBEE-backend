package com.solux31.nubee_BE.domain.news.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.news.dto.*;
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

    /**
     * [전체 데일리 뉴스 생성 파이프라인]
     * 카테고리별 분할 수집(총 8개) 및 균형 있는 적재를 처리합니다.
     */
    @Transactional
    public void executeDailyNewsWorkflow() {
        System.out.println("[배치 시작] 오늘의 뉴스를 위해 기존 뉴스 및 퀴즈 데이터를 정리합니다.");

        // 연관관계 자식 테이블부터 순서대로 비워주기 (외래키 에러 방지)
        quizRepository.deleteAllInBatch();      // 퀴즈 삭제
        dailyNewsRepository.deleteAllInBatch(); // 뉴스 삭제

        // 1. 수집할 네이버 뉴스 카테고리 ID 배열 (정치/국제, 경제, 사회, IT/과학)
        String[] categories = {"100", "101", "102", "105"};

        System.out.println(" 카테고리별 네이버 API 분할 호출 시작...");

        // 2. 카테고리별로 루프 돌기
        for (String categoryId : categories) {
            String categoryName = convertCategoryName(categoryId); // 한글 카테고리명 변환 ("101" -> "경제")

            System.out.println("[" + categoryName + "] 카테고리 기사 수집 중...");
            List<NaverNewsResponse.NaverNewsItem> naverNewsList = newsApiService.fetchNewsByCategory(categoryId, 2);

            if (naverNewsList == null || naverNewsList.isEmpty()) {
                System.out.println("⚠️ " + categoryName + " 카테고리에 수집된 기사가 없습니다.");
                continue;
            }

            // 테스트 기간 크레딧 세이브를 위한 subList 장치
            if (naverNewsList.size() > 1) { naverNewsList = naverNewsList.subList(0, 1); }

            // 3단계: 해당 카테고리의 뉴스별 본문 확보 및 1단계 연성
            for (NaverNewsResponse.NaverNewsItem naverNews : naverNewsList) {
                try {
                    String mainKeyword = newsTransactionHelper.processSingleNews(naverNews, categoryName);

                    // 💡 [수정] 기사 하나당 메인 키워드가 정상적으로 추출되었다면, 바로 그 자리에서 퀴즈를 생성하고 저장합니다!
                    if (mainKeyword != null) {

                        // 단일 키워드를 리스트에 담아 Gemini 메서드에 전달 (기존 메서드 스펙 유지용)
                        List<String> singleKeywordList = List.of(mainKeyword);
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
                                        .category(categoryName) // ➔ 💡 이제 루프 안쪽이므로 현재 기사의 categoryName("경제" 등)이 정확하게 들어갑니다!
                                        .build();
                                quizRepository.save(keywordQuiz);
                            } catch (Exception e) {
                                System.err.println("키워드 마스터 퀴즈 DB 저장 중 오류 발생");
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("❌ [" + categoryName + "] 특정 뉴스 파이프라인 실패로 패스합니다: " + naverNews.getLink());
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 네이버 카테고리 코드를 서비스 내부에서 사용하는 한글 카테고리명으로 매핑하는 헬퍼 메서드
     */
    private String convertCategoryName(String categoryId) {
        return switch (categoryId) {
            case "100" -> "경제";
            case "101" -> "사회";
            case "102" -> "과학";
            case "105" -> "세계";
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

        // 5. ✨ 유저가 설정한 preferred_keyword_count 만큼 정확하게 리스트 슬라이싱!
        int targetSize = Math.min(userPreferredCount, balancedList.size());
        List<DailyNews> selectedNews = balancedList.subList(0, targetSize);

        // 6. DTO 변환 및 매핑 (기존 로직 동일)
        List<TodayNewsResponse.NewsDto> newsDtoList = selectedNews.stream().map(news -> {
            TodayNewsResponse.MainKeywordDto keywordDto = new TodayNewsResponse.MainKeywordDto(
                    12L, news.getMainKeyword(), "설명 텍스트", "예문 텍스트", "MAIN"
            );

            return new TodayNewsResponse.NewsDto(
                    news.getNewsId(),
                    convertToEngCategory(news.getCategory()),
                    news.getTitle(),
                    news.getSummary(),
                    "이미지URL",
                    keywordDto
            );
        }).toList();

        return new TodayNewsResponse(newsDtoList.size(), newsDtoList);
    }

    /**
     * [명세서 반영] 특정 키워드 ID에 매칭된 KEYWORD 타입 퀴즈 조회 (정답/해설 제외 버전)
     */
    @Transactional(readOnly = true)
    public KeywordQuizResponse getKeywordQuizByKeywordId(Long keywordId) {
        // 1. Quiz 레포지토리에서 keywordId와 타입이 "KEYWORD"인 데이터 1건 찾기
        // (💡 QuizRepository에 findByKeywordIdAndQuizType 메서드가 정의되어 있어야 합니다!)
        Quiz quiz = quizRepository.findByKeywordIdAndQuizType(keywordId, "KEYWORD")
                .orElseThrow(() -> new IllegalArgumentException("해당 키워드에 연결된 퀴즈 존재하지 않음"));

        List<KeywordQuizResponse.OptionDto> parsedOptions = new ArrayList<>();

        try {
            // 2. DB에 텍스트(JSON)로 저장된 ["선지1", "선지2", ...] 꺼내서 List<String>으로 복원
            List<String> rawOptions = objectMapper.readValue(quiz.getOptionsJson(), new TypeReference<List<String>>() {});

            // 3. 프론트엔드 요구 스펙에 맞춰 번호(1~4) 매겨서 변환
            for (int i = 0; i < rawOptions.size(); i++) {
                parsedOptions.add(new KeywordQuizResponse.OptionDto(i + 1, rawOptions.get(i)));
            }
        } catch (Exception e) {
            System.err.println("🚨 퀴즈 보기 JSON 파싱 중 오류 발생");
            e.printStackTrace();
        }

        // 4. 최종 DTO 조립 (정답과 해설은 빼고 빌드)
        return new KeywordQuizResponse(
                quiz.getQuizId(),
                quiz.getNewsId(), // 어떤 뉴스와 연결되어 있는지 ERD 매핑값
                quiz.getKeywordId(),
                quiz.getQuizType(),
                quiz.getQuestion(),
                parsedOptions
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
