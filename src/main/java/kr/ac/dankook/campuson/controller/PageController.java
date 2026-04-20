package kr.ac.dankook.campuson.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.MemberService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class PageController {

    private final MemberRepository memberRepository;
    private final MemberService memberService;

    public PageController(MemberRepository memberRepository, MemberService memberService) {
        this.memberRepository = memberRepository;
        this.memberService = memberService;
    }

    @GetMapping("/chat")
    public String chat(Model model) {
        model.addAttribute("activeMenu", "chat");
        return "chat/index";
    }

    @GetMapping("/mypage")
    public String mypage(Model model, Principal principal) {
        Member member = memberRepository.findByStudentId(principal.getName());
        model.addAttribute("member", member);
        model.addAttribute("activeMenu", "mypage");
        return "mypage";
    }

    @GetMapping("/change-password")
    public String changePasswordForm() {
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Principal principal, Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "새 비밀번호가 일치하지 않습니다.");
            return "change-password";
        }
        try {
            memberService.changePassword(principal.getName(), currentPassword, newPassword);
            return "redirect:/mypage?pwChanged";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "change-password";
        }
    }

    @PostMapping("/delete-account")
    public String deleteAccount(Principal principal,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        memberService.deleteAccount(principal.getName());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return "redirect:/?withdrawn";
    }
}
