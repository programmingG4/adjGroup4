package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.entity.*;
import kr.ac.dankook.campuson.repository.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class AdminController {

    private final MemberRepository memberRepository;
    private final ReportRepository reportRepository;
    private final BoardRepository boardRepository;
    private final TalkBoardRepository talkBoardRepository;
    private final CommentRepository commentRepository;
    private final TalkCommentRepository talkCommentRepository;

    public AdminController(MemberRepository memberRepository,
                           ReportRepository reportRepository,
                           BoardRepository boardRepository,
                           TalkBoardRepository talkBoardRepository,
                           CommentRepository commentRepository,
                           TalkCommentRepository talkCommentRepository) {
        this.memberRepository = memberRepository;
        this.reportRepository = reportRepository;
        this.boardRepository = boardRepository;
        this.talkBoardRepository = talkBoardRepository;
        this.commentRepository = commentRepository;
        this.talkCommentRepository = talkCommentRepository;
    }

    // ── 관리자 대시보드 ─────────────────────────────────────────
    @GetMapping("/admin")
    public String adminDashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Member me = memberRepository.findByStudentId(userDetails.getUsername());
        model.addAttribute("member", me);
        model.addAttribute("members", memberRepository.findAll());

        List<Report> allReports = reportRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("reports", allReports);

        // 동일 대상 신고 묶기: targetType+targetId 기준, 신고 건수 많은 순 정렬
        List<List<Report>> reportGroups = allReports.stream()
            .collect(Collectors.groupingBy(
                r -> r.getTargetType() + "___" + r.getTargetId(),
                LinkedHashMap::new,
                Collectors.toList()
            ))
            .values().stream()
            .sorted((a, b) -> b.size() - a.size())
            .collect(Collectors.toList());
        model.addAttribute("reportGroups", reportGroups);

        return "admin/index";
    }

    // ── 회원 차단/해제 ─────────────────────────────────────────
    @PostMapping("/admin/member/{id}/block")
    public String toggleBlock(@PathVariable Long id,
                              @RequestParam(defaultValue = "reports") String redirectTab) {
        memberRepository.findById(id).ifPresent(m -> {
            if (!m.isAdmin()) {
                m.setBlocked(!m.isBlocked());
                memberRepository.save(m);
            }
        });
        return "redirect:/admin?tab=" + redirectTab;
    }

    // ── 회원 삭제 ─────────────────────────────────────────────
    @PostMapping("/admin/member/{id}/delete")
    public String deleteMember(@PathVariable Long id) {
        memberRepository.deleteById(id);
        return "redirect:/admin";
    }

    // ── 게시글 강제 삭제 ───────────────────────────────────────
    @PostMapping("/admin/board/{id}/delete")
    public String deleteBoard(@PathVariable Long id) {
        boardRepository.deleteById(id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/talkboard/{id}/delete")
    public String deleteTalkBoard(@PathVariable Long id) {
        talkBoardRepository.deleteById(id);
        return "redirect:/admin";
    }

    // ── 댓글 강제 삭제 ────────────────────────────────────────
    @PostMapping("/admin/comment/{id}/delete")
    public String deleteComment(@PathVariable Long id) {
        commentRepository.deleteById(id);
        return "redirect:/admin";
    }

    @PostMapping("/admin/talkcomment/{id}/delete")
    public String deleteTalkComment(@PathVariable Long id) {
        talkCommentRepository.deleteById(id);
        return "redirect:/admin";
    }

    // ── 그룹 전체 해결 ────────────────────────────────────────
    @PostMapping("/admin/report/resolve-group")
    public String resolveGroup(@RequestParam String targetType, @RequestParam Long targetId) {
        List<Report> group = reportRepository.findByTargetTypeAndTargetId(targetType, targetId);
        group.forEach(r -> r.setResolved(true));
        reportRepository.saveAll(group);
        return "redirect:/admin";
    }

    // ── 해결 → 미처리로 되돌리기 (삭제처리된 건 제외) ─────────
    @PostMapping("/admin/report/unresolve-group")
    public String unresolveGroup(@RequestParam String targetType, @RequestParam Long targetId) {
        List<Report> group = reportRepository.findByTargetTypeAndTargetId(targetType, targetId);
        group.stream()
             .filter(r -> !r.isContentDeleted())
             .forEach(r -> r.setResolved(false));
        reportRepository.saveAll(group);
        return "redirect:/admin";
    }

    // ── 그룹 전체 삭제처리 ────────────────────────────────────
    @PostMapping("/admin/report/delete-content-group")
    public String deleteContentGroup(@RequestParam String targetType, @RequestParam Long targetId) {
        switch (targetType) {
            case "BOARD"      -> boardRepository.deleteById(targetId);
            case "TALKBOARD"  -> talkBoardRepository.deleteById(targetId);
            case "COMMENT"    -> commentRepository.deleteById(targetId);
            case "TALKCOMMENT"-> talkCommentRepository.deleteById(targetId);
        }
        List<Report> group = reportRepository.findByTargetTypeAndTargetId(targetType, targetId);
        group.forEach(r -> { r.setResolved(true); r.setContentDeleted(true); });
        reportRepository.saveAll(group);
        return "redirect:/admin";
    }

    // ── 신고 처리 (해결 표시) ──────────────────────────────────
    @PostMapping("/admin/report/{id}/resolve")
    public String resolveReport(@PathVariable Long id) {
        reportRepository.findById(id).ifPresent(r -> {
            r.setResolved(true);
            reportRepository.save(r);
        });
        return "redirect:/admin";
    }

    // ── 신고 + 대상 콘텐츠 삭제 ───────────────────────────────
    @PostMapping("/admin/report/{id}/delete-content")
    public String deleteReportedContent(@PathVariable Long id) {
        reportRepository.findById(id).ifPresent(r -> {
            switch (r.getTargetType()) {
                case "BOARD" -> boardRepository.deleteById(r.getTargetId());
                case "TALKBOARD" -> talkBoardRepository.deleteById(r.getTargetId());
                case "COMMENT" -> commentRepository.deleteById(r.getTargetId());
                case "TALKCOMMENT" -> talkCommentRepository.deleteById(r.getTargetId());
            }
            r.setResolved(true);
            reportRepository.save(r);
        });
        return "redirect:/admin";
    }

    // ── 계정 정지 (기간) ──────────────────────────────────────
    @PostMapping("/admin/member/{id}/suspend")
    public String suspendMember(@PathVariable Long id, @RequestParam int days) {
        memberRepository.findById(id).ifPresent(m -> {
            if (!m.isAdmin()) {
                m.setBlockedUntil(LocalDateTime.now().plusDays(days));
                memberRepository.save(m);
            }
        });
        return "redirect:/admin";
    }

    // ── 계정 정지 해제 ────────────────────────────────────────
    @PostMapping("/admin/member/{id}/unsuspend")
    public String unsuspendMember(@PathVariable Long id) {
        memberRepository.findById(id).ifPresent(m -> {
            m.setBlockedUntil(null);
            memberRepository.save(m);
        });
        return "redirect:/admin?tab=members";
    }

    // ── 신고 접수 엔드포인트 ──────────────────────────────────
    @PostMapping("/report/board/{id}")
    public String reportBoard(@PathVariable Long id,
                              @RequestParam String reason,
                              @AuthenticationPrincipal UserDetails userDetails) {
        Member me = memberRepository.findByStudentId(userDetails.getUsername());
        boardRepository.findById(id).ifPresent(post -> {
            Report r = new Report();
            r.setTargetType("BOARD");
            r.setTargetId(id);
            r.setTargetSummary(post.getTitle());
            r.setTargetCategory(post.getCategory());
            r.setTargetContent(post.getContent());
            r.setAuthorName(post.getAuthor() != null ? post.getAuthor() : "익명");
            r.setAuthorMemberId(post.getMemberId());
            r.setReporterName(me.getName());
            r.setReporterId(me.getId());
            r.setReason(reason);
            reportRepository.save(r);
        });
        return "redirect:/board/" + id + "?reported=true";
    }

    @PostMapping("/report/talkboard/{id}")
    public String reportTalkBoard(@PathVariable Long id,
                                  @RequestParam String reason,
                                  @AuthenticationPrincipal UserDetails userDetails) {
        Member me = memberRepository.findByStudentId(userDetails.getUsername());
        talkBoardRepository.findById(id).ifPresent(post -> {
            Report r = new Report();
            r.setTargetType("TALKBOARD");
            r.setTargetId(id);
            r.setTargetSummary(post.getTitle());
            r.setTargetContent(post.getContent());
            r.setAuthorName(post.getAuthor() != null ? post.getAuthor() : "알 수 없음");
            r.setAuthorMemberId(post.getMemberId());
            r.setReporterName(me.getName());
            r.setReporterId(me.getId());
            r.setReason(reason);
            reportRepository.save(r);
        });
        return "redirect:/talkboard/" + id + "?reported=true";
    }

    @PostMapping("/report/comment/{id}")
    public String reportComment(@PathVariable Long id,
                                @RequestParam String reason,
                                @RequestParam Long boardId,
                                @AuthenticationPrincipal UserDetails userDetails) {
        Member me = memberRepository.findByStudentId(userDetails.getUsername());
        commentRepository.findById(id).ifPresent(c -> {
            Report r = new Report();
            r.setTargetType("COMMENT");
            r.setTargetId(id);
            r.setTargetSummary(c.getContent().length() > 60 ? c.getContent().substring(0, 60) + "..." : c.getContent());
            r.setTargetContent(c.getContent());
            r.setAuthorName(c.getAuthor() != null ? c.getAuthor() : "익명");
            r.setAuthorMemberId(c.getMemberId());
            r.setReporterName(me.getName());
            r.setReporterId(me.getId());
            r.setReason(reason);
            reportRepository.save(r);
        });
        return "redirect:/board/" + boardId + "?reported=true";
    }

    @PostMapping("/report/talkcomment/{id}")
    public String reportTalkComment(@PathVariable Long id,
                                    @RequestParam String reason,
                                    @RequestParam Long postId,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        Member me = memberRepository.findByStudentId(userDetails.getUsername());
        talkCommentRepository.findById(id).ifPresent(c -> {
            Report r = new Report();
            r.setTargetType("TALKCOMMENT");
            r.setTargetId(id);
            r.setTargetSummary(c.getContent().length() > 60 ? c.getContent().substring(0, 60) + "..." : c.getContent());
            r.setTargetContent(c.getContent());
            r.setAuthorName(c.getAuthor() != null ? c.getAuthor() : "알 수 없음");
            r.setAuthorMemberId(c.getMemberId());
            r.setReporterName(me.getName());
            r.setReporterId(me.getId());
            r.setReason(reason);
            reportRepository.save(r);
        });
        return "redirect:/talkboard/" + postId + "?reported=true";
    }
}
