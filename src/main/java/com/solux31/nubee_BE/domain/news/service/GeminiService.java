package com.solux31.nubee_BE.domain.news.service;

import com.solux31.nubee_BE.domain.news.dto.Request.GeminiReqDTO;
import com.solux31.nubee_BE.domain.news.dto.Response.GeminiResDTO;
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
     * 프롬프트를 받아 스노우챗 API Gateway(OpenAI 호환 포맷)를 호출하고 텍스트 응답을 반환
     */
    public String callGemini(String prompt) {
        // 1. 규격에 맞춰 순수 Base URL 엔드포인트 그대로 사용 (뒤에 ?key= 제거)
        String requestUrl = apiUrl;

        // 2. 하드코딩 대신 yml의 모델명(gemini-1.5-flash) 주입하여 요청 객체 생성
        GeminiReqDTO request = new GeminiReqDTO(modelName, prompt);

        // 3. HTTP Header 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 3. OpenAI 스타일의 Bearer 토큰 인증 헤더 주입!
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<GeminiReqDTO> entity = new HttpEntity<>(request, headers);

        try {
            // 4. 게이트웨이 엔드포인트로 POST 요청 송신
            GeminiResDTO response = restTemplate.postForObject(requestUrl, entity, GeminiResDTO.class);

            if (response != null) {
                return response.getAnswerText();
            }
            throw new RuntimeException("API Gateway 응답이 비어 있습니다.");

        } catch (Exception e) {
            System.err.println("❌ API Gateway 호출 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
