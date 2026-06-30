package com.chubby.dolphin.contentfactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentFactoryScoringServiceTest {

    private final ContentFactoryScoringService service = new ContentFactoryScoringService();

    @Test
    void scoreFormulaUsesAllRequestedComponents() {
        var score = service.score(
                "Limited Offer Today",
                "Save now with a trusted new offer for local buyers.",
                "Book Now",
                "Exclusive deal ending soon. Only few slots today."
        );

        assertTrue(score.lengthScore() > 0);
        assertTrue(score.powerWordScore() > 0);
        assertTrue(score.urgencyScore() > 0);
        assertTrue(score.emojiScore() >= 0);
        assertTrue(score.clarityScore() > 0);
        assertEquals(ContentFactoryScoringService.FORMULA_VERSION, score.formulaVersion());
        assertEquals(
                score.lengthScore() + score.powerWordScore() + score.urgencyScore() + score.emojiScore() + score.clarityScore(),
                score.score()
        );
    }

    @Test
    void scoreClampsBetweenZeroAndOneHundred() {
        var highScore = service.score(
                "Free Now Limited Save Today",
                "Exclusive guaranteed trusted new offer today now limited last chance only few ending soon. Save now.",
                "Get Offer",
                "free now exclusive limited save offer new today guaranteed trusted last chance only few ending soon"
        );

        assertTrue(highScore.score() >= 0);
        assertTrue(highScore.score() <= 100);
    }
}
