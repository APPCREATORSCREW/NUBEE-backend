package com.solux31.nubee_BE.domain.words.service;

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

    /**
     * [1단계 연동] 메인 키워드와 서브 키워드들을 받아 DB에 중복 없이 저장
     * 엔티티의 nullable = false 제약 조건을 충족하기 위해 newsId를 인자로 받음
     */
    @Transactional
    public void saveKeywords(String mainKeywordName, List<String> subKeywordNames, Long newsId) {

        // 1. 메인 키워드 중복 체크 후 저장 (타입을 "MAIN"으로 명시)
        saveIfAbsent(mainKeywordName, "MAIN", newsId);

        // 2. 서브 키워드 리스트 돌면서 중복 체크 후 저장 (타입을 "SUB"으로 명시)
        if (subKeywordNames != null) {
            for (String subName : subKeywordNames) {
                saveIfAbsent(subName, "SUB", newsId);
            }
        }
    }

    /**
     * [2단계 연동] Gemini가 새로 생성한 마스터 설명(뜻)을 단어 테이블에 업데이트
     */
    @Transactional
    public Long updateKeywordExplanations(String keywordName, String explanation) {
        // 1. keywordRepository를 사용해 단어를 찾음
        Optional<Keyword> keywordOpt = keywordRepository.findByWord(keywordName);

        if (keywordOpt.isPresent()) {
            Keyword keyword = keywordOpt.get();

            // 2. 엔티티 내부에 작성한 업데이트 비즈니스 메서드를 호출
            keyword.updateExplanation(explanation);

            // 3. 퀴즈 저장할 때 쓸 수 있도록, 이 단어의 고유 ID를 반환(return)
            return keyword.getKeywordId();
        }

        // 4. 만약 단어를 찾지 못했다면 null을 반환
        System.err.println("⚠️ [경고] updateKeywordExplanations 도중 단어를 찾지 못했습니다: " + keywordName);
        return null;
    }

    /**
     * 이미 존재하는 단어인지 검사하고, 없을 때만 새 엔티티를 만들어 저장하는 헬퍼 메서드
     */
    private void saveIfAbsent(String wordName, String type, Long newsId) {
        if (wordName == null || wordName.trim().isEmpty()) return;

        // 레포지토리 규격에 맞춰 findByWord로 변경
        Optional<Keyword> existingKeyword = keywordRepository.findByWord(wordName);

        if (existingKeyword.isEmpty()) {
            Keyword newKeyword = Keyword.builder()
                    .word(wordName)
                    .explanation("")     // 1단계 시점에는 설명이 아직 없으므로 빈 문자열(nullable=false 대비)
                    .keywordType(type)   // "MAIN" 또는 "SUB"
                    .newsId(newsId)      // 연관된 뉴스 외래키 ID
                    .build();

            keywordRepository.save(newKeyword);
        }
    }
    
    @Transactional(readOnly = true)
    public KeywordDetailResponse getKeywordDetail(Long keywordId) {
        // 1. DB에서 키워드 ID로 조회하고, 없으면 404용 예외(IllegalArgumentException) 발생시키기
        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 키워드 ID"));

        // 2. 조회한 엔티티 데이터를 명세서 규격 DTO에 알맞게 매핑해서 리턴!
        // (주의: 엔티티의 실제 컬럼명 필드에 맞춰 대입해 주시면 됩니다. 예: keyword.getWord() 등)
        return new KeywordDetailResponse(
                keyword.getKeywordId(),
                keyword.getWord(),
                keyword.getExplanation(),
                keyword.getExampleSentence() != null ? keyword.getExampleSentence() : "예문이 존재하지 않습니다.", // null 방어
                keyword.getKeywordType() != null ? keyword.getKeywordType() : "MAIN"
        );
    }
}
