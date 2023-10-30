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

import fr.ans.psc.model.FirstName;
import fr.ans.psc.pscextract.service.TransformationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ContextConfiguration(classes = PscextractApplication.class)
public class TransformationServiceTest {

    @Autowired
    TransformationService transformationService;

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry propertiesRegistry) {
        propertiesRegistry.add("page.size", () -> "1");
    }

    @Test
    public void transformFirstNamesTest() {
        FirstName fn1 = new FirstName("KADER", 0);
        FirstName fn2 = new FirstName("HASSAN", 1);
        FirstName fn3 = new FirstName("JOHNNY", 2);

        List<FirstName> fnList = new ArrayList<>();
        fnList.add(fn1);
        fnList.add(fn3);
        fnList.add(fn2);

        String namesString = transformationService.transformFirstNamesToStringWithApostrophes(fnList);
        assertEquals("KADER'HASSAN'JOHNNY", namesString);
    }
}
