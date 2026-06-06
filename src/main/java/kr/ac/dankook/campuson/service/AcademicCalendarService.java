package kr.ac.dankook.campuson.service;

import kr.ac.dankook.campuson.dto.AcademicScheduleEvent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AcademicCalendarService {

    private static final String DANKOOK_CALENDAR_BASE_URL = "https://www.dankook.ac.kr/web/kor/-2014-";
    private static final String DANKOOK_CALENDAR_LIST_URL_TEMPLATE = "https://www.dankook.ac.kr/web/kor/-2014-?p_p_id=dku_calendar_CalendarEvent&p_p_lifecycle=0&p_p_state=normal&p_p_mode=view&_dku_calendar_CalendarEvent_action=view&_dku_calendar_CalendarEvent_tab=list&_dku_calendar_CalendarEvent_selYear=%d";
    private static final String DANKOOK_BOOTCAMP_CALENDAR_URL_TEMPLATE = "https://bootcamp.dankook.ac.kr/education/courseList.php?list=list&year=%d";
    private static final int REQUEST_TIMEOUT_MS = 7000;
    private static final long CACHE_TTL_SECONDS = 60 * 60 * 6;

    private static final Pattern FULL_RANGE_PATTERN = Pattern.compile("(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})\\s*(?:~|～|-|–|—|부터|∼)\\s*(?:(\\d{4})[.\\-/])?(\\d{1,2})[.\\-/](\\d{1,2})");
    private static final Pattern FULL_SINGLE_PATTERN = Pattern.compile("(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})");
    private static final Pattern MONTH_RANGE_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})[.\\-/](\\d{1,2})\\s*(?:~|～|-|–|—|부터|∼)\\s*(\\d{1,2})[.\\-/](\\d{1,2})(?!\\d)");
    private static final Pattern MONTH_SINGLE_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,2})[.\\-/](\\d{1,2})(?!\\d)");

    private final Map<Integer, CachedSchedules> cachedSchedulesByYear = new HashMap<>();

    public List<AcademicScheduleEvent> findSchedulesForMonth(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1).minusDays(7);
        LocalDate end = yearMonth.atEndOfMonth().plusDays(7);
        return findSchedulesForRange(year, start, end);
    }

    public List<AcademicScheduleEvent> findMonthlyScheduleList(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        return findSchedulesForRange(year, yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }

    private List<AcademicScheduleEvent> findSchedulesForRange(int year, LocalDate start, LocalDate end) {
        return getAllSchedules(year).stream()
                .filter(event -> !event.endDate().isBefore(start) && !event.startDate().isAfter(end))
                .toList();
    }

    private List<AcademicScheduleEvent> getAllSchedules(int year) {
        CachedSchedules cachedSchedules = cachedSchedulesByYear.get(year);
        if (cachedSchedules != null && !cachedSchedules.isExpired()) {
            return cachedSchedules.events();
        }

        List<AcademicScheduleEvent> events = List.of();
        String bootcampListUrl = String.format(DANKOOK_BOOTCAMP_CALENDAR_URL_TEMPLATE, year);
        String dankookListUrl = String.format(DANKOOK_CALENDAR_LIST_URL_TEMPLATE, year);

        try {
            Document document = fetchDocument(bootcampListUrl);
            events = parseSchedules(document, year, bootcampListUrl);
        } catch (Exception ignored) {
            events = List.of();
        }

        if (events.isEmpty()) {
            try {
                Document document = fetchDocument(dankookListUrl);
                events = parseSchedules(document, year, dankookListUrl);
            } catch (Exception ignored) {
                events = List.of();
            }
        }

        cachedSchedulesByYear.put(year, new CachedSchedules(events, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
        return events;
    }

    private Document fetchDocument(String url) throws java.io.IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 CampusON/1.0")
                .referrer(DANKOOK_CALENDAR_BASE_URL)
                .timeout(REQUEST_TIMEOUT_MS)
                .followRedirects(true)
                .get();
    }

    private List<AcademicScheduleEvent> parseSchedules(Document document, int fallbackYear, String sourceUrl) {
        Map<String, AcademicScheduleEvent> unique = new LinkedHashMap<>();

        List<ScheduleSource> sources = new ArrayList<>();
        sources.addAll(collectWholeTextPairSources(document, sourceUrl));
        sources.addAll(collectElementPairSources(document, sourceUrl));
        sources.addAll(collectElementSources(document, sourceUrl));

        for (ScheduleSource source : sources) {
            String text = normalize(source.text());
            if (!isUsableScheduleText(text)) {
                continue;
            }

            Optional<DateRange> dateRange = extractDateRange(text, fallbackYear);
            if (dateRange.isEmpty()) {
                continue;
            }

            String title = cleanupTitle(text);
            if (!isPotentialScheduleTitle(title)) {
                continue;
            }

            DateRange range = dateRange.get();
            AcademicScheduleEvent event = new AcademicScheduleEvent(
                    stableId(title, range.startDate(), range.endDate()),
                    title,
                    range.startDate(),
                    range.endDate(),
                    classify(title),
                    source.sourceUrl().isBlank() ? sourceUrl : source.sourceUrl()
            );
            unique.putIfAbsent(event.id(), event);
        }

        return unique.values().stream()
                .sorted((left, right) -> {
                    int byDate = left.startDate().compareTo(right.startDate());
                    return byDate != 0 ? byDate : left.title().compareTo(right.title());
                })
                .toList();
    }

    private List<ScheduleSource> collectWholeTextPairSources(Document document, String sourceUrl) {
        List<ScheduleSource> sources = new ArrayList<>();
        List<String> lines = document.wholeText().lines()
                .map(this::normalize)
                .filter(line -> !line.isBlank())
                .toList();

        for (int i = 0; i < lines.size() - 1; i++) {
            String dateText = lines.get(i);
            String titleText = lines.get(i + 1);
            if (isDateOnlyText(dateText) && isPotentialScheduleTitle(titleText)) {
                sources.add(new ScheduleSource(dateText + " " + titleText, sourceUrl));
                i++;
            }
        }
        return sources;
    }

    private List<ScheduleSource> collectElementPairSources(Document document, String sourceUrl) {
        List<ScheduleSource> sources = new ArrayList<>();
        List<String> items = new ArrayList<>();

        for (Element candidate : document.select("li, tr, td, dd, dt, p, span, div")) {
            String text = normalize(candidate.ownText().isBlank() ? candidate.text() : candidate.ownText());
            if (!text.isBlank()) {
                items.add(text);
            }
        }

        for (int i = 0; i < items.size() - 1; i++) {
            String dateText = items.get(i);
            String titleText = items.get(i + 1);
            if (isDateOnlyText(dateText) && isPotentialScheduleTitle(titleText)) {
                sources.add(new ScheduleSource(dateText + " " + titleText, sourceUrl));
                i++;
            }
        }
        return sources;
    }

    private List<ScheduleSource> collectElementSources(Document document, String sourceUrl) {
        List<ScheduleSource> sources = new ArrayList<>();

        for (Element candidate : document.select("table tr, li, .schedule, .calendar, .event, .bbs, .list, .cont, .content, [class*=calendar], [class*=schedule], [class*=event]")) {
            String text = normalize(candidate.text());
            if (!text.isBlank()) {
                sources.add(new ScheduleSource(text, firstLinkOrDefault(candidate, sourceUrl)));
            }
        }
        return sources;
    }

    private String firstLinkOrDefault(Element candidate, String sourceUrl) {
        return Optional.ofNullable(candidate.selectFirst("a[href]"))
                .map(link -> link.absUrl("href"))
                .filter(value -> !value.isBlank())
                .orElse(sourceUrl);
    }

    private boolean isDateOnlyText(String text) {
        String normalized = normalize(text);
        return FULL_RANGE_PATTERN.matcher(normalized).matches()
                || FULL_SINGLE_PATTERN.matcher(normalized).matches()
                || MONTH_RANGE_PATTERN.matcher(normalized).matches()
                || MONTH_SINGLE_PATTERN.matcher(normalized).matches();
    }

    private boolean isPotentialScheduleTitle(String text) {
        String normalized = normalize(text);
        return normalized.length() >= 2
                && normalized.length() <= 180
                && !isDateOnlyText(normalized)
                && !isCalendarLabel(normalized)
                && !isNoiseText(normalized)
                && containsKoreanOrLetter(normalized);
    }

    private boolean containsKoreanOrLetter(String text) {
        return text.matches(".*[가-힣A-Za-z].*");
    }

    private boolean isCalendarLabel(String text) {
        String normalized = normalize(text);
        return normalized.isBlank()
                || normalized.matches("\\d{1,2}")
                || normalized.matches("\\d{1,2}월")
                || normalized.matches("[일월화수목금토]")
                || normalized.equalsIgnoreCase("TODAY")
                || normalized.equals("월간")
                || normalized.equals("목록")
                || normalized.equals("학기")
                || normalized.equals("월")
                || normalized.equals("일정")
                || normalized.equals("학사내용")
                || normalized.equals("calendar_month")
                || normalized.equals("list")
                || normalized.equals("grid_view");
    }

    private boolean isUsableScheduleText(String text) {
        return text.length() >= 6 && text.length() <= 900 && containsDateLikeText(text) && !isNoiseText(text);
    }

    private boolean containsDateLikeText(String text) {
        return FULL_SINGLE_PATTERN.matcher(text).find() || MONTH_SINGLE_PATTERN.matcher(text).find();
    }

    private boolean isNoiseText(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("copyright")
                || lower.contains("chevron")
                || lower.contains("today")
                || lower.contains("print")
                || lower.contains("ssl")
                || lower.contains("certificate")
                || lower.contains("style")
                || lower.contains("margin")
                || lower.contains("function")
                || lower.contains("script")
                || lower.contains("javascript")
                || lower.contains("swiper")
                || lower.contains("console")
                || lower.contains("var ")
                || lower.contains("let ")
                || lower.contains("const ")
                || text.contains("유효기간")
                || text.contains("유효...")
                || text.contains("본문 바로가기")
                || text.contains("메인메뉴")
                || text.contains("공유하기")
                || text.contains("개인정보처리방침")
                || text.contains("이용약관")
                || text.contains("이메일무단수집")
                || text.contains("관리자로그인")
                || text.contains("유관기관사이트")
                || text.contains("콘텐츠 실명제")
                || text.contains("담당부서")
                || text.contains("최종수정일")
                || text.contains("공통 퀵링크")
                || text.contains("경기도 용인시")
                || text.contains("충남 천안시")
                || text.contains("학사일정 DKU 캘린더")
                || text.matches(".*(?:03월|04월|05월|06월|07월|08월|09월|10월|11월|12월|01월|02월).*Today.*");
    }

    private Optional<DateRange> extractDateRange(String text, int fallbackYear) {
        Matcher fullRange = FULL_RANGE_PATTERN.matcher(text);
        if (fullRange.find()) {
            int startYear = parseInt(fullRange.group(1));
            int startMonth = parseInt(fullRange.group(2));
            int startDay = parseInt(fullRange.group(3));
            int endYear = fullRange.group(4) == null ? startYear : parseInt(fullRange.group(4));
            int endMonth = parseInt(fullRange.group(5));
            int endDay = parseInt(fullRange.group(6));
            return dateRange(startYear, startMonth, startDay, endYear, endMonth, endDay);
        }

        Matcher monthRange = MONTH_RANGE_PATTERN.matcher(text);
        if (monthRange.find()) {
            int startMonth = parseInt(monthRange.group(1));
            int startDay = parseInt(monthRange.group(2));
            int endMonth = parseInt(monthRange.group(3));
            int endDay = parseInt(monthRange.group(4));
            int endYear = endMonth < startMonth ? fallbackYear + 1 : fallbackYear;
            return dateRange(fallbackYear, startMonth, startDay, endYear, endMonth, endDay);
        }

        Matcher fullSingle = FULL_SINGLE_PATTERN.matcher(text);
        if (fullSingle.find()) {
            int year = parseInt(fullSingle.group(1));
            int month = parseInt(fullSingle.group(2));
            int day = parseInt(fullSingle.group(3));
            return dateRange(year, month, day, year, month, day);
        }

        Matcher monthSingle = MONTH_SINGLE_PATTERN.matcher(text);
        if (monthSingle.find()) {
            int month = parseInt(monthSingle.group(1));
            int day = parseInt(monthSingle.group(2));
            return dateRange(fallbackYear, month, day, fallbackYear, month, day);
        }

        return Optional.empty();
    }

    private Optional<DateRange> dateRange(int startYear, int startMonth, int startDay, int endYear, int endMonth, int endDay) {
        try {
            LocalDate start = LocalDate.of(startYear, startMonth, startDay);
            LocalDate end = LocalDate.of(endYear, endMonth, endDay);
            if (end.isBefore(start)) {
                end = start;
            }
            return Optional.of(new DateRange(start, end));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String cleanupTitle(String text) {
        String title = text
                .replaceAll("\\d{4}[.\\-/]\\d{1,2}[.\\-/]\\d{1,2}\\s*(?:~|～|-|–|—|부터|∼)\\s*(?:\\d{4}[.\\-/])?\\d{1,2}[.\\-/]\\d{1,2}", " ")
                .replaceAll("\\d{4}[.\\-/]\\d{1,2}[.\\-/]\\d{1,2}", " ")
                .replaceAll("(?<!\\d)\\d{1,2}[.\\-/]\\d{1,2}\\s*(?:~|～|-|–|—|부터|∼)\\s*\\d{1,2}[.\\-/]\\d{1,2}(?!\\d)", " ")
                .replaceAll("(?<!\\d)\\d{1,2}[.\\-/]\\d{1,2}(?!\\d)", " ")
                .replaceAll("\\bSUN\\b|\\bMON\\b|\\bTUE\\b|\\bWED\\b|\\bTHU\\b|\\bFRI\\b|\\bSAT\\b", " ")
                .replaceAll("일정|학사일정|캘린더|DKU", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (title.length() > 120) {
            return title.substring(0, 120).trim();
        }
        return title;
    }

    private String classify(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (title.contains("시험") || title.contains("고사")) {
            return "시험";
        }
        if (title.contains("수강") || title.contains("신청") || title.contains("정정")) {
            return "수강";
        }
        if (title.contains("등록") || title.contains("납부")) {
            return "등록";
        }
        if (title.contains("휴학") || title.contains("복학") || title.contains("졸업") || lower.contains("graduation")) {
            return "학적";
        }
        if (title.contains("방학") || title.contains("개강") || title.contains("종강")) {
            return "학기";
        }
        return "학사";
    }

    private int parseInt(String value) {
        return Integer.parseInt(value);
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String stableId(String title, LocalDate startDate, LocalDate endDate) {
        String raw = title + "|" + startDate.format(DateTimeFormatter.ISO_DATE) + "|" + endDate.format(DateTimeFormatter.ISO_DATE);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes());
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8 && i < hashed.length; i++) {
                builder.append(String.format("%02x", hashed[i]));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return raw.replaceAll("[^a-zA-Z0-9가-힣]", "");
        }
    }

    private record ScheduleSource(String text, String sourceUrl) {
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    private record CachedSchedules(List<AcademicScheduleEvent> events, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
