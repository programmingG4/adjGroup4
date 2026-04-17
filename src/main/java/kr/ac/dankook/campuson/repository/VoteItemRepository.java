package kr.ac.dankook.campuson.repository;

import kr.ac.dankook.campuson.entity.VoteItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoteItemRepository extends JpaRepository<VoteItem, Long> {
}