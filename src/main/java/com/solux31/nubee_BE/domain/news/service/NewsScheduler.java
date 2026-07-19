package com.solux31.nubee_BE.domain.news.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NewsScheduler {

    private final NewsService newsService;

    /**
     * 매일 새벽 4시에 자동으로 뉴스 수집 및 퀴즈 생성 워크플로우를 실행합니다.
     * 크론 표현식: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul") // 한국 시간 기준 새벽 4시 정각
    public void runDailyNewsWorkflow() {
        log.info("=== 새벽 뉴스 배치 스케줄러 시작 ===");
        try {
            newsService.executeDailyNewsWorkflow();
            log.info("=== 새벽 뉴스 배치 스케줄러 정상 종료 ===");
        } catch (Exception e) {
            log.error("=== 배치 스케줄러 실행 중 에러 발생! ===", e);
            // 필요하다면 이곳에 슬랙 알림이나 메일 발송 로직을 연동 가능
        }
    }
}
