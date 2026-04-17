package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.MemberService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class AuthController {

    private final MemberService memberService;
    private final MemberRepository memberRepository;

    public AuthController(MemberService memberService, MemberRepository memberRepository) {
        this.memberService = memberService;
        this.memberRepository = memberRepository;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String registerPost(
        @RequestParam String name,
        @RequestParam String studentId,
        @RequestParam String password,
        @RequestParam MultipartFile image,
        Model model) {
        
        try {
            memberService.register(name, studentId, password, image);
            return "redirect:/login";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        if ("notfound".equals(error)) {
            model.addAttribute("errorMsg", "존재하지 않는 학번입니다.");
        } else if ("wrongpw".equals(error)) {
            model.addAttribute("errorMsg", "비밀번호가 올바르지 않습니다.");
        }
        return "login";
    }

    @GetMapping("/home")
    public String home() {
        return "home";
    }

    @GetMapping("/mypage")
    public String profile(Model model, Authentication authentication) {
        String studentId = authentication.getName();
        Member member = memberRepository.findByStudentId(studentId);
        model.addAttribute("member", member);
        return "mypage";
    }
}