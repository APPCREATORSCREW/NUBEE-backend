package com.solux31.nubee_BE.domain.words.controller;

import com.solux31.nubee_BE.domain.words.dto.KeywordDetailResponse;
import com.solux31.nubee_BE.domain.words.service.WordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Keyword API", description = "단어/키워드 상세 정보 관련 API")
@RestController
@RequestMapping("/api/v1/keywords")
@RequiredArgsConstructor
public class WordsController {

    private final WordService wordService;

    /**
     * [명세서 반영] 특정 키워드 설명 조회
     * GET /api/v1/keywords/{keyword_id}
     */
    @Operation(summary = "선택한 키워드의 설명 조회",
            description = "키워드 ID를 통해 해당 단어의 이름, 초등 눈높이 설명, 예문, 타입을 반환합니다.")
    @GetMapping("/{keyword_id}")
    public ResponseEntity<?> getKeywordDetail(
            @RequestHeader("Authorization") String token,
            @PathVariable("keyword_id") Long keywordId // 👈 Path Variable로 ID 접수
    ) {
        // 400 에러 방어: ID가 null이거나 유효하지 않은 값 체크
        if (keywordId == null || keywordId <= 0) {
            return ResponseEntity.badRequest().body("유효하지 않은 키워드 ID 입력");
        }

        try {
            // Service를 호출해서 키워드 상세 조회 DTO 가져오기
            KeywordDetailResponse responseData = wordService.getKeywordDetail(keywordId);

            // 명세서 양식대로 공통 결과 포장 (SUCCESS)
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("message", "선택한 메인 키워드의 상세 정보 조회가 완료되었습니다.");
            result.put("data", responseData);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            // 404 에러 처리: DB에 해당 ID의 키워드가 없을 때 서비스에서 던진 예외 처리
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
