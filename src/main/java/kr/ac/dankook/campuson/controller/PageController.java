package kr.ac.dankook.campuson.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("activeMenu", "chat");
        return "chat/index";
    }

    @GetMapping("/articles")
    public String articles(Model model) {
        model.addAttribute("activeMenu", "articles");
        return "article/list";
    }

    @GetMapping("/mypage")
    public String mypage(Model model) {
        model.addAttribute("activeMenu", "mypage");
        return "my/index";
    }
}
