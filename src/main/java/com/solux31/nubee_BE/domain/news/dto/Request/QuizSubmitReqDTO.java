package com.solux31.nubee_BE.domain.news.dto.Request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuizSubmitReqDTO {
    private Long quiz_id;             // 풀이한 퀴즈 ID
    private int selected_answer;      // 사용자가 고른 보기 번호 (1~4)
}
