/**
 * Copyright (C) 2022-2023 Agence du Numérique en Santé (ANS) (https://esante.gouv.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.ans.psc.pscextract.service.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FileNamesUtil {

  private static final Logger log = LoggerFactory.getLogger(FileNamesUtil.class);

  private FileNamesUtil() {
  }

  /**
   * Extract rass string.
   * @return the string
   */
  public static String extractRASSName(String extractName, String extractTime) {
    return extractName + "_" + extractTime + ".txt";
  }

  public static String getFilePath(String filesDirectory, String fileName) {
    if ("".equals(filesDirectory)) {
      return filesDirectory + fileName;
    } else {
      return filesDirectory + '/' + fileName;
    }
  }

  public static void cleanup(String filesDirectory, String exceptFile) {
    log.info("Cleaning files repository, removing all but latest file and demo");
    File[] fileArray = new File(filesDirectory).listFiles();
    List<File> listOfFiles = new ArrayList<>();
    if (fileArray != null) {
      listOfFiles.addAll(Arrays.asList(fileArray));
    }
    listOfFiles.removeIf(file -> file.getName().contains(exceptFile));
    listOfFiles.sort(FileNamesUtil::compare);

    if (listOfFiles.size() > 0) {
      listOfFiles.remove(listOfFiles.size() - 1);
    }

    for (File file : listOfFiles) {
      if (!file.isDirectory()) {
        if (file.delete()) {
          log.info("File " + file.getName() + " deleted");
        } else {
          log.error("File " + file.getName() + " could not be deleted");
        }
      }
    }
  }

  private static int compare(File f1, File f2) {
    try {
      return getDateFromFileName(f1).compareTo(getDateFromFileName(f2));
    } catch (ParseException e) {
      e.printStackTrace();
    }
    return 0;
  }

  public static Date getDateFromFileName(File file) throws ParseException {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMddhhmm");

    String regex = ".*(\\d{12}).*";
    Pattern pattern = Pattern.compile(regex);
    Matcher m = pattern.matcher(file.getName());
    if (m.find()) {
      return dateFormatter.parse(m.group(1));
    }
    return new Date(0);
  }

  public static File getLatestExtract(String filesDirectory, String extractName) {
    File[] allFiles = new File(filesDirectory).listFiles();
    File latestExtractFile = null;

    List<File> extractFiles = new ArrayList<>();

    for (File file : allFiles != null ? allFiles : new File[0]) {
      if (file.getName().startsWith(extractName)) {
        extractFiles.add(file);
      }
    }

    extractFiles.sort(FileNamesUtil::compare);

    if (!extractFiles.isEmpty()) {
      latestExtractFile = extractFiles.get(extractFiles.size() - 1);
    }
    return latestExtractFile;
  }


}
