package net.einself.mu.reader;

import net.einself.mu.MuInfo;

import java.io.File;

class MuFileParser {

    private final File file;

    public MuFileParser(File file) {
        this.file = file;
    }

    public MuInfo parse() {
        String name = file.getName().substring(0, file.getName().lastIndexOf(".mu"));
        String[] splits = name.split("=", 2);
        String key = MuInfo.Attribute.fromKey(splits[0]).map(MuInfo.Attribute::getLabel).orElse(splits[0]);
        String value = splits[1];
        return new MuInfo(key, value);
    }

}
