package com.ivanbonkin.neutrino;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Header {

    @Getter
    private long bodySize;

    @Getter
    private String fileName, mimeType;

}
