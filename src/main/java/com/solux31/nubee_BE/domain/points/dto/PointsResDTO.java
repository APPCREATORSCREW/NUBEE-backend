package com.solux31.nubee_BE.domain.points.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

public class PointsResDTO {

    @Getter
    @Builder
    public static class PointInfo {
        private int currentPoint;
        private int currentLevel;
    }

    @Getter
    @Builder
    public static class PointResult {
        private int earnedPoint;
        private int currentPoint;
        private boolean leveledUp;
        private int currentLevel;
        private List<NewSkinInfo> newSkins;
    }

    @Getter
    @Builder
    public static class NewSkinInfo {
        private Long skinId;
        private String skinName;
    }
}
