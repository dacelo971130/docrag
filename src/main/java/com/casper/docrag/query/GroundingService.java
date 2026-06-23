package com.casper.docrag.query;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 後處理引用驗證（SPEC §4.2 步驟 6，防線 4）。檢查回答標註的引用編號 [k] 是否落在
 * 檢索結果範圍內；越界引用代表模型可能編造來源。屬觀測性檢查（記錄/告警），不阻斷回應。
 */
@Component
public class GroundingService {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d{1,3})]");

    public GroundingResult validate(String answer, int retrievedCount) {
        if (answer == null || answer.isBlank()) {
            return new GroundingResult(true, List.of());
        }
        Matcher matcher = CITATION.matcher(answer);
        List<Integer> invalid = new ArrayList<>();
        while (matcher.find()) {
            int k = Integer.parseInt(matcher.group(1));
            if (k < 1 || k > retrievedCount) {
                invalid.add(k);
            }
        }
        return new GroundingResult(invalid.isEmpty(), List.copyOf(invalid));
    }

    public record GroundingResult(boolean valid, List<Integer> invalidCitations) {
    }
}
