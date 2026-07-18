package com.solux31.nubee_BE.domain.news.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class GeminiResponse {

    private List<Choice> choices;

    @Getter @Setter
    public static class Choice {
        private Message message;
    }

    @Getter @Setter
    public static class Message {
        private String role;
        private String content;
    }

    // 표준 규격 박스 안에서 AI가 답변한 진짜 텍스트만 쏙 빼오는 편의 메서드
    public String getAnswerText() {
        if (choices != null && !choices.isEmpty() && choices.get(0).getMessage() != null) {
            return choices.get(0).getMessage().getContent();
        }
        return "";
    }
}
