package kr.ac.dankook.campuson.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        // TODO: 개인정보는 추후 로그인 사용자 기준으로 연동 예정
        // TODO: 최근 채팅방 및 기사 데이터는 추후 DB/크롤링 기반으로 대체 예정
        model.addAttribute("activeMenu", "home");
        return "home/index";
    }
}
