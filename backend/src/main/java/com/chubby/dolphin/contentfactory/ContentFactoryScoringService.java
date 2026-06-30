package com.chubby.dolphin.contentfactory;

import com.chubby.dolphin.contentfactory.dto.ContentScoreBreakdown;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ContentFactoryScoringService {

    public static final String FORMULA_VERSION = "content-factory-score-v1";
    private static final Set<String> POWER_WORDS = Set.of(
            "free", "now", "exclusive", "limited", "save",
            "offer", "new", "today", "guaranteed", "trusted"
    );
    private static final Set<String> URGENCY_WORDS = Set.of(
            "today", "now", "limited", "last chance", "only few", "ending soon"
    );
    private static final Pattern EMOJI_PATTERN = Pattern.compile("\\p{So}");

    public ContentScoreBreakdown score(String headline, String description, String cta, String contentText) {
        String fullText = normalize((headline == null ? "" : headline) + " "
                + (description == null ? "" : description) + " "
                + (cta == null ? "" : cta) + " "
                + (contentText == null ? "" : contentText));

        int lengthScore = lengthScore(headline, description, cta);
        int powerWordScore = cappedCount(fullText, POWER_WORDS, 3, 30);
        int urgencyScore = cappedCount(fullText, URGENCY_WORDS, 5, 20);
        int emojiScore = emojiScore(headline, description, contentText);
        int clarityScore = clarityScore(headline, description, cta, contentText);
        int score = clamp(lengthScore + powerWordScore + urgencyScore + emojiScore + clarityScore, 0, 100);

        return new ContentScoreBreakdown(
                lengthScore,
                powerWordScore,
                urgencyScore,
                emojiScore,
                clarityScore,
                score,
                FORMULA_VERSION
        );
    }

    private int lengthScore(String headline, String description, String cta) {
        int score = 0;
        if (within(headline, 1, 40)) score += 5;
        if (within(description, 1, 125)) score += 5;
        if (within(cta, 1, 24)) score += 5;
        return score;
    }

    private int cappedCount(String text, Set<String> words, int pointsPerHit, int maxScore) {
        int hits = 0;
        for (String word : words) {
            if (text.contains(word)) {
                hits++;
            }
        }
        return Math.min(maxScore, hits * pointsPerHit);
    }

    private int emojiScore(String headline, String description, String contentText) {
        String text = (headline == null ? "" : headline)
                + (description == null ? "" : description)
                + (contentText == null ? "" : contentText);
        Matcher matcher = EMOJI_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        if (count == 0) {
            return 6;
        }
        if (count <= 3) {
            return 10;
        }
        return 4;
    }

    private int clarityScore(String headline, String description, String cta, String contentText) {
        int score = 0;
        String combined = normalize((headline == null ? "" : headline) + " "
                + (description == null ? "" : description) + " "
                + (contentText == null ? "" : contentText));
        if (combined.split("\\s+").length >= 5) score += 5;
        if (hasActionVerb(cta) || hasActionVerb(combined)) score += 8;
        if (!combined.contains("???") && !combined.contains("!!!")) score += 4;
        if (hasAudienceOrBenefit(description, contentText)) score += 4;
        if (within(headline, 1, 40) && within(description, 1, 125)) score += 4;
        return Math.min(25, score);
    }

    private boolean hasActionVerb(String value) {
        String text = normalize(value);
        return text.contains("book")
                || text.contains("shop")
                || text.contains("save")
                || text.contains("start")
                || text.contains("get")
                || text.contains("learn")
                || text.contains("claim")
                || text.contains("contact")
                || text.contains("join")
                || text.contains("try");
    }

    private boolean hasAudienceOrBenefit(String description, String contentText) {
        String text = normalize((description == null ? "" : description) + " " + (contentText == null ? "" : contentText));
        return text.contains("for ") || text.contains("with ") || text.contains("save") || text.contains("trusted");
    }

    private boolean within(String value, int min, int max) {
        if (value == null) return false;
        int length = value.trim().length();
        return length >= min && length <= max;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
