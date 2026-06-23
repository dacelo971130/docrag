package com.casper.docrag.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingServiceTest {

    private final GroundingService service = new GroundingService();

    @Test
    void citationsWithinRangeAreValid() {
        GroundingService.GroundingResult result = service.validate("依據 [1] 與 [2] 可知……", 2);
        assertThat(result.valid()).isTrue();
        assertThat(result.invalidCitations()).isEmpty();
    }

    @Test
    void outOfRangeCitationIsFlagged() {
        GroundingService.GroundingResult result = service.validate("見片段 [3] 與 [1]", 2);
        assertThat(result.valid()).isFalse();
        assertThat(result.invalidCitations()).containsExactly(3);
    }

    @Test
    void blankAnswerIsValid() {
        assertThat(service.validate("", 0).valid()).isTrue();
        assertThat(service.validate(null, 0).valid()).isTrue();
    }

    @Test
    void answerWithoutCitationsIsValid() {
        assertThat(service.validate("這是一段沒有引用標註的回答。", 3).valid()).isTrue();
    }
}
