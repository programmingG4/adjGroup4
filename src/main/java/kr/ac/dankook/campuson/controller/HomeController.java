package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.repository.MemberRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class HomeController {

    private final MemberRepository memberRepository;

    public HomeController(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @GetMapping("/home")
    public String home(Model model, Principal principal) {
        if (principal != null) {
            Member member = memberRepository.findByStudentId(principal.getName());
            model.addAttribute("member", member);
        }
        model.addAttribute("activeMenu", "home");
        return "home/index";
    }
}
