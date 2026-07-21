package com.solux31.nubee_BE.domain.words.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KeywordDetailResDTO {
    private Long id;
    private String word;
    private String explanation;
    private String example_sentence;
    private String keyword_type;
}
