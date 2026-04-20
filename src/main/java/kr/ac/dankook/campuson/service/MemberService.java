package kr.ac.dankook.campuson.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ac.dankook.campuson.domain.Member;
import kr.ac.dankook.campuson.repository.MemberRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.UUID;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${google.vision.api-key}")
    private String googleApiKey;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(String name, String studentId, String password, MultipartFile image) throws IOException {

        if (memberRepository.existsByStudentId(studentId)) {
            throw new IllegalArgumentException("이미 가입된 학번입니다.");
        }

        String detectedText = extractText(image);
        System.out.println("OCR 인식 텍스트: " + detectedText);

        if (!detectedText.contains("컴퓨터공학과")) {
            throw new IllegalArgumentException("컴퓨터공학과 학생만 가입할 수 있습니다.");
        }

        if (!detectedText.contains(name)) {
            throw new IllegalArgumentException("이름이 단국대 앱 캡쳐와 일치하지 않습니다.");
        }

        String imagePath = saveImage(image);

        Member member = new Member();
        member.setName(name);
        member.setStudentId(studentId);
        member.setPassword(passwordEncoder.encode(password));
        member.setImagePath(imagePath);
        member.setStatus(Member.MemberStatus.APPROVED);

        memberRepository.save(member);
    }

    private String extractText(MultipartFile image) throws IOException {
        String base64Image = Base64.getEncoder().encodeToString(image.getBytes());

        String requestBody = String.format("""
            {
              "requests": [
                {
                  "image": { "content": "%s" },
                  "features": [{ "type": "TEXT_DETECTION" }]
                }
              ]
            }
            """, base64Image);

        String url = "https://vision.googleapis.com/v1/images:annotate?key=" + googleApiKey;

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Google Vision 응답: " + response.body());
            return parseOcrText(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private String parseOcrText(String responseBody) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(responseBody);
        JsonNode textAnnotations = root.path("responses").path(0).path("textAnnotations");
        if (textAnnotations.isEmpty()) return "";
        return textAnnotations.path(0).path("description").asText();
    }

    public void changePassword(String studentId, String currentPassword, String newPassword) {
        Member member = memberRepository.findByStudentId(studentId);
        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        member.setPassword(passwordEncoder.encode(newPassword));
        memberRepository.save(member);
    }

    public void deleteAccount(String studentId) {
        Member member = memberRepository.findByStudentId(studentId);
        memberRepository.delete(member);
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
