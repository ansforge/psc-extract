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
package fr.ans.psc.pscextract;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import fr.ans.psc.pscextract.controller.ExtractionController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SpringBootTest
@ContextConfiguration(classes = PscextractApplication.class)
@AutoConfigureMockMvc
class ExtractionControllerTests {
  /* 
   * Target the copy from test-ressource stage rather than the sources, 
   * to avoid creating bogus diff during tests.
   */
  private static final String TEST_RESOURCE_DIRECTORY = "target/test-classes/work";
  private static final String EDIT_UPLOAD_TOKEN = "edit-upload-token";
  private static final String EDIT_EXTRACT_NAME = "Extraction_EDIT_partenaire.zip";
  private static final Path EDIT_EXTRACT_PATH = Path.of(TEST_RESOURCE_DIRECTORY, "edit", EDIT_EXTRACT_NAME);
  private static final byte[] EDIT_EXTRACT_CONTENT = {'P', 'K', 3, 4, 'e', 'd', 'i', 't'};

  @Autowired
  private ExtractionController controller;

  @Autowired
  private MockMvc mockMvc;

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
    propertiesRegistry.add("working.directory", () -> TEST_RESOURCE_DIRECTORY);
    propertiesRegistry.add("files.directory", () -> TEST_RESOURCE_DIRECTORY);
    propertiesRegistry.add("page.size", () -> "1");
    propertiesRegistry.add("first.name.count", () -> "3");
    propertiesRegistry.add("edit.upload.token", () -> EDIT_UPLOAD_TOKEN);

  }

  @BeforeEach
  private void Clean() {
    controller.cleanAll();
    await().until(controllerIsReady(controller));
  }

  @Test
  void singlePageExtractionAndResultConformityTest() throws Exception {

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("multiple-work-situations.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(null);
    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();

    String expected = getContentAsString("multiple-work-situations-result");
    String actual = getDataEntryAsString(response);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  void multiplePagesExtractionAndResultConformityTest() throws Exception {

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page1size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page2size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=2&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page3size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=3&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(1);
    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();

    String expected = getContentAsString("multiple-pages-result");
    String actual = getDataEntryAsString(response);
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
//  @Disabled //FIXME please tell why !!! Disabled tests hsould disappear or get fixed, prefably the latter.
  void emptyPsExtractionTest() throws Exception {

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("empty-ps.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(null);
    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();

    String expected = getContentAsString("empty-ps-result");
    String actual = getDataEntryAsString(response);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  void fullyEmptyPsExtractionTest() throws Exception {

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("very-empty-ps.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(null);
    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();

    String expected = getContentAsString("very-empty-ps-result");
    String actual = getDataEntryAsString(response);
    Assertions.assertEquals(expected, actual);
  }

  @Test
  void testUploadDemoExtractFile() throws Exception {
    String filesDirectory = "target/test-classes/work";
    controller.extractTestName = "test.zip";
    String testContent = "Hello, World!";
    File extractTestFile = new File(filesDirectory, controller.extractTestName);

    Field filesDirectoryField = controller.getClass().getDeclaredField("filesDirectory");
    filesDirectoryField.setAccessible(true);
    filesDirectoryField.set(controller, filesDirectory);


    MockMultipartFile testFile = new MockMultipartFile("testFile", "test.zip", MediaType.TEXT_PLAIN_VALUE, testContent.getBytes());

    mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/test")
                    .file(testFile))
            .andExpect(status().isAccepted());


    assertThat(extractTestFile).exists();
    assertThat(Files.readString(extractTestFile.toPath())).isEqualTo(testContent);

    // cleanup
    Files.delete(extractTestFile.toPath());
  }


  private MockMultipartFile editUpload(byte[] content) {
    return new MockMultipartFile("file", EDIT_EXTRACT_NAME, "application/zip", content);
  }

  @Test
  void editExtractIsUploadedThenDownloadedBesideTheRassExtract() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/edit")
                    .file(editUpload(EDIT_EXTRACT_CONTENT))
                    .header("X-Upload-Token", EDIT_UPLOAD_TOKEN))
            .andExpect(status().isAccepted());

    assertThat(Files.readAllBytes(EDIT_EXTRACT_PATH)).isEqualTo(EDIT_EXTRACT_CONTENT);

    var response = mockMvc.perform(MockMvcRequestBuilders.get("/download/edit"))
            .andExpect(status().isOk())
            .andReturn().getResponse();
    assertThat(response.getContentType()).isEqualTo("application/zip");
    assertThat(response.getContentAsByteArray()).isEqualTo(EDIT_EXTRACT_CONTENT);
  }

  @Test
  void theEditExtractIsServedUnderTheNameItWasDepositedWith() throws Exception {
    String deposited = "202611041354_Extract_RASS_PARTENAIRES_PSC.zip";
    mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/edit")
                    .file(new MockMultipartFile("file", deposited, "application/zip", EDIT_EXTRACT_CONTENT))
                    .header("X-Upload-Token", EDIT_UPLOAD_TOKEN))
            .andExpect(status().isAccepted());

    var response = mockMvc.perform(MockMvcRequestBuilders.get("/download/edit"))
            .andExpect(status().isOk())
            .andReturn().getResponse();

    assertThat(response.getHeader("Content-Disposition"))
            .isEqualTo("attachment; filename=\"" + deposited + "\"");
    assertThat(response.getContentAsByteArray()).isEqualTo(EDIT_EXTRACT_CONTENT);
  }

  @Test
  void aDepositNameThatCouldEscapeTheDirectoryIsRefused() throws Exception {
    for (String name : new String[]{"../escaped.zip", "sub/dir.zip", "not-a-zip.txt"}) {
      mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/edit")
                      .file(new MockMultipartFile("file", name, "application/zip", EDIT_EXTRACT_CONTENT))
                      .header("X-Upload-Token", EDIT_UPLOAD_TOKEN))
              .andExpect(status().isBadRequest());
    }
  }

  @Test
  void aNewEditUploadReplacesThePreviousOne() throws Exception {
    byte[] second = {'P', 'K', 3, 4, 's', 'e', 'c', 'o', 'n', 'd'};
    for (byte[] content : new byte[][]{EDIT_EXTRACT_CONTENT, second}) {
      mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/edit")
                      .file(editUpload(content))
                      .header("X-Upload-Token", EDIT_UPLOAD_TOKEN))
              .andExpect(status().isAccepted());
    }

    assertThat(Files.readAllBytes(EDIT_EXTRACT_PATH)).isEqualTo(second);
    try (var files = Files.list(EDIT_EXTRACT_PATH.getParent())) {
      assertThat(files.map(path -> path.getFileName().toString())).containsExactly(EDIT_EXTRACT_NAME);
    }
  }

  @Test
  void editUploadIsRefusedWithAWrongOrMissingToken() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/edit")
                    .file(editUpload(EDIT_EXTRACT_CONTENT))
                    .header("X-Upload-Token", "wrong"))
            .andExpect(status().isUnauthorized());
    mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/edit")
                    .file(editUpload(EDIT_EXTRACT_CONTENT)))
            .andExpect(status().isUnauthorized());

    assertThat(EDIT_EXTRACT_PATH).doesNotExist();
  }

  @Test
  void editUploadIsDisabledWhenNoTokenIsConfigured() throws Exception {
    ReflectionTestUtils.setField(controller, "editUploadToken", "");
    try {
      mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/edit")
                      .file(editUpload(EDIT_EXTRACT_CONTENT))
                      .header("X-Upload-Token", ""))
              .andExpect(status().isServiceUnavailable());
    } finally {
      ReflectionTestUtils.setField(controller, "editUploadToken", EDIT_UPLOAD_TOKEN);
    }

    assertThat(EDIT_EXTRACT_PATH).doesNotExist();
  }

  @Test
  void editUploadRefusesAFileThatIsNotAZip() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/edit")
                    .file(editUpload("not a zip".getBytes(StandardCharsets.UTF_8)))
                    .header("X-Upload-Token", EDIT_UPLOAD_TOKEN))
            .andExpect(status().isBadRequest());

    assertThat(EDIT_EXTRACT_PATH).doesNotExist();
  }

  @Test
  void editDownloadIsNotFoundBeforeAnyUpload() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/download/edit"))
            .andExpect(status().isNotFound());
  }

  @Test
  void rassExtractGenerationLeavesTheEditExtractAlone() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/edit")
                    .file(editUpload(EDIT_EXTRACT_CONTENT))
                    .header("X-Upload-Token", EDIT_UPLOAD_TOKEN))
            .andExpect(status().isAccepted());
    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page1size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(null);
    await().until(controllerIsReady(controller));

    assertThat(Files.readAllBytes(EDIT_EXTRACT_PATH)).isEqualTo(EDIT_EXTRACT_CONTENT);
    ResponseEntity<FileSystemResource> rassExtract = controller.getFile();
    assertThat(Objects.requireNonNull(rassExtract.getBody()).getFile().getName()).isNotEqualTo(EDIT_EXTRACT_NAME);
  }

  @Test
  void generateExtractTest() throws Exception {

    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page1size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page2size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=2&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page3size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=3&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(null);
    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();

    String expected = getContentAsString("multiple-pages-result");
    String actual = getDataEntryAsString(response);
    Assertions.assertEquals(expected, actual);
  }
  
  @Test
  public void shouldJoinExtractSha256Digest() throws IOException {
    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page1size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page2size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=2&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("page3size1.json")));
    httpMockServer.stubFor(get("/v2/ps?page=3&size=1").willReturn(aResponse().withStatus(410)));

    controller.generateExtract(null);
    await().until(controllerIsReady(controller));

    ResponseEntity<FileSystemResource> response = controller.getFile();
    
    String expected = getTxtTestResourceAsString("multiple-pages-result", ".sha256");
    String actual = getEntryContentAsString(response, ".sha256");
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

  @Test
  void verifyBusyStateConformityTest() {
    httpMockServer.stubFor(get("/v2/ps?page=0&size=1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBodyFile("multiple-work-situations.json")));
    httpMockServer.stubFor(get("/v2/ps?page=1&size=1").willReturn(aResponse().withStatus(410)));

    boolean busy = controller.checkControllerIsBusy();
    Assertions.assertFalse(busy);

    controller.generateExtract(null);

    await().until(controllerIsBusy(controller));
    busy = controller.checkControllerIsBusy();
    Assertions.assertTrue(busy);

    await().until(controllerIsReady(controller));
    busy = controller.checkControllerIsBusy();
    Assertions.assertFalse(busy);
  }

  private java.lang.String getDataEntryAsString(ResponseEntity<FileSystemResource> response) throws java.io.IOException {
    return getEntryContentAsString(response, ".txt");
  }

  private String getEntryContentAsString(ResponseEntity<FileSystemResource> response, final String extension) throws IOException {
    ZipFile zipFile = new ZipFile(Objects.requireNonNull(response.getBody()).getFile());
    Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
    InputStream stream = null;
    while (enumeration.hasMoreElements()) {
      ZipEntry zipEntry = enumeration.nextElement();
      if(zipEntry.getName().endsWith(extension)){
        stream = zipFile.getInputStream(zipEntry);
      }
    }
    assert stream != null;
    byte[] responseBytes = stream.readAllBytes();
    zipFile.close();
    stream.close();
    return getNormalizedEOL(new String(responseBytes, StandardCharsets.UTF_8));
  }

  private String getContentAsString(String responseFilename) throws IOException {
    return getTxtTestResourceAsString(responseFilename, ".txt");
  }

  private String getTxtTestResourceAsString(String responseFilename, final String txtExtension) throws IOException {
    Path responsePath = new File(Thread.currentThread().getContextClassLoader().getResource("wiremock/__files/" + responseFilename + txtExtension).getFile()).toPath();
    byte[] expectedResponseBytes = Files.readAllBytes(responsePath);
    return getNormalizedEOL(new String(expectedResponseBytes, StandardCharsets.UTF_8));
  }

  private String getNormalizedEOL(String content) {
    return content.replaceAll("\\r\\n?", "\n");
  }

  private Callable<Boolean> controllerIsReady(ExtractionController controller) {
    return () -> !controller.isBusy();
  }
  private Callable<Boolean> controllerIsBusy(ExtractionController controller) {
    return () -> (Boolean) controller.checkControllerIsBusy();
  }
}
