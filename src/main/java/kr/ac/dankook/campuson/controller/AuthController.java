package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.service.MemberService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class AuthController {

    private final MemberService memberService;

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
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
        @RequestParam String passwordConfirm,
        @RequestParam int grade,
        @RequestParam MultipartFile image,
        Model model) {

        if (!password.equals(passwordConfirm)) {
            model.addAttribute("error", "비밀번호가 일치하지 않습니다.");
            return "register";
        }

        try {
            memberService.register(name, studentId, password, grade, image);
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

}