package com.solux31.nubee_BE.domain.words.repository;

import com.solux31.nubee_BE.domain.words.entity.Keyword;
import com.solux31.nubee_BE.domain.words.entity.mapping.UserKeyword;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserKeywordRepository extends JpaRepository<UserKeyword, Long> {

    // 유저의 단어 목록 조회
    List<UserKeyword> findByUserId(Long userId);

    // 특정 유저의 특정 단어 조회
    Optional<UserKeyword> findByIdAndUserId(Long id, Long userId);

    // 이미 단어장에 있는지 확인
    boolean existsByUserIdAndKeywordId(Long userId, Long keywordId);
}
