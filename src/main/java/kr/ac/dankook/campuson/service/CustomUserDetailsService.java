package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.repository.MemberRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    public CustomUserDetailsService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String studentId) throws UsernameNotFoundException {
        Member member = memberRepository.findByStudentId(studentId);

        if (member == null) {
            throw new UsernameNotFoundException("존재하지 않는 학번입니다.");
        }

        boolean locked = member.isBlocked()
                || (member.getBlockedUntil() != null && LocalDateTime.now().isBefore(member.getBlockedUntil()));

        return User.builder()
                .username(member.getStudentId())
                .password(member.getPassword())
                .roles(member.isAdmin() ? "ADMIN" : "USER")
                .accountLocked(locked)
                .build();
    }
}