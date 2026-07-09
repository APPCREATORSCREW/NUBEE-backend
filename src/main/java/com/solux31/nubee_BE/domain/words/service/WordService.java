package com.solux31.nubee_BE.domain.words.service;

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
     * [1단계 연동] 메인 키워드와 서브 키워드들을 받아 DB에 중복 없이 저장합니다.
     * 엔티티의 nullable = false 제약 조건을 충족하기 위해 newsId를 인자로 받습니다.
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
     * [2단계 연동] Gemini가 새로 생성한 마스터 설명(뜻)을 단어 테이블에 업데이트합니다.
     */
    @Transactional
    public void updateKeywordExplanations(String keywordName, String explanation) {
        // 엔티티의 실제 필드명에 맞추어 findByWord로 수정
        keywordRepository.findByWord(keywordName)
                .ifPresent(keyword -> {
                    // 엔티티 내부에 작성하신 업데이트 비즈니스 메서드를 호출
                    keyword.updateExplanation(explanation);
                });
    }

    /**
     * 이미 존재하는 단어인지 검사하고, 없을 때만 새 엔티티를 만들어 저장하는 헬퍼 메서드
     */
    private void saveIfAbsent(String wordName, String type, Long newsId) {
        if (wordName == null || wordName.trim().isEmpty()) return;

        // 레포지토리 규격에 맞춰 findByWord로 변경
        Optional<Keyword> existingKeyword = keywordRepository.findByWord(wordName);

        if (existingKeyword.isEmpty()) {
            // Keyword 엔티티의 실제 필드명(word 등)과 builder 규격을 일치시켰습니다.
            Keyword newKeyword = Keyword.builder()
                    .word(wordName)
                    .explanation("")     // 1단계 시점에는 설명이 아직 없으므로 빈 문자열(nullable=false 대비)
                    .keywordType(type)   // "MAIN" 또는 "SUB"
                    .newsId(newsId)      // 연관된 뉴스 외래키 ID
                    .build();

            keywordRepository.save(newKeyword);
        }
    }
}
