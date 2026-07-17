package com.solux31.nubee_BE.domain.profile.repository;

import com.solux31.nubee_BE.domain.profile.entity.UserSkin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserSkinRepository extends JpaRepository<UserSkin, Long> {
    @Query("SELECT us FROM UserSkin us JOIN FETCH us.skin WHERE us.user.id = :userId")
    List<UserSkin> findAllByUserId(@Param("userId") Long userId);
}

