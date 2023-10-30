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
package fr.ans.psc.pscextract;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fr.ans.psc.pscextract.controller.ExtractionController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;


import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SpringBootTest
@ContextConfiguration(classes = PscextractApplication.class)
@AutoConfigureMockMvc
class ExtractionControllerTests {

  @Autowired
  private ExtractionController controller;

  /**
   * Countdown latch
   */
  private final CountDownLatch lock = new CountDownLatch(1);

  /**
   * The http mock server.
   */
  @RegisterExtension
  static WireMockExtension httpMockServer = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort().usingFilesUnderClasspath("wiremock")).build();

  /**
   * Register pg properties.
   * @param propertiesRegistry the properties registry
   */
  // For use with mockMvc
  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry propertiesRegistry) {
    propertiesRegistry.add("api.base.url", () -> httpMockServer.baseUrl());
    propertiesRegistry.add("working.directory", () -> "src/test/resources/work");
    propertiesRegistry.add("files.directory", () -> "src/test/resources/work");
    propertiesRegistry.add("page.size", () -> "1");
    propertiesRegistry.add("first.name.count", () -> "3");

  }

  @BeforeEach
  private void Clean() {
    controller.cleanAll();
    await().until(controllerIsReady(controller));
  }

  @Test
  void singlePageExtractionAndResultConformityTest() throws Exception {

    String responseFilename = "multiple-work-situations-result";
    String responsePath = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("wiremock/__files/" + responseFilename + ".txt")).getPath().substring(1);
    byte[] expectedResponseBytes = Files.readAllBytes(Paths.get(responsePath));

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("multiple-work-situations.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(null);

    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();

    byte[] responseBytes = getBytesFromResponse(response);

    String expected = new String(expectedResponseBytes, StandardCharsets.UTF_8);
    String actual = new String(responseBytes, StandardCharsets.UTF_8);

    Assertions.assertEquals(expected, actual);
  }

  @Test
  void multiplePagesExtractionAndResultConformityTest() throws Exception {
    String responseFilename = "multiple-pages-result";
    String responsePath = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("wiremock/__files/" + responseFilename + ".txt")).getPath().substring(1);
    byte[] expectedResponseBytes = Files.readAllBytes(Paths.get(responsePath));

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page1size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page2size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=2&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page3size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=3&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(1);

    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();

    byte[] responseBytes = getBytesFromResponse(response);

    String expected = new String(expectedResponseBytes, StandardCharsets.UTF_8);
    String actual = new String(responseBytes, StandardCharsets.UTF_8);

    Assertions.assertEquals(expected, actual);
  }

  @Test
  void noPagesExtractionTest() {
    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(1);

    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();
    Assertions.assertThrows(NullPointerException.class, () -> System.out.println(Objects.requireNonNull(response.getBody())));
  }

  @Test
  @Disabled
  void emptyPsExtractionTest() throws Exception {

    String responseFilename = "empty-ps-result";
    String responsePath = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("wiremock/__files/" + responseFilename + ".txt")).getPath().substring(1);
    byte[] expectedResponseBytes = Files.readAllBytes(Paths.get(responsePath));

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("empty-ps.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(null);

    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();

    byte[] responseBytes = getBytesFromResponse(response);

    String expected = new String(expectedResponseBytes, StandardCharsets.UTF_8);
    String actual = new String(responseBytes, StandardCharsets.UTF_8);

    Assertions.assertEquals(expected, actual);
  }

  @Test
  void fullyEmptyPsExtractionTest() throws Exception {

    String responseFilename = "very-empty-ps-result";
    String responsePath = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("wiremock/__files/" + responseFilename + ".txt")).getPath().substring(1);
    byte[] expectedResponseBytes = Files.readAllBytes(Paths.get(responsePath));

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("very-empty-ps.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(null);

    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();

    byte[] responseBytes = getBytesFromResponse(response);

    String expected = new String(expectedResponseBytes, StandardCharsets.UTF_8);
    String actual = new String(responseBytes, StandardCharsets.UTF_8);

    Assertions.assertEquals(expected, actual);
  }

  @Test
  void generateExtractTest() throws Exception {
    String responseFilename = "multiple-pages-result";
    String responsePath = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("wiremock/__files/" + responseFilename + ".txt")).getPath().substring(1);
    byte[] expectedResponseBytes = Files.readAllBytes(Paths.get(responsePath));

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page1size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page2size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=2&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page3size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=3&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(null);

    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();

    byte[] responseBytes = getBytesFromResponse(response);

    String expected = new String(expectedResponseBytes, StandardCharsets.UTF_8);
    String actual = new String(responseBytes, StandardCharsets.UTF_8);

    Assertions.assertEquals(expected, actual);
  }

  @Test
  void lockTest() {

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page1size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page2size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=2&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page3size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=3&size=1").willReturn(aResponse().withStatus(410)));

    ResponseEntity<FileSystemResource> response;

    controller.generateExtract(null);
    response = controller.getFile();
    ResponseEntity<FileSystemResource> finalResponse = response;
    assertThrows(NullPointerException.class, () -> Objects.requireNonNull(finalResponse.getBody()).getFile());
    ResponseEntity<?> responseFailure = controller.generateExtract(null);
    assert responseFailure != null;
    assertEquals(responseFailure.getStatusCode(), HttpStatus.CONFLICT);
  }

  private byte[] getBytesFromResponse(ResponseEntity<FileSystemResource> response) throws IOException {
    ZipFile zipFile = new ZipFile(Objects.requireNonNull(response.getBody()).getFile());
    Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
    InputStream stream = null;

    while (enumeration.hasMoreElements()) {
      ZipEntry zipEntry = enumeration.nextElement();
      stream = zipFile.getInputStream(zipEntry);
    }
    assert stream != null;
    byte[] responseBytes = stream.readAllBytes();

    zipFile.close();
    stream.close();
    return responseBytes;
  }

  private Callable<Boolean> controllerIsReady(ExtractionController controller) {
    return () -> !controller.isBusy();
  }
}
