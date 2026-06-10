package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.entity.TalkBoard;
import kr.ac.dankook.campuson.entity.VoteItem;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.ChatService;
import kr.ac.dankook.campuson.service.TalkBoardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
public class TalkBoardController {

    @Autowired
    private TalkBoardService talkBoardService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChatService chatService;


    private Member getLoginMember(UserDetails userDetails) {
        if (userDetails == null)
            return null;
        return memberRepository.findByStudentId(userDetails.getUsername());
    }

    // 채팅게시판 목록
    @GetMapping("/talkboard")
    public String list(@RequestParam(defaultValue = "공지") String category,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) Long roomId,
            @RequestParam(defaultValue = "global") String roomKey,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {

        // 학년별 접근 권한 체크
        Member loginMember = getLoginMember(userDetails);
        if (roomKey.startsWith("grade_")) {
            int grade = Integer.parseInt(roomKey.replace("grade_", ""));
            if (loginMember == null || loginMember.getGrade() != grade) {
                return "redirect:/talkboard?roomKey=global";
            }
        }

        model.addAttribute("selectedCategory", category);
        model.addAttribute("categories", List.of("공지", "투표", "사진", "동영상", "파일"));
        model.addAttribute("posts", talkBoardService.getPostsByCategory(category, roomKey));
        model.addAttribute("roomKey", roomKey);

        String chatRoomName = chatService.findByRoomKey(roomKey).map(r -> {
            if (r.getType() == kr.ac.dankook.campuson.entity.ChatRoom.RoomType.PRIVATE && loginMember != null) {
                String[] keys = r.getRoomKey().split("_");
                String otherStudentId = keys[0].equals(loginMember.getStudentId()) ? keys[1] : keys[0];
                Member other = memberRepository.findByStudentId(otherStudentId);
                return other != null ? other.getName() : r.getName();
            }
            return r.getName();
        }).orElse(null);
        model.addAttribute("chatRoomName", chatRoomName);

        if ("chat".equals(from) && roomId != null) {
            model.addAttribute("backUrl", "/chat/" + roomId);
        } else {
            model.addAttribute("backUrl", null);
        }

        Long loginMemberId = loginMember != null ? loginMember.getId() : null;
        model.addAttribute("loginMemberId", loginMemberId);
        if (loginMember != null) {
            String year = loginMember.getStudentId().substring(2, 4);
            model.addAttribute("loginMemberName", loginMember.getName() + " " + year + "학번");
        } else {
            model.addAttribute("loginMemberName", "");
        }
        model.addAttribute("member", loginMember);
        return "talkboard/list";
    }

    // 글쓰기 페이지
    @GetMapping("/talkboard/write")
    public String writeForm(@RequestParam(required = false) String from,
            @RequestParam(required = false) Long roomId,
            @RequestParam(defaultValue = "global") String roomKey,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember != null) {
            String year = loginMember.getStudentId().substring(2, 4);
            model.addAttribute("loginMemberName", loginMember.getName() + " " + year + "학번");
        } else {
            model.addAttribute("loginMemberName", "");
        }
        model.addAttribute("from", from);
        model.addAttribute("roomId", roomId);
        model.addAttribute("roomKey", roomKey);
        if ("chat".equals(from) && roomId != null) {
            model.addAttribute("backUrl", "/chat/" + roomId);
        } else {
            model.addAttribute("backUrl", "/talkboard?roomKey=" + roomKey);
        }
        model.addAttribute("member", loginMember);
        return "talkboard/write";
    }

    // 글 저장
    @PostMapping("/talkboard/save")
    public String save(@ModelAttribute TalkBoard post,
            @RequestParam(value = "pinned", required = false) boolean pinned,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) Long roomId,
            @RequestParam(defaultValue = "global") String roomKey,
            @RequestParam(value = "voteOption", required = false) List<String> voteItems,
            @RequestParam(value = "voteDeadline", required = false) String voteDeadline,
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

        // 투표 종료 시간 저장
        if (voteDeadline != null && !voteDeadline.isBlank()) {
            try {
                post.setVoteEndTime(LocalDateTime.parse(voteDeadline,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")));
            } catch (Exception ignored) {}
        }

        post.setRoomKey(roomKey);
        post.setPinned(pinned);
        talkBoardService.save(post);

        // 해당 채팅방에 알림 전송
        chatService.findByRoomKey(roomKey).ifPresent(room -> {
            String noticeContent = "📣 새 공지가 등록되었습니다.\n"
                    + "「 " + post.getTitle() + " 」"
                    + "\n[LINK]/talkboard/" + post.getId();
            ChatMessage msg = chatService.sendSystemMessage(room.getId(), noticeContent);
            if (msg != null) {
                msg.setSenderName("공지");
                chatService.saveMessage(msg);
            }

            // 상단 고정 체크했으면 채팅방 공지도 업데이트
            if (pinned) {
                chatService.updatePinnedNotice(room.getId(), post.getTitle(), post.getContent(), post.getId());
            }
        });

        // 투표 저장
        if (voteItems != null && !voteItems.isEmpty()) {
            List<String> validItems = voteItems.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .toList();
            if (!validItems.isEmpty())
                talkBoardService.saveVoteItems(post, validItems);
        }

        if ("chat".equals(from) && roomId != null) {
            return "redirect:/chat/" + roomId;
        }
        return "redirect:/talkboard?roomKey=" + roomKey;
    }

    // 상세 페이지
    @GetMapping("/talkboard/{id}")
    public String detail(@PathVariable Long id,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) Long roomId,
            @AuthenticationPrincipal UserDetails userDetails,
            jakarta.servlet.http.HttpServletResponse response,
            Model model) {
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        // 삭제된 글 처리
        java.util.Optional<TalkBoard> postOpt = talkBoardService.findByIdOptional(id);
        if (postOpt.isEmpty()) {
            model.addAttribute("deleted", true);
            if ("chat".equals(from) && roomId != null) {
                model.addAttribute("backUrl", "/chat/" + roomId);
                model.addAttribute("backLabel", "← 채팅방으로 돌아가기");
            } else {
                model.addAttribute("backUrl", "/talkboard");
                model.addAttribute("backLabel", "← 게시판으로 돌아가기");
            }
            return "talkboard/detail";
        }

        TalkBoard post = postOpt.get();
        int totalVotes = post.getVoteItems() == null ? 0
                : post.getVoteItems().stream().mapToInt(VoteItem::getVoteCount).sum();

        Member loginMember = getLoginMember(userDetails);
        Long loginMemberId = loginMember != null ? loginMember.getId() : null;
        boolean voteEnded = post.getVoteEndTime() != null && post.getVoteEndTime().isBefore(LocalDateTime.now());

        model.addAttribute("deleted", false);
        model.addAttribute("post", post);
        model.addAttribute("totalVotes", totalVotes);
        model.addAttribute("loginMemberId", loginMemberId);
        model.addAttribute("fromCategory", category);
        model.addAttribute("voteEnded", voteEnded);

        if (loginMember != null) {
            String year = loginMember.getStudentId().substring(2, 4);
            model.addAttribute("loginMemberName", loginMember.getName() + " " + year + "학번");
        } else {
            model.addAttribute("loginMemberName", "");
        }

        // 채팅방에서 왔으면 채팅방으로 아니면 게시판으로
        if ("chat".equals(from) && roomId != null) {
            model.addAttribute("backUrl", "/chat/" + roomId);
            model.addAttribute("backLabel", "← 채팅방으로 돌아가기");
        } else {
            String roomKey = post.getRoomKey() != null ? post.getRoomKey() : "global";
            model.addAttribute("backUrl",
                    "/talkboard?category=" + (category != null ? category : "공지") + "&roomKey=" + roomKey);
            model.addAttribute("backLabel", "← 게시판으로 돌아가기");
        }

        model.addAttribute("fromCategory", category != null ? category : "공지");
        model.addAttribute("member", loginMember);

        // 이미 투표한 항목 ID
        Long votedItemId = null;
        if (loginMember != null && post.getVoteItems() != null) {
            final Long memberId = loginMember.getId();
            votedItemId = post.getVoteItems().stream()
                    .filter(v -> v.getVotedMemberIds().contains(memberId))
                    .findFirst()
                    .map(v -> v.getId())
                    .orElse(null);
        }
        model.addAttribute("votedItemId", votedItemId);

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
        model.addAttribute("member", loginMember);
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

    // 좋아요
    @PostMapping("/talkboard/{id}/like")
    public String like(@PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember != null)
            talkBoardService.toggleLike(id, loginMember.getId());
        return "redirect:/talkboard/" + id;
    }

    // 상단 고정
    @PostMapping("/talkboard/{id}/pin")
    public String pin(@PathVariable Long id,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal UserDetails userDetails) {
        Member loginMember = getLoginMember(userDetails);
        if (loginMember == null)
            return "redirect:/login";

        TalkBoard post = talkBoardService.findById(id);
        if (!post.getMemberId().equals(loginMember.getId()))
            return "redirect:/talkboard/" + id;

        boolean newPinned = !post.isPinned();
        post.setPinned(newPinned);
        talkBoardService.save(post);

        // 해당 roomKey의 채팅방에 공지 연동
        chatService.findByRoomKey(post.getRoomKey()).ifPresent(room -> {
            if (newPinned) {
                chatService.updatePinnedNotice(room.getId(), post.getTitle(), post.getContent(), post.getId());
            } else {
                if (post.getId().equals(room.getPinnedTalkBoardId())) {
                    chatService.clearPinnedNotice(room.getId(), post.getId());
                }
            }
        });

        return "redirect:/talkboard/" + id;
    }

    // 검색
    @GetMapping("/talkboard/search")
    public String search(@RequestParam String keyword,
            @RequestParam(defaultValue = "global") String roomKey,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model) {
        model.addAttribute("posts", talkBoardService.search(keyword, roomKey));
        model.addAttribute("keyword", keyword);
        model.addAttribute("categories", List.of("공지", "투표", "사진", "동영상", "파일"));
        model.addAttribute("selectedCategory", "");
        model.addAttribute("roomKey", roomKey);
        Member loginMember = getLoginMember(userDetails);
        model.addAttribute("loginMemberId", loginMember != null ? loginMember.getId() : null);
        if (loginMember != null) {
            String year = loginMember.getStudentId().substring(2, 4);
            model.addAttribute("loginMemberName", loginMember.getName() + " " + year + "학번");
        }
        return "talkboard/list";
    }
}