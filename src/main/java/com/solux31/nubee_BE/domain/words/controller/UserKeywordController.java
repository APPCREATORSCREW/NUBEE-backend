package com.solux31.nubee_BE.domain.words.controller;

import com.solux31.nubee_BE.domain.words.dto.Response.WordsResDTO;
import com.solux31.nubee_BE.domain.words.service.UserKeywordService;
import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import com.solux31.nubee_BE.global.apiPayload.code.GeneralSuccessCode;
import com.solux31.nubee_BE.global.security.entity.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
@Tag(name = "UserKeyword API", description = "단어장 API")
public class UserKeywordController {

    private final UserKeywordService userKeywordServiceService;

    // 단어 리스트 조회
    @GetMapping
    @Operation(summary = "단어 리스트 조회", description = "단어장의 단어 리스트를 조회합니다.")
    public ApiResponse<List<WordsResDTO>> getWords(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, userKeywordServiceService.getWords(authUser.getUserId()));
    }

    // 단어 삭제
    @DeleteMapping("/{user_keyword_id}/delete")
    @Operation(summary = "단어 삭제", description = "플래시카드 학습 중 알아요 버튼 시 단어를 삭제합니다.")
    public ApiResponse<String> deleteWord(@PathVariable("user_keyword_id") Long wordId,
                                          @AuthenticationPrincipal AuthUser authUser) {
        userKeywordServiceService.deleteWord(wordId, authUser.getUserId());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "단어가 삭제되었습니다.");
    }

    // 단어장에 추가
    @PostMapping("/{keyword_id}")
    @Operation(summary = "단어장에 추가", description = "뉴스 화면에서 단어를 단어장에 추가합니다.")
    public ApiResponse<String> addWord(@PathVariable("keyword_id") Long keywordId,
                                       @AuthenticationPrincipal AuthUser authUser) {
        userKeywordServiceService.addWord(keywordId, authUser.getUserId());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, "단어장에 추가되었습니다.");
    }
}