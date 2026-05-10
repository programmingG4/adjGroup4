package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.entity.TalkBoard;
import kr.ac.dankook.campuson.entity.VoteItem;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.TalkBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
public class TalkBoardController {

    @Autowired
    private TalkBoardService talkBoardService;
    @Autowired
    private MemberRepository memberRepository;

    private Member getLoginMember(UserDetails userDetails) {
        if (userDetails == null)
            return null;
        return memberRepository.findByStudentId(userDetails.getUsername());
    }

    // 채팅게시판 목록
    @GetMapping("/talkboard")
    public String list(@RequestParam(defaultValue = "공지") String category,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        model.addAttribute("selectedCategory", category);
        model.addAttribute("categories", List.of("공지", "투표", "사진", "동영상", "파일"));
        model.addAttribute("posts", talkBoardService.getPostsByCategory(category));

        Member loginMember = getLoginMember(userDetails);
        Long loginMemberId = loginMember != null ? loginMember.getId() : null;
        model.addAttribute("loginMemberId", loginMemberId);
        if (loginMember != null) {
            String year = loginMember.getStudentId().substring(2, 4);
            model.addAttribute("loginMemberName", loginMember.getName() + "_" + year);
        } else {
            model.addAttribute("loginMemberName", "");
        }
        return "talkboard/list";
    }

    // 글쓰기 페이지
    @GetMapping("/talkboard/write")
    public String writeForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember != null) {
            String year = loginMember.getStudentId().substring(2, 4);
            model.addAttribute("loginMemberName", loginMember.getName() + "_" + year);
        } else {
            model.addAttribute("loginMemberName", "");
        }
        return "talkboard/write";
    }

    // 글 저장
    @PostMapping("/talkboard/save")
    public String save(@ModelAttribute TalkBoard post,
            @RequestParam(value = "voteOption", required = false) List<String> voteItems,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "videos", required = false) List<MultipartFile> videos,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        Member loginMember = getLoginMember(userDetails);
        if (post.getAuthor() == null || post.getAuthor().isBlank()) {
            post.setAuthor(loginMember != null ? loginMember.getName() : "알 수 없음");
        }
        if (loginMember != null)
            post.setMemberId(loginMember.getId());

        String uploadDir = System.getProperty("user.dir") + "/uploads/";
        new File(uploadDir).mkdirs();

        // 이미지 저장
        if (images != null) {
            List<String> paths = new ArrayList<>();
            for (MultipartFile f : images) {
                if (f != null && !f.isEmpty()) {
                    String filename = UUID.randomUUID() + "_" + f.getOriginalFilename();
                    f.transferTo(new File(uploadDir + filename));
                    paths.add("/uploads/" + filename);
                }
            }
            if (!paths.isEmpty())
                post.setImagePaths(paths);
        }

        // 동영상 저장
        if (videos != null) {
            List<String> paths = new ArrayList<>();
            for (MultipartFile f : videos) {
                if (f != null && !f.isEmpty()) {
                    String filename = UUID.randomUUID() + "_" + f.getOriginalFilename();
                    f.transferTo(new File(uploadDir + filename));
                    paths.add("/uploads/" + filename);
                }
            }
            if (!paths.isEmpty())
                post.setVideoPaths(paths);
        }

        // 파일 저장
        if (files != null) {
            List<String> paths = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) {
                    String filename = UUID.randomUUID() + "_" + f.getOriginalFilename();
                    f.transferTo(new File(uploadDir + filename));
                    paths.add("/uploads/" + filename);
                    names.add(f.getOriginalFilename());
                }
            }
            if (!paths.isEmpty()) {
                post.setFilePaths(paths);
                post.setFileNames(names);
            }
        }

        talkBoardService.save(post);

        // 투표 저장
        if (voteItems != null && !voteItems.isEmpty()) {
            List<String> validItems = voteItems.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .toList();
            if (!validItems.isEmpty())
                talkBoardService.saveVoteItems(post, validItems);
        }

        return "redirect:/talkboard";
    }

    // 상세 페이지
    @GetMapping("/talkboard/{id}")
    public String detail(@PathVariable Long id,
            @RequestParam(defaultValue = "공지") String category,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        TalkBoard post = talkBoardService.findById(id);
        int totalVotes = post.getVoteItems() == null ? 0
                : post.getVoteItems().stream().mapToInt(VoteItem::getVoteCount).sum();

        Member loginMember = getLoginMember(userDetails);
        Long loginMemberId = loginMember != null ? loginMember.getId() : null;

        model.addAttribute("post", post);
        model.addAttribute("totalVotes", totalVotes);
        model.addAttribute("loginMemberId", loginMemberId);
        model.addAttribute("fromCategory", category);

        if (loginMember != null) {
            String year = loginMember.getStudentId().substring(2, 4);
            model.addAttribute("loginMemberName", loginMember.getName() + "_" + year);
        } else {
            model.addAttribute("loginMemberName", "");
        }
        return "talkboard/detail";
    }

    // 수정 페이지
    @GetMapping("/talkboard/{id}/edit")
    public String editForm(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        TalkBoard post = talkBoardService.findById(id);
        Member loginMember = getLoginMember(userDetails);
        if (loginMember == null || !post.getMemberId().equals(loginMember.getId())) {
            return "redirect:/talkboard";
        }
        model.addAttribute("post", post);
        return "talkboard/edit";
    }

    // 수정 저장
    @PostMapping("/talkboard/{id}/edit")
    public String edit(@PathVariable Long id,
            @RequestParam String title,
            @RequestParam String content,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember == null)
            return "redirect:/login";

        TalkBoard post = talkBoardService.findById(id);
        if (!post.getMemberId().equals(loginMember.getId()))
            return "redirect:/talkboard";

        post.setTitle(title);
        post.setContent(content);
        talkBoardService.save(post);
        return "redirect:/talkboard/" + id;
    }

    // 글 삭제
    @PostMapping("/talkboard/{id}/delete")
    public String delete(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember != null)
            talkBoardService.delete(id, loginMember.getId());
        return "redirect:/talkboard";
    }

    // 댓글 저장
    @PostMapping("/talkboard/{id}/comment")
    public String saveComment(@PathVariable Long id,
            @RequestParam String content,
            @RequestParam String author,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        Long memberId = loginMember != null ? loginMember.getId() : null;
        talkBoardService.saveComment(id, content, author, memberId);
        return "redirect:/talkboard/" + id;
    }

    // 댓글 삭제
    @PostMapping("/talkboard/comment/{commentId}/delete")
    public String deleteComment(@PathVariable Long commentId,
            @RequestParam Long postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember != null)
            talkBoardService.deleteComment(commentId, loginMember.getId());
        return "redirect:/talkboard/" + postId;
    }

    // 투표
    @PostMapping("/talkboard/vote/{voteItemId}")
    public String vote(@PathVariable Long voteItemId,
            @RequestParam Long postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember == null)
            return "redirect:/login";
        talkBoardService.vote(voteItemId, loginMember.getId());
        return "redirect:/talkboard/" + postId;
    }
}