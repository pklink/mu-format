package net.einself.mu.reader;

import net.einself.mu.dto.Key;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AttributeKeyExtractor implements Function<String, Key> {

    public static final Pattern ORDER_PREFIX_PATTERN = Pattern.compile("^.*-(\\d+)$");

    @Override
    public Key apply(String name) {
        return extract(name);
    }

    public Key extract(String name) {
        String keyPart = extractKeyPart(name);
        return extractOrderPrefix(keyPart);
    }

    private static String extractKeyPart(String name) {
        return Optional.of(name.indexOf('='))
                .filter(index -> index != -1)
                .map(index -> name.substring(0, index))
                .orElse(name);
    }

    private static Key extractOrderPrefix(String rv) {
        Matcher matcher = ORDER_PREFIX_PATTERN.matcher(rv);

        if (matcher.find()) {
            String group = matcher.group(1);
            Integer order = Integer.valueOf(group);
            String name = rv.substring(0, rv.lastIndexOf(group) - 1);
            return new Key(name, order);
        }

        return new Key(rv, -1);
    }

}
