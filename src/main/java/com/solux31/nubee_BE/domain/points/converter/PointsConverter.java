package com.solux31.nubee_BE.domain.points.converter;

import com.solux31.nubee_BE.domain.auth.entity.User;
import com.solux31.nubee_BE.domain.profile.entity.Skin;
import com.solux31.nubee_BE.domain.points.dto.PointsResDTO;
import org.springframework.stereotype.Component;

@Component
public class PointsConverter {
    public PointsResDTO.PointInfo toPointInfo(User user) {
        return PointsResDTO.PointInfo.builder()
                .currentPoint(user.getPoint())
                .currentLevel(user.getCurrentLevel())
                .build();
    }

    public PointsResDTO.NewSkinInfo toNewSkinInfo(Skin skin) {
        if (skin == null) return null;
        return PointsResDTO.NewSkinInfo.builder()
                .skinId(skin.getId())
                .skinName(skin.getSkinName())
                .build();
    }
}
