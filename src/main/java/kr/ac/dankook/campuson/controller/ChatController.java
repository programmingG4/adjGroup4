package kr.ac.dankook.campuson.controller;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.entity.ChatMessage;
import kr.ac.dankook.campuson.entity.ChatRoom;
import kr.ac.dankook.campuson.repository.MemberRepository;
import kr.ac.dankook.campuson.service.ChatService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        List<Long> allRoomIds = new ArrayList<>();
        rooms.forEach(r -> allRoomIds.add(r.getId()));
        privateRooms.forEach(r -> allRoomIds.add(r.getId()));

        Map<Long, ChatMessage> lastMessages = chatService.getLastMessages(allRoomIds);
        Map<Long, Long> unreadCounts = chatService.getUnreadCounts(member.getStudentId(), allRoomIds);

        model.addAttribute("rooms", rooms);
        model.addAttribute("privateRooms", privateRooms);
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
            model.addAttribute("pinnedNoticeTitles",
                    room.getPinnedNoticeTitles() != null ? room.getPinnedNoticeTitles() : new ArrayList<>());
            model.addAttribute("pinnedTalkBoardIds",
                    room.getPinnedTalkBoardIds() != null ? room.getPinnedTalkBoardIds() : new ArrayList<>());

            return "chat/room";
        }).orElse("redirect:/chat");
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
