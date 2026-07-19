package com.solux31.nubee_BE.domain.words.service;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.news.dto.NewsAnalysisResult;
import com.solux31.nubee_BE.domain.words.dto.Response.WordsResDTO;
import com.solux31.nubee_BE.domain.words.entity.Keyword;
import com.solux31.nubee_BE.domain.words.entity.mapping.UserKeyword;
import com.solux31.nubee_BE.domain.words.repository.KeywordRepository;
import com.solux31.nubee_BE.domain.words.repository.UserKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WordsService {

    private final KeywordRepository keywordRepository;
    private final UserRepository userRepository;
    private final UserKeywordRepository userKeywordRepository;

    // 단어 리스트 조회
    @Transactional(readOnly = true)
    public List<WordsResDTO> getWords(Long userId) {
        return userKeywordRepository.findByUserId(userId)
                .stream()
                .map(uk -> new WordsResDTO(
                        uk.getId(),
                        uk.getKeyword().getId(),
                        uk.getKeyword().getWord(),
                        uk.getKeyword().getExplanation(),
                        uk.getKeyword().getExampleSentence()
                ))
                .collect(Collectors.toList());
    }

    // 단어 삭제
    @Transactional
    public void deleteWord(Long wordId, Long userId) {
        UserKeyword userKeyword = userKeywordRepository.findByIdAndUserId(wordId, userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 단어입니다."));
        userKeywordRepository.delete(userKeyword);
    }

    // 단어장에 추가
    @Transactional
    public void addWord(Long keywordId, Long userId) {

        // 중복 확인
        if (userKeywordRepository.existsByUserIdAndKeywordId(userId, keywordId)) {
            throw new IllegalArgumentException("이미 단어장에 있는 단어입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 키워드입니다."));

        UserKeyword userKeyword = UserKeyword.builder()
                .user(user)
                .keyword(keyword)
                .build();

        userKeywordRepository.save(userKeyword);
    }

    /**
     * 🔥 [추가] 크롤링 파이프라인용 키워드 중복 방지 저장 로직
     * @param mainKeywordName 메인 키워드명
     * @param subKeywords 서브 키워드 DTO 리스트
     * @param newsId 현재 저장된 DailyNews ID (향후 매핑 필요시 활용)
     */
    @Transactional
    public void saveKeywords(String mainKeywordName, List<NewsAnalysisResult.SubKeyword> subKeywords, Long newsId) {

        // 1. 메인 키워드 중복 방어 처리
        if (mainKeywordName != null && !mainKeywordName.trim().isEmpty()) {
            String cleanedMain = mainKeywordName.trim();

            keywordRepository.findByWord(cleanedMain)
                    .orElseGet(() -> keywordRepository.saveAndFlush(
                            Keyword.builder()
                                    .word(cleanedMain)
                                    .explanation("오늘의 핵심 뉴스 키워드입니다.")
                                    .exampleSentence("뉴스 본문을 읽으며 단어의 맥락을 파악해 보세요.")
                                    .build()
                    ));
        }

        // 2. 서브 키워드 세트 중복 방어 처리
        if (subKeywords != null) {
            for (NewsAnalysisResult.SubKeyword sub : subKeywords) {
                if (sub.getWord() == null || sub.getWord().trim().isEmpty()) continue;

                String cleanedSub = sub.getWord().trim();

                // DB에 있으면 기존 Keyword 사용, 없으면 새로 들어온 초등 설명글과 함께 인서트
                keywordRepository.findByWord(cleanedSub)
                        .orElseGet(() -> keywordRepository.saveAndFlush(
                                Keyword.builder()
                                        .word(cleanedSub)
                                        .explanation(sub.getExplanation()) // Gemini가 만든 눈높이 해설
                                        .exampleSentence("문장 속에서 이 단어가 어떻게 쓰였는지 확인해 볼까요?")
                                        .build()
                        ));
            }
        }
    }
}