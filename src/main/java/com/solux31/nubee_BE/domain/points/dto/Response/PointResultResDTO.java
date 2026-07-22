package com.solux31.nubee_BE.domain.points.dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PointResultResDTO {
    private int earnedPoint;
    private int currentPoint;
    private boolean leveledUp;
    private int currentLevel;
    private List<NewSkinInfoResDTO> newSkins;
}
