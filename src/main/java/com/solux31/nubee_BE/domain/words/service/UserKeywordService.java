package com.solux31.nubee_BE.domain.words.service;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.words.dto.Response.WordsResDTO;
import com.solux31.nubee_BE.domain.words.entity.Keyword;
import com.solux31.nubee_BE.domain.words.entity.mapping.UserKeyword;
import com.solux31.nubee_BE.domain.words.exception.WordsException;
import com.solux31.nubee_BE.domain.words.exception.code.WordsErrorCode;
import com.solux31.nubee_BE.domain.words.repository.KeywordRepository;
import com.solux31.nubee_BE.domain.words.repository.UserKeywordRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserKeywordService {

    private final UserKeywordRepository userKeywordRepository;
    private final KeywordRepository keywordRepository;
    private final UserRepository userRepository;

    // 단어 리스트 조회
    @Transactional(readOnly = true)
    public WordsResDTO getWords(Long userId) {
        List<UserKeyword> allKeywords = userKeywordRepository.findByUserId(userId);
        LocalDate today = LocalDate.now();

        List<WordsResDTO.WordItem> todayWords = allKeywords.stream()
                .filter(uk -> uk.getCreatedAt().toLocalDate().equals(today))
                .map(uk -> new WordsResDTO.WordItem(
                        uk.getId(),
                        uk.getKeyword().getId(),
                        uk.getKeyword().getWord(),
                        uk.getKeyword().getExplanation(),
                        uk.getKeyword().getExampleSentence()
                ))
                .collect(Collectors.toList());

        List<WordsResDTO.WordItem> previousWords = allKeywords.stream()
                .filter(uk -> uk.getCreatedAt().toLocalDate().isBefore(today))
                .map(uk -> new WordsResDTO.WordItem(
                        uk.getId(),
                        uk.getKeyword().getId(),
                        uk.getKeyword().getWord(),
                        uk.getKeyword().getExplanation(),
                        uk.getKeyword().getExampleSentence()
                ))
                .collect(Collectors.toList());

        return new WordsResDTO(todayWords, previousWords);
    }

    // 단어 삭제
    @Transactional
    public void deleteWord(Long wordId, Long userId) {
        UserKeyword userKeyword = userKeywordRepository.findByIdAndUserId(wordId, userId)
                .orElseThrow(() -> new WordsException(WordsErrorCode.WORD_NOT_FOUND));
        userKeywordRepository.delete(userKeyword);
    }

    // 단어장에 추가
    @Transactional
    public void addWord(Long keywordId, Long userId) {

        // 중복 확인
        if (userKeywordRepository.existsByUserIdAndKeywordId(userId, keywordId)) {
            throw new WordsException(WordsErrorCode.DUPLICATE_WORD);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new WordsException(WordsErrorCode.USER_NOT_FOUND));

        Keyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new WordsException(WordsErrorCode.KEYWORD_NOT_FOUND));

        UserKeyword userKeyword = UserKeyword.builder()
                .user(user)
                .keyword(keyword)
                .build();

        userKeywordRepository.save(userKeyword);
    }
}