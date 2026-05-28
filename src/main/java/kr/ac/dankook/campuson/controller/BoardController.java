package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.entity.Board;
import kr.ac.dankook.campuson.entity.ChatRoom;
import kr.ac.dankook.campuson.entity.VoteItem;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.BoardService;
import kr.ac.dankook.campuson.service.ChatService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
public class BoardController {

    @Autowired
    private BoardService boardService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChatService chatService;

    private String getChatRoomName() {
        LocalDate now = LocalDate.now();
        String semester = (now.getMonthValue() >= 3 && now.getMonthValue() <= 8) ? "1학기" : "2학기";
        return String.format("[%d-%s] DKU 컴퓨터공학과 통합게시판", now.getYear(), semester);
    }

    // 로그인 유저 -> Member 가져오기
    private Member getLoginMember(UserDetails userDetails) {
        if (userDetails == null)
            return null;
        return memberRepository.findByStudentId(userDetails.getUsername());
    }

    @GetMapping("/board")
    public String boardList(@RequestParam(defaultValue = "익명 게시판") String category,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        model.addAttribute("selectedCategory", category);
        model.addAttribute("categories", List.of("익명 게시판", "중고거래 게시판", "📢 모집중"));

        model.addAttribute("posts", boardService.getPostsByCategory(category));

        Member loginMember = getLoginMember(userDetails);
        model.addAttribute("loginMemberId", loginMember != null ? loginMember.getId() : null);
        return "board/list";
    }

    @GetMapping("/board/write")
    public String writeForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        model.addAttribute("categories", List.of("익명 게시판", "중고거래 게시판", "📢 모집중"));

        // 로그인 유저 이름_학번 형식으로 전달
        Member loginMember = getLoginMember(userDetails);
        if (loginMember != null) {
            // 학번 3,4번째 숫자 추출 (index 2,3)
            String year = loginMember.getStudentId().substring(2, 4);
            String memberName = loginMember.getName() + "_" + year; // 예: OOO_24
            model.addAttribute("loginMemberName", memberName);
        } else {
            model.addAttribute("loginMemberName", "");
        }
        return "board/write";
    }

    @PostMapping("/board/save")
    public String save(@ModelAttribute Board board,
            @RequestParam(value = "voteOption", required = false) List<String> voteItems,
            @RequestParam(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "voteDeadline", required = false) String voteDeadline,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        Member loginMember = getLoginMember(userDetails);

        // 익명 처리
        if (board.getAuthor() == null || board.getAuthor().isBlank()) {
            board.setAuthor("익명");
            board.setMemberId(loginMember != null ? loginMember.getId() : null);
        } else {
            if (loginMember != null)
                board.setMemberId(loginMember.getId());
        }

        // 이미지 여러장 업로드
        if (images != null && !images.isEmpty()) {
            String uploadDir = System.getProperty("user.dir") + "/uploads/";
            new File(uploadDir).mkdirs();
            List<String> paths = new ArrayList<>();
            for (MultipartFile image : images) {
                if (image != null && !image.isEmpty()) {
                    String filename = UUID.randomUUID() + "_" + image.getOriginalFilename();
                    image.transferTo(new File(uploadDir + filename));
                    paths.add("/uploads/" + filename);
                }
            }
            if (!paths.isEmpty())
                board.setImagePaths(paths);
        }

        // 투표 종료 시간 저장
        if (voteDeadline != null && !voteDeadline.isBlank()) {
            try {
                board.setVoteEndTime(LocalDateTime.parse(voteDeadline,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
            } catch (Exception ignored) {}
        }

        boardService.save(board);

        // 투표 항목 저장
        if (voteItems != null && !voteItems.isEmpty()) {
            List<String> validItems = voteItems.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .toList();
            if (!validItems.isEmpty()) {
                boardService.saveVoteItems(board, validItems);
            }
        }

        return "redirect:/board";
    }

    @GetMapping("/board/{id}")
    public String detail(@PathVariable Long id,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        Board post = boardService.findById(id);
        int totalVotes = post.getVoteItems() == null ? 0
                : post.getVoteItems().stream().mapToInt(VoteItem::getVoteCount).sum();

        Member loginMember = getLoginMember(userDetails);
        Long loginMemberId = loginMember != null ? loginMember.getId() : null;
        if (loginMember != null) {
            String year = loginMember.getStudentId().substring(2, 4);
            model.addAttribute("loginMemberName", loginMember.getName() + "_" + year);
        } else {
            model.addAttribute("loginMemberName", "익명");
        }

        boolean voteEnded = post.getVoteEndTime() != null && post.getVoteEndTime().isBefore(LocalDateTime.now());

        model.addAttribute("post", post);
        model.addAttribute("totalVotes", totalVotes);

        model.addAttribute("loginMemberId", loginMemberId);
        model.addAttribute("fromCategory", category != null ? category : post.getCategory());
        model.addAttribute("voteEnded", voteEnded);

        return "board/detail";
    }

    // 게시글 삭제
    @PostMapping("/board/{id}/delete")
    public String delete(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember != null)
            boardService.delete(id, loginMember.getId());
        return "redirect:/board";
    }

    // 수정 페이지
    @GetMapping("/board/{id}/edit")
    public String editForm(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        Board post = boardService.findById(id);
        Member loginMember = getLoginMember(userDetails);

        // 본인 아니면 목록으로
        if (loginMember == null || !post.getMemberId().equals(loginMember.getId())) {
            return "redirect:/board";
        }
        model.addAttribute("post", post);
        model.addAttribute("categories", List.of("익명 게시판", "중고거래 게시판", "📢 모집중"));
        return "board/edit";
    }

    // 수정 저장
    @PostMapping("/board/{id}/edit")
    public String edit(@PathVariable Long id,
            @RequestParam String title,
            @RequestParam String content,
            @RequestParam String category,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember == null)
            return "redirect:/login";

        Board post = boardService.findById(id);
        if (!post.getMemberId().equals(loginMember.getId()))
            return "redirect:/board";

        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);
        boardService.save(post);
        return "redirect:/board/" + id;
    }

    // 댓글 저장
    @PostMapping("/board/{id}/comment")
    public String saveComment(@PathVariable Long id,
            @RequestParam String content,
            @RequestParam String author,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        Long memberId = loginMember != null ? loginMember.getId() : null;
        boardService.saveComment(id, content, author, memberId);
        return "redirect:/board/" + id;
    }

    // 댓글 삭제
    @PostMapping("/comment/{commentId}/delete")
    public String deleteComment(@PathVariable Long commentId,
            @RequestParam Long boardId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember != null)
            boardService.deleteComment(commentId, loginMember.getId());
        return "redirect:/board/" + boardId;
    }

    // 투표
    @PostMapping("/vote/{voteItemId}")
    public String vote(@PathVariable Long voteItemId,
            @RequestParam Long boardId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember == null)
            return "redirect:/login";

        boardService.vote(voteItemId, loginMember.getId());
        return "redirect:/board/" + boardId;
    }

    // 좋아요
    @PostMapping("/board/{id}/like")
    public String like(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember != null)
            boardService.toggleLike(id, loginMember.getId());
        return "redirect:/board/" + id;
    }

    // 검색
    @GetMapping("/board/search")
    public String search(@RequestParam String keyword,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        model.addAttribute("posts", boardService.search(keyword));
        model.addAttribute("keyword", keyword);
        model.addAttribute("categories", List.of("익명 게시판", "중고거래 게시판", "📢 모집중"));
        model.addAttribute("selectedCategory", "");

        Member loginMember = getLoginMember(userDetails);
        model.addAttribute("loginMemberId", loginMember != null ? loginMember.getId() : null);
        return "board/list";
    }
    
    @GetMapping("/board/{id}/chat")
    public String startChatWithSeller(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember == null)
            return "redirect:/login";

        Board post = boardService.findById(id);
        Member seller = memberRepository.findById(post.getMemberId()).orElse(null);
        if (seller == null)
            return "redirect:/board/" + id;

        if (seller.getId().equals(loginMember.getId()))
            return "redirect:/board/" + id;

        ChatRoom room = chatService.getOrCreateTradeRoom(
                loginMember.getName(), loginMember.getStudentId(),
                seller.getName(), seller.getStudentId());

        return "redirect:/chat/" + room.getId();
    }
    
    @GetMapping("/board/{id}/join")
    public String joinGroupChat(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember == null)
            return "redirect:/login";

        Board post = boardService.findById(id);

        // 채팅방 없으면 새로 생성
        String roomKey = "board_" + id;
        ChatRoom room = chatService.getOrCreateGroupRoom(
                "[모집] " + post.getTitle(), roomKey);

        // board에 chatRoomId 저장
        if (post.getChatRoomId() == null) {
            post.setChatRoomId(room.getId());
            boardService.save(post);
        }

        return "redirect:/chat/" + room.getId();
    }
}