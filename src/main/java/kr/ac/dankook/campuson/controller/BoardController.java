package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.entity.Board;
import kr.ac.dankook.campuson.entity.VoteItem;
import kr.ac.dankook.campuson.repository.BoardRepository;
import kr.ac.dankook.campuson.entity.VoteItem;
import kr.ac.dankook.campuson.repository.BoardRepository;
import kr.ac.dankook.campuson.service.BoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Controller
public class BoardController {

    @Autowired
    private BoardRepository boardRepository;
    
    @Autowired
    private BoardService boardService;
    
    private String getChatRoomName() {
        LocalDate now = LocalDate.now();
        String semester = (now.getMonthValue() >= 3 && now.getMonthValue() <= 8) ? "1학기" : "2학기";
        return String.format("[%d-%s] DKU 컴퓨터공학과 통합게시판", now.getYear(), semester);
    }
    
    @GetMapping("/board")
    public String boardList(@RequestParam(value = "category", defaultValue = "전체") String category, Model model) {
        List<String> categories = List.of("전체", "공지", "투표", "스터디 모집", "중고거래"); // 카테고리 목록 정의
        List<Board> posts = boardService.getPostsByCategory(category);

        // 날짜 기반 자동 채팅방 이름 생성
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        String semester = (month >= 3 && month <= 8) ? "1학기" : "2학기";

        String autoChatRoomName = String.format("[%d-%s] DKU 컴퓨터공학과 통합게시판", year, semester);

        model.addAttribute("selectedCategory", category);
        model.addAttribute("categories", categories);
        model.addAttribute("posts", posts);
        model.addAttribute("chatRoomName", autoChatRoomName);
        return "board/list";
    }
    
    // 1. 게시글 작성 버튼 클릭 시 이동
    @GetMapping("/board/write")
    public String writeForm(Model model) {
        model.addAttribute("categories", List.of("공지", "투표", "스터디 모집", "중고거래"));
        return "board/write";
    }

    // 2. 게시글 저장 (카테고리 포함)
    @PostMapping("/board/save")
    public String save(@ModelAttribute Board board,
            @RequestParam(value = "voteOption", required = false) List<String> voteItems,
            @RequestParam(required = false) MultipartFile image) throws Exception {

        // 익명 처리: author가 비어있으면 익명
        if (board.getAuthor() == null || board.getAuthor().isBlank()) {
            board.setAuthor("익명");
        }

        // 이미지 업로드 처리
        if (image != null && !image.isEmpty()) {
            String uploadDir = System.getProperty("user.dir") + "/uploads/";
            new File(uploadDir).mkdirs();
            String filename = UUID.randomUUID() + "_" + image.getOriginalFilename();
            image.transferTo(new File(uploadDir + filename));
            board.setImagePath("/uploads/" + filename); // 경로명 변경
        }

        boardRepository.save(board); // 먼저 저장해서 ID 생성
        boardService.save(board);

        // 투표 항목 저장
        if ("투표".equals(board.getCategory()) && voteItems != null) {
            boardService.saveVoteItems(board, voteItems);
        }

        return "redirect:/board";
    }

    // 게시글 상세
    @GetMapping("/board/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Board post = boardService.findById(id);
        int totalVotes = post.getVoteItems() == null ? 0 :
            post.getVoteItems().stream().mapToInt(VoteItem::getVoteCount).sum();
        model.addAttribute("post", post);
        model.addAttribute("totalVotes", totalVotes);
        model.addAttribute("chatRoomName", getChatRoomName());
        return "board/detail";
    }

    // 댓글 저장
    @PostMapping("/board/{id}/comment")
    public String saveComment(@PathVariable Long id,
                              @RequestParam String content,
                              @RequestParam String author) {
        boardService.saveComment(id, content, author);
        return "redirect:/board/" + id;
    }

    // 투표 처리
    @PostMapping("/vote/{voteItemId}")
    public String vote(@PathVariable Long voteItemId,
                       @RequestParam Long boardId) {
        boardService.vote(voteItemId);
        return "redirect:/board/" + boardId;
    }
}