package com.solux31.nubee_BE.domain.words.repository;

import com.solux31.nubee_BE.domain.words.entity.Keyword;
import com.solux31.nubee_BE.domain.words.entity.mapping.UserKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    // 변경 사항: 단일 결과를 전제하여 데이터 정합성을 깨뜨릴 수 있는 전역 findByWord 계약을 제거함

    /**
     * 특정 뉴스 ID에 속한 모든 키워드(MAIN, SUB 전체)를 조회함
     *
     * @param newsId DailyNews 테이블의 ID
     * @return 해당 뉴스에 포함된 키워드 리스트
     */
    List<Keyword> findByNewsId(Long newsId);

    /**
     * 특정 뉴스 ID 안에서 메인 키워드 혹은 서브 키워드만 골라서 조회함
     *
     * @param newsId      DailyNews 테이블의 ID
     * @param keywordType "MAIN" 또는 "SUB"
     * @return 필터링된 키워드 리스트
     */
    List<Keyword> findByNewsIdAndKeywordType(Long newsId, String keywordType);

    /**
     * 특정 뉴스 ID 내에서 단어 이름(word)을 기준으로 기존에 등록된 키워드가 있는지 정확하게 조회함
     * 1단계 중복 체크 및 2단계 설명(뜻) 업데이트 시 복합 식별자로 사용함
     *
     * @param word 찾고자 하는 단어 이름 (ex: "금리", "우주선")
     * @param newsId 연관된 뉴스 외래키 ID
     * @return 일치하는 Keyword 엔티티
     */
    Optional<Keyword> findByWordAndNewsId(String word, Long newsId);
}