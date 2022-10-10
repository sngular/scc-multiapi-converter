package net.coru.multiapi.converter.util;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class FileHelper {

  public static File getFile(String fileName) {
    final URL url = FileHelper.class.getResource(fileName);
    return new File(url.getFile());
  }

  public String getContent(String fileName) throws Exception {
    return IOUtils.toString(
        getClass().getResourceAsStream(fileName),
        StandardCharsets.UTF_8
    );
  }
}

