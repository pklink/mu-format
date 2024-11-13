package net.einself.reader;

import net.einself.dto.MuFolder;

public interface MuParser<T> {

    public T parse(MuFolder folder);

}
