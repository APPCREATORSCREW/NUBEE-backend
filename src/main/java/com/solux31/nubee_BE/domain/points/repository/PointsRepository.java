package com.solux31.nubee_BE.domain.points.repository;

import com.solux31.nubee_BE.domain.points.entity.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointsRepository extends JpaRepository<PointHistory, Long> {
}
