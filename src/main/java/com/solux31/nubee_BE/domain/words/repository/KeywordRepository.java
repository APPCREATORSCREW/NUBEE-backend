package com.solux31.nubee_BE.domain.words.repository;

import com.solux31.nubee_BE.domain.words.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Long> {
    // 특정 뉴스 기사에 포함된 모든 핵심 단어 목록을 뽑아오는 메서드
    List<Keyword> findByNewsId(Long newsId);
}
