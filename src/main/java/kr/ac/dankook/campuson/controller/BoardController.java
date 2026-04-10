package kr.ac.dankook.campuson.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@Controller
public class BoardController {

    @GetMapping("/board")
    public String boardList(@RequestParam(value = "category", defaultValue = "전체") String category, Model model) {
        // 카테고리 목록 정의
        List<String> categories = List.of("전체", "공지", "투표", "스터디 모집", "중고거래");

        // 채팅방 이름 설정 (예시: 2026-1 FLY 공지방)
        String chatRoomName = "2026-1 단국대 컴퓨터공학과 채팅방";
        
        // 선택된 카테고리 전달
        model.addAttribute("selectedCategory", category);
        model.addAttribute("categories", categories);
        model.addAttribute("chatRoomName", chatRoomName);
        
        // (추후 구현) DB에서 해당 카테고리 글만 가져오는 로직이 들어갈 자리입니다.
        
        return "board/list";
    }
    
}