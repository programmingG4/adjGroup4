package kr.ac.dankook.campuson.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {

        // TODO: 배너 및 기사 데이터는 크롤링 기반으로 추후 구현 예정

        return "home/index";
    }
}
