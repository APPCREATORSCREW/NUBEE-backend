package com.solux31.nubee_BE.domain.points.controller;

import com.solux31.nubee_BE.domain.points.dto.Response.PointInfoResDTO;
import com.solux31.nubee_BE.domain.points.exception.code.PointsSuccessCode;
import com.solux31.nubee_BE.domain.points.service.PointsService;
import com.solux31.nubee_BE.global.apiPayload.ApiResponse;
import com.solux31.nubee_BE.global.security.entity.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "포인트 API", description = "포인트 관련 API")
@SecurityRequirement(name = "JWT TOKEN")
@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @Operation(summary = "포인트 조회", description = "로그인한 유저의 현재 포인트와 레벨을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PointInfoResDTO>> getPoints(
            @AuthenticationPrincipal AuthUser authUser
    ) {
        PointInfoResDTO result = pointsService.getPoints(authUser.getUserId());
        return ResponseEntity.ok(
                ApiResponse.onSuccess(PointsSuccessCode.POINT_FETCH_SUCCESS, result)
        );
    }
}