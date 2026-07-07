package com.solux31.nubee_BE.domain.news.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long quizId;

    @Column(nullable = false)
    private Long newsId; // 어떤 뉴스 기사에 딸린 퀴즈인지

    private Long keywordId; // 단어 퀴즈일 경우 어떤 단어의 퀴즈인지 (뉴스 퀴즈면 null 가능)

    @Column(nullable = false, length = 20)
    private String quizType; // "KEYWORD" (단어퀴즈) 또는 "NEWS" (뉴스독해퀴즈)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question; // 퀴즈 문제 내용

    @Column(nullable = false, columnDefinition = "TEXT")
    private String optionsJson; //퀴즈 보기(옵션) json

    @Column(nullable = false)
    private int answer; // 정답 번호 (예: 1, 2, 3)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation; // 퀴즈 오답노트용 해설지
}
