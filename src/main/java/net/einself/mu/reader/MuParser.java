package net.einself.mu.reader;

import net.einself.mu.dto.MuFolder;

public interface MuParser<T> {

    public T parse(MuFolder folder);

}
