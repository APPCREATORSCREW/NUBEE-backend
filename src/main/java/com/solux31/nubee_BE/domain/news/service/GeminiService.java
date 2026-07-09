package com.solux31.nubee_BE.domain.news.service;

import com.solux31.nubee_BE.domain.news.dto.GeminiRequest;
import com.solux31.nubee_BE.domain.news.dto.GeminiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeminiService {

    @Value("${llm.gateway.url}")
    private String apiUrl;

    @Value("${llm.gateway.key}")
    private String apiKey;

    @Value("${llm.gateway.model}")
    private String modelName;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 프롬프트를 받아 Gemini API를 호출하고 텍스트 응답을 반환
     *
     * @param prompt AI에게 보낼 지시문(프롬프트)
     * @return Gemini가 응답한 순수 문자열(JSON 텍스트)
     */
    public String callGemini(String prompt) {
        // 1. 요청 URL 조립 (엔드포인트 뒤에 쿼리 파라미터로 API Key 부착)
        String requestUrl = apiUrl + "?key=" + apiKey;

        // 2. GeminiRequest DTO 활용 (모델명은 URL에서 정의하므로 임시 값 세팅)
        GeminiRequest request = new GeminiRequest(modelName, prompt);

        // 3. HTTP Header 설정 (JSON 통신)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);

        try {
            // 4. RestTemplate을 이용한 API 호출 및 응답 매핑
            GeminiResponse response = restTemplate.postForObject(requestUrl, entity, GeminiResponse.class);

            if (response != null) {
                return response.getAnswerText();
            }

            throw new RuntimeException("Gemini 응답이 비어 있습니다.");

        } catch (Exception e) {
            System.err.println("❌ Gemini API 호출 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
