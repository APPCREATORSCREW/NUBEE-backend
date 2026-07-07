package com.solux31.nubee_BE.domain.news.repository;

import com.solux31.nubee_BE.domain.news.entity.DailyNews;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DailyNewsRepository extends JpaRepository<DailyNews, Long> {
    // JpaRepository를 상속 -> save(), findById() 등 사용 가능
}
