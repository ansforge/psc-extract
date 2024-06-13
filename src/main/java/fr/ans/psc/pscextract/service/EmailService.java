/**
 * Copyright (C) 2022-2024 Agence du Numérique en Santé (ANS) (https://esante.gouv.fr)
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
package fr.ans.psc.pscextract.service;

import fr.ans.psc.pscextract.service.utils.FileNamesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  @Autowired
  private JavaMailSender emailSender;

  @Value("${spring.mail.username}")
  private String sender;

  @Value("${pscextract.mail.receiver}")
  private String receiver;

  @Value("${secpsc.environment}")
  private String platform;

  public void sendSimpleMessage(String subject, File latestExtract) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(sender);
    String[] allReceivers = receiver.split(",");
    message.setTo(allReceivers);
    message.setSubject(platform + " - " + subject);
    message.setText(getEmailMessage(latestExtract));

    emailSender.send(message);
  }

  private String getEmailMessage(File latestExtract) {
    if (latestExtract == null) {
      return "Échec lors de la génération du fichier par PSCEXTRACT.";
    }
    String stringDate = "";
    String stringHour = "";

    try {
      Date extractDate = FileNamesUtil.getDateFromFileName(latestExtract);
      SimpleDateFormat sdfDate = new SimpleDateFormat("dd MMM yyy");
      stringDate = sdfDate.format(extractDate);
      SimpleDateFormat sdfHour = new SimpleDateFormat("hh:mm");
      stringHour = sdfHour.format(extractDate);
    } catch (ParseException e) {
      log.error("Unable to parse extract file date", e);
    }

    return "Le fichier " + latestExtract.getName() + " a été généré par pscextract le "
            + stringDate + " à " + stringHour + " et est disponible au téléchargement.";
  }
}
