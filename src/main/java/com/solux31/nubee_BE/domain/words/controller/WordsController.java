package com.solux31.nubee_BE.domain.words.controller;

import com.solux31.nubee_BE.domain.news.dto.Response.QuizResDTO;
import com.solux31.nubee_BE.domain.news.dto.Request.QuizSubmitReqDTO;
import com.solux31.nubee_BE.domain.news.dto.Response.QuizSubmitResDTO;
import com.solux31.nubee_BE.domain.news.dto.Response.TodayNewsResDTO;
import com.solux31.nubee_BE.domain.news.service.NewsService;
import com.solux31.nubee_BE.domain.words.dto.Response.KeywordDetailResDTO;
import com.solux31.nubee_BE.domain.words.exception.code.WordsSuccessCode;
import com.solux31.nubee_BE.domain.words.service.WordService;
import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import com.solux31.nubee_BE.global.security.entity.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "Keyword API", description = "단어/키워드 상세 정보 및 퀴즈 관련 API")
@RestController
@RequestMapping("/api/keywords")
@RequiredArgsConstructor
public class WordsController {

    private final WordService wordService;
    private final NewsService newsService;

    /**
     * 오늘의 맞춤 키워드 및 뉴스 리스트 조회
     * GET /api/words
     */
    @Operation(summary = "오늘의 맞춤 키워드 및 뉴스 리스트 조회",
            description = "유저 설정에 맞춰 카테고리 균형을 잡은 단어 카드와 연관 뉴스 리스트를 반환함.")
    @GetMapping
    public ApiResponse<TodayNewsResDTO> getTodayKeywordsAndNews(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        TodayNewsResDTO responseData = newsService.getBalancedTodayNewsForUser(authUser.getUserId());
        return ApiResponse.onSuccess(WordsSuccessCode.TODAY_KEYWORD_LIST_GET_SUCCESS, responseData);
    }

    /**
     * 1번 특정 키워드 설명 조회
     * GET /api/words/{keyword_id}
     */
    @Operation(summary = "선택한 키워드의 설명 조회",
            description = "키워드 ID를 통해 해당 단어의 이름, 초등 눈높이 설명, 예문, 타입을 반환함.")
    @GetMapping("/{keyword_id}")
    public ApiResponse<KeywordDetailResDTO> getKeywordDetail(
            @PathVariable("keyword_id") Long keywordId
    ) {
        KeywordDetailResDTO responseData = wordService.getKeywordDetail(keywordId);
        return ApiResponse.onSuccess(WordsSuccessCode.KEYWORD_DETAIL_GET_SUCCESS, responseData);
    }

    /**
     * 2번 특정 키워드의 복습 퀴즈 조회
     * GET /api/words/{keyword_id}/quiz
     */
    @Operation(summary = "특정 키워드의 퀴즈 조회",
            description = "키워드 ID를 통해 해당 단어와 연동된 KEYWORD 타입 퀴즈 1문제를 조회함.")
    @GetMapping("/{keyword_id}/quiz")
    public ApiResponse<QuizResDTO> getKeywordQuiz(
            @PathVariable("keyword_id") Long keywordId
    ) {
        QuizResDTO responseData = newsService.getKeywordQuizByKeywordId(keywordId);
        return ApiResponse.onSuccess(WordsSuccessCode.KEYWORD_QUIZ_GET_SUCCESS, responseData);
    }

    /**
     * 3번 키워드 퀴즈 채점 및 포인트 지급
     * POST /api/words/{keyword_id}/quiz/submit
     */
    @Operation(summary = "키워드 퀴즈 채점 및 포인트 지급",
            description = "유저가 제출한 퀴즈 답안을 채점하여 정답 여부와 해설을 반환하고, 정답일 경우 1포인트를 지급함.")
    @PostMapping("/{keyword_id}/quiz/submit")
    public ApiResponse<QuizSubmitResDTO> submitKeywordQuiz(
            @PathVariable("keyword_id") Long keywordId,
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody QuizSubmitReqDTO request
    ) {
        QuizSubmitResDTO responseData = newsService.submitAndGradeKeywordQuiz(authUser.getUserId(), keywordId, request);
        return ApiResponse.onSuccess(WordsSuccessCode.KEYWORD_QUIZ_GRADE_SUCCESS, responseData);
    }
}