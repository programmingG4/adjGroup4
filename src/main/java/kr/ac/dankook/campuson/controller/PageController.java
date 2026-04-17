package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.repository.MemberRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.security.Principal;

@Controller
public class PageController {

    private final MemberRepository memberRepository;

    public PageController(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("activeMenu", "chat");
        return "chat/index";
    }

    @GetMapping("/mypage")
    public String mypage(Model model, Principal principal) {
        String studentId = principal.getName();
        Member member = memberRepository.findByStudentId(studentId);
        model.addAttribute("member", member);
        model.addAttribute("activeMenu", "mypage");
        return "mypage";
    }
}
