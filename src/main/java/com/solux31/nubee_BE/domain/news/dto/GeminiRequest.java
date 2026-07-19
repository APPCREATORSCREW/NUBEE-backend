package com.solux31.nubee_BE.domain.news.dto;

import lombok.Getter;
import java.util.ArrayList;
import java.util.List;

@Getter
public class GeminiRequest {

    private String model;
    private List<Message> messages;

    // 편리하게 모델명과 프롬프트를 넣어 요청 객체를 만드는 생성자
    public GeminiRequest(String model, String prompt) {
        this.model = model;
        this.messages = new ArrayList<>();
        // AI에게 역할을 부여하는 system 메시지나 일반 user 메시지로 세팅
        this.messages.add(new Message("user", prompt));
    }

    @Getter
    public static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}