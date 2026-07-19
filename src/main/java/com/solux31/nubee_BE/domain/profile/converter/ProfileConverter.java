package com.solux31.nubee_BE.domain.profile.converter;

import com.solux31.nubee_BE.domain.profile.dto.ProfileResDTO;
import com.solux31.nubee_BE.domain.profile.entity.Skin;
import com.solux31.nubee_BE.domain.profile.entity.UserSkin;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ProfileConverter {
    public ProfileResDTO.SkinInfo toSkinInfo(Skin skin, boolean isOwned) {
        return ProfileResDTO.SkinInfo.builder()
                .skinId(skin.getId())
                .skinName(skin.getSkinName())
                .imageUrl((skin.getImageUrl()))
                .isOwned(isOwned)
                .build();
    }

    public List<ProfileResDTO.SkinInfo> toSkinInfoList(List<Skin> allSkins, List<UserSkin> ownedSkins) {
        Set<Long> ownedSkinIds = ownedSkins.stream()
                .map(us -> us.getSkin().getId())
                .collect(Collectors.toSet());

        return allSkins.stream()
                .map(skin -> toSkinInfo(skin, ownedSkinIds.contains(skin.getId())))
                .toList();
    }
}
