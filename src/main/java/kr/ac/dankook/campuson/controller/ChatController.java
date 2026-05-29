package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.entity.ChatRoom;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.ChatService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.File;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;
    private final MemberRepository memberRepository;

    public ChatController(ChatService chatService, MemberRepository memberRepository) {
        this.chatService = chatService;
        this.memberRepository = memberRepository;
    }

    @GetMapping
    public String chatHome(Model model, Principal principal) {
        Member member = memberRepository.findByStudentId(principal.getName());

        List<ChatRoom> rooms = chatService.getPublicRoomsForMember(member.getGrade());
        List<ChatRoom> privateRooms = chatService.getPrivateRoomsForUser(member.getStudentId());
        List<ChatRoom> groupRooms = chatService.getGroupRoomsForUser(member.getStudentId());
        List<ChatRoom> tradeRooms = chatService.getTradeRoomsForUser(member.getStudentId());

        List<Long> allRoomIds = new ArrayList<>();
        rooms.forEach(r -> allRoomIds.add(r.getId()));
        privateRooms.forEach(r -> allRoomIds.add(r.getId()));
        groupRooms.forEach(r -> allRoomIds.add(r.getId()));
        tradeRooms.forEach(r -> allRoomIds.add(r.getId()));

        Map<Long, ChatMessage> lastMessages = chatService.getLastMessages(allRoomIds);
        Map<Long, Long> unreadCounts = chatService.getUnreadCounts(member.getStudentId(), allRoomIds);

        model.addAttribute("rooms", rooms);
        model.addAttribute("privateRooms", privateRooms);
        model.addAttribute("groupRooms", groupRooms);
        model.addAttribute("tradeRooms", tradeRooms);
        model.addAttribute("member", member);
        model.addAttribute("myGrade", String.valueOf(member.getGrade()));
        model.addAttribute("lastMessages", lastMessages);
        model.addAttribute("unreadCounts", unreadCounts);

        return "chat/index";
    }

    @GetMapping("/{id}")
    public String chatRoom(@PathVariable Long id, Model model, Principal principal) {
        Member member = memberRepository.findByStudentId(principal.getName());

        return chatService.findById(id).map(room -> {
            List<ChatMessage> messages = chatService.getMessages(id);
            chatService.markAsRead(member.getStudentId(), id);

            String displayRoomName = room.getName();
            Long otherLastRead = null;
            if (room.getType() == ChatRoom.RoomType.PRIVATE) {
                String[] keys = room.getRoomKey().split("_");
                String otherStudentId = keys[0].equals(member.getStudentId()) ? keys[1] : keys[0];
                Member other = memberRepository.findByStudentId(otherStudentId);
                if (other != null)
                    displayRoomName = other.getName();
                otherLastRead = chatService.getLastReadMessageId(otherStudentId, id);
            }

            model.addAttribute("room", room);
            model.addAttribute("displayRoomName", displayRoomName);
            model.addAttribute("otherLastRead", otherLastRead);
            model.addAttribute("messages", messages);
            model.addAttribute("member", member);

            // 공지 정보 추가
            model.addAttribute("pinnedNotice", room.getPinnedNotice());
            model.addAttribute("pinnedNoticeTitle", room.getPinnedNoticeTitle());
            model.addAttribute("pinnedTalkBoardId", room.getPinnedTalkBoardId());
            List<String> titles = room.getPinnedNoticeTitles() != null ? room.getPinnedNoticeTitles() : new ArrayList<>();
            List<Long> ids = room.getPinnedTalkBoardIds() != null ? room.getPinnedTalkBoardIds() : new ArrayList<>();
            model.addAttribute("pinnedNoticeTitles", titles);
            model.addAttribute("pinnedTalkBoardIds", ids);

            return "chat/room";
        }).orElse("redirect:/chat");
    }

    @PostMapping("/{id}/upload")
    @ResponseBody
    public Map<String, String> uploadFile(@PathVariable Long id,
                                          @RequestParam("file") MultipartFile file) throws Exception {
        String uploadDir = System.getProperty("user.dir") + "/uploads/";
        new File(uploadDir).mkdirs();
        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        file.transferTo(new File(uploadDir + filename));
        String url = "/uploads/" + filename;
        String type = file.getContentType() != null && file.getContentType().startsWith("image/") ? "image" : "file";
        return Map.of("url", url, "type", type, "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : filename);
    }

    @PostMapping("/group/create")
    public String createGroupRoom(@RequestParam String name,
                                  @RequestParam(defaultValue = "") String memberIds,
                                  Principal principal,
                                  RedirectAttributes redirectAttributes) {
        if (name.isBlank()) {
            redirectAttributes.addFlashAttribute("groupError", "채팅방 이름을 입력해주세요.");
            return "redirect:/chat";
        }
        Member me = memberRepository.findByStudentId(principal.getName());
        List<String> ids = Arrays.stream(memberIds.split("[,\\s]+"))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
        ChatRoom room = chatService.createGroupRoom(name.trim(), me.getStudentId(), ids);
        return "redirect:/chat/" + room.getId();
    }

    @PostMapping("/{id}/invite")
    public String inviteMember(@PathVariable Long id,
                               @RequestParam String studentId,
                               RedirectAttributes redirectAttributes) {
        Member target = memberRepository.findByStudentId(studentId.trim());
        if (target == null) {
            redirectAttributes.addFlashAttribute("inviteError", "존재하지 않는 학번입니다.");
            return "redirect:/chat/" + id;
        }
        boolean added = chatService.addMemberToGroup(id, studentId.trim());
        if (!added) {
            redirectAttributes.addFlashAttribute("inviteError", "이미 채팅방에 참여 중인 멤버입니다.");
        }
        return "redirect:/chat/" + id;
    }

    @GetMapping("/members/search")
    @ResponseBody
    public List<Map<String, Object>> searchMembers(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Integer grade,
            Principal principal) {
        String myId = principal.getName();
        List<Member> results;

        if (grade != null && !q.isBlank()) {
            results = memberRepository.searchByKeyword(q, myId)
                    .stream().filter(m -> m.getGrade() == grade).toList();
        } else if (grade != null) {
            results = memberRepository.findByGradeAndStudentIdNot(grade, myId);
        } else if (!q.isBlank()) {
            results = memberRepository.searchByKeyword(q, myId);
        } else {
            return List.of();
        }

        return results.stream().limit(10).map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("studentId", m.getStudentId());
            map.put("name", m.getName());
            map.put("grade", m.getGrade());
            return map;
        }).toList();
    }

    @GetMapping("/private")
    public String startPrivateChat(@RequestParam String targetStudentId,
                                   Principal principal,
            RedirectAttributes redirectAttributes) {
        Member me = memberRepository.findByStudentId(principal.getName());
        Member target = memberRepository.findByStudentId(targetStudentId);

        if (target == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "존재하지 않는 학번입니다.");
            return "redirect:/chat";
        }
        if (target.getStudentId().equals(me.getStudentId())) {
            redirectAttributes.addFlashAttribute("errorMsg", "자기 자신과는 채팅할 수 없습니다.");
            return "redirect:/chat";
        }

        ChatRoom room = chatService.getOrCreatePrivateRoom(
                me.getName(), me.getStudentId(),
                target.getName(), target.getStudentId());
        return "redirect:/chat/" + room.getId();
    }
}
