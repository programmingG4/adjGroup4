package kr.ac.dankook.campuson.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/chat")
    public String chat() {
        return "chat/index";
    }

    @GetMapping("/articles")
    public String articles() {
        return "article/list";
    }

    @GetMapping("/mypage")
    public String mypage() {
        return "my/index";
    }
}
