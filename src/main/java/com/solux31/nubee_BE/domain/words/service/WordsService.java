package com.solux31.nubee_BE.domain.words.service;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.auth.repository.UserRepository;
import com.solux31.nubee_BE.domain.news.dto.NewsAnalysisResult;
import com.solux31.nubee_BE.domain.news.exception.NewsException;
import com.solux31.nubee_BE.domain.news.exception.code.NewsErrorCode;
import com.solux31.nubee_BE.domain.words.dto.Response.WordsResDTO;
import com.solux31.nubee_BE.domain.words.entity.Keyword;
import com.solux31.nubee_BE.domain.words.entity.mapping.UserKeyword;
import com.solux31.nubee_BE.domain.words.exception.WordsException;
import com.solux31.nubee_BE.domain.words.exception.code.WordsErrorCode;
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
                .orElseThrow(() -> new WordsException(WordsErrorCode.KEYWORD_NOT_FOUND));
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
}