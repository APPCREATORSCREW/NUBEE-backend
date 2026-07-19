package com.solux31.nubee_BE.domain.words.service;

import com.solux31.nubee_BE.domain.news.dto.NewsAnalysisResult;
import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import com.solux31.nubee_BE.domain.news.repository.DailyNewsRepository;
import com.solux31.nubee_BE.domain.words.dto.KeywordDetailResponse;
import com.solux31.nubee_BE.domain.words.entity.Keyword;
import com.solux31.nubee_BE.domain.words.repository.KeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WordService {

    private final KeywordRepository keywordRepository;
    private final DailyNewsRepository dailyNewsRepository;

    /**
     * [1단계 연동] 메인 키워드와 서브 키워드들을 받아 DB에 중복 없이 저장
     * 엔티티의 nullable = false 제약 조건을 충족하기 위해 newsId를 인자로 받음
     * subKeywordNames 대신 객체 리스트(subKeywords)를 받아 단어와 뜻을 함께 저장
     */
    @Transactional
    public void saveKeywords(String mainKeywordName, List<NewsAnalysisResult.SubKeyword> subKeywords, Long newsId) {

        // 1. 메인 키워드 중복 체크 후 저장 (타입을 "MAIN"으로 명시, 1단계 시점에는 뜻이 없으므로 빈 문자열)
        saveIfAbsent(mainKeywordName, "", "MAIN", newsId);

        // 2. 서브 키워드 리스트 돌면서 중복 체크 후 저장 (타입을 "SUB"으로 명시, 뜻 설명도 함께 저장)
        if (subKeywords != null) {
            for (NewsAnalysisResult.SubKeyword sub : subKeywords) {
                saveIfAbsent(sub.getWord(), sub.getExplanation(), "SUB", newsId);
            }
        }
    }

    /**
     * [2단계 연동] Gemini가 새로 생성한 마스터 설명(뜻)을 단어 테이블에 업데이트
     * 단어 식별의 정확도를 위해 newsId를 인자로 받아 복합 조회로 대상을 제한함
     */
    @Transactional
    public Long updateKeywordExplanations(String keywordName, String explanation, String exampleSentence, Long newsId) {
        // 1. keywordRepository를 사용해 (word, newsId) 쌍으로 정확한 단어를 찾음
        Optional<Keyword> keywordOpt = keywordRepository.findByWordAndDailyNewsId(keywordName, newsId);

        if (keywordOpt.isPresent()) {
            Keyword keyword = keywordOpt.get();

            // 2. 엔티티 내부에 작성한 업데이트 비즈니스 메서드를 호출
            keyword.updateExplanation(explanation);
            keyword.updateExampleSentence(exampleSentence);

            // 3. 퀴즈 저장할 때 쓸 수 있도록, 이 단어의 고유 ID를 반환
            return keyword.getId();
        }

        // 4. 만약 단어를 찾지 못했다면 null을 반환
        System.err.println("⚠️ [경고] updateKeywordExplanations 도중 단어를 찾지 못함: " + keywordName + " (newsId: " + newsId + ")");
        return null;
    }

    /**
     * 이미 존재하는 단어인지 검사하고, 없을 때만 새 엔티티를 만들어 저장
     * 변경 사항: 전역 단어 중복이 아닌, 동일 뉴스(newsId) 안에서 동일 단어가 중복되는지 검사함
     */
    private void saveIfAbsent(String wordName, String explanation, String type, Long newsId) {
        if (wordName == null || wordName.trim().isEmpty()) return;

        Optional<Keyword> existingKeyword = keywordRepository.findByWordAndDailyNewsId(wordName, newsId);

        if (existingKeyword.isEmpty()) {
            DailyNews proxyNews = dailyNewsRepository.getReferenceById(newsId);

            Keyword newKeyword = Keyword.builder()
                    .word(wordName)
                    .explanation(explanation != null ? explanation : "") // 받아온 뜻 정보를 빌더에 세팅 (null 방어)
                    .keywordType(type)   // "MAIN" 또는 "SUB"
                    .dailyNews(proxyNews)    // 연관된 뉴스 외래키 ID
                    .build();

            newKeyword.setDailyNews(proxyNews);
            keywordRepository.save(newKeyword);
        }
    }

    @Transactional(readOnly = true)
    public KeywordDetailResponse getKeywordDetail(Long keywordId) {
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 키워드 ID"));

        // 기본적으로 예문을 가공해서 꺼내오되
        String example = keyword.getExampleSentence();

        // 만약 SUB 타입(본문 팝업용 단순 단어)이라면 예문은 비우기
        if ("SUB".equals(keyword.getKeywordType())) {
            example = null; // 또는 "" (빈 문자열)
        } else {
            // MAIN 키워드인데 DB에 예문이 비어있을 때를 위한 안전장치
            if (example == null || example.trim().isEmpty()) {
                example = "'" + keyword.getWord() + "' 단어가 쓰인 멋진 예문을 준비 중이야!";
            }
        }

        return new KeywordDetailResponse(
                keyword.getId(),
                keyword.getWord(),
                keyword.getExplanation(),
                example, // 가공된 예문 대입 (SUB는 null, MAIN은 알찬 예문)
                keyword.getKeywordType()
        );
    }
}