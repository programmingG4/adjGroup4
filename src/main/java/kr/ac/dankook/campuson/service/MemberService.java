package kr.ac.dankook.campuson.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(String name, String studentId, String password, MultipartFile image) throws IOException {

        if (memberRepository.existsByStudentId(studentId)) {
            throw new IllegalArgumentException("이미 가입된 학번입니다.");
        }

        // OCR로 이름 + 학과 인증
        String detectedText = extractText(image);
        System.out.println("OCR 인식 텍스트: " + detectedText);

        if (!detectedText.contains("컴퓨터공학과")) {
            throw new IllegalArgumentException("컴퓨터공학과 학생만 가입할 수 있습니다.");
        }

        if (!detectedText.contains(name)) {
            throw new IllegalArgumentException("이름이 단국대 앱 캡쳐와 일치하지 않습니다.");
        }

        // 이미지 저장
        String imagePath = saveImage(image);

        // 회원 저장
        Member member = new Member();
        member.setName(name);
        member.setStudentId(studentId);
        member.setPassword(passwordEncoder.encode(password));
        member.setImagePath(imagePath);
        member.setStatus(Member.MemberStatus.APPROVED);

        memberRepository.save(member);
    }

    private String extractText(MultipartFile image) throws IOException {
        System.setProperty("jna.library.path", "/opt/homebrew/lib");

        BufferedImage bufferedImage = ImageIO.read(image.getInputStream());

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/opt/homebrew/share/tessdata");
        tesseract.setLanguage("kor");

        try {
            return tesseract.doOCR(bufferedImage);
        } catch (TesseractException e) {
            e.printStackTrace();
            return "";
        }
    }

    private String saveImage(MultipartFile image) throws IOException {
        String uploadDir = System.getProperty("user.home") + "/uploads/";
        File dir = new File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        String fileName = UUID.randomUUID() + "_" + image.getOriginalFilename();
        File dest = new File(uploadDir + fileName);
        image.transferTo(dest);

        return uploadDir + fileName;
    }
}