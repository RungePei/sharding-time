package com.github.shardingTime.common;

import java.util.Arrays;
import java.util.List;

public interface Constants {
    char zero = '0';

    interface Symbol {
        Character underLine = '_';
        Character singleQuote = '\'';
        Character backQuote = '`';
        Character quote = '"';
        List<Character> quoteList = Arrays.asList(singleQuote, backQuote, quote);
    }
}
