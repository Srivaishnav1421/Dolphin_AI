package com.chubby.dolphin.contentfactory.dto;

public record ContentScoreBreakdown(
        int lengthScore,
        int powerWordScore,
        int urgencyScore,
        int emojiScore,
        int clarityScore,
        int score,
        String formulaVersion
) {}
