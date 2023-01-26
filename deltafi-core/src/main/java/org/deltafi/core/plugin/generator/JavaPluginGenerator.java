/**
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.core.plugin.generator;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.text.CaseUtils;
import org.deltafi.common.types.ActionType;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Service
public class JavaPluginGenerator {

    private static final String ACTION_PARAM_PACKAGE = "org.deltafi.actionkit.action.parameters";
    private static final String ACTION_PARAM_CLASS = "ActionParameters";
    private static final String SRC_MAIN_JAVA = "/src/main/java/";
    private static final String SRC_TEST_JAVA = "/src/test/java/";
    private static final String JAVA_EXT = ".java";
    private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER = new PropertyPlaceholderHelper("{{", "}}", null, false);
    private final EnumMap<ActionType, String> actionTemplateMap;
    private final Map<String, String> topLevelTemplateMap;
    private final String applicationYamlTemplate;
    private final String testTemplate;
    private final String mainClassTemplate;
    private final String paramClassTemplate;

    private final ApplicationContext applicationContext;

    private final BuildProperties buildProperties;

    public JavaPluginGenerator(ApplicationContext applicationContext, BuildProperties buildProperties) {
        this.applicationContext = applicationContext;
        this.buildProperties = buildProperties;
        actionTemplateMap = new EnumMap<>(ActionType.class);
        topLevelTemplateMap = new HashMap<>();
        mainClassTemplate = readClassPathResource("plugin-templates/java/classTemplates/main-class.tpl");
        paramClassTemplate = readClassPathResource("plugin-templates/java/classTemplates/action-parameters.tpl");
        applicationYamlTemplate = readClassPathResource("plugin-templates/java/resourceTemplates/application.yaml.tpl");
        testTemplate = readClassPathResource("plugin-templates/java/classTemplates/action-test.tpl");

        populateClassTemplateMap();
        populateTopLevelTemplateMap();
    }

    public ByteArrayOutputStream generateProject(PluginGeneratorInput pluginGeneratorInput) throws IOException {
        // use the artifactId as the application name and the root directory for the project, special characters are stripped out
        String appName = normalizedAppName(pluginGeneratorInput.getArtifactId(), "-");

        // use the groupId as the base package for the project, special characters are stripped out
        String packageName = normalizedAppName(pluginGeneratorInput.getGroupId(), ".").toLowerCase();

        String packageDir = packageName.replace(".", "/") + "/";

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ArchiveOutputStream outputStream = new ZipArchiveOutputStream(byteArrayOutputStream);

        topLevelTemplateMap.forEach((filename, content) -> addTopLevelFile(pluginGeneratorInput, filename, content, appName, packageName, outputStream));
        addGradleWrapper(appName, outputStream);
        addSampleFlows(appName, outputStream);

        addMainClass(appName, packageName, packageDir, outputStream);
        addApplicationYaml(appName, outputStream);
        pluginGeneratorInput.getActions().forEach(actionGeneratorInput -> addActionClasses(appName, packageDir, packageName, actionGeneratorInput, outputStream));

        outputStream.finish();
        return byteArrayOutputStream;
    }

    private void addActionClasses(String rootDirectory,  String packageDir, String packageName, ActionGeneratorInput actionGeneratorInput, ArchiveOutputStream outputStream) {
        try {
            Properties props = new Properties();
            props.put("package", packageName + ".actions");
            props.put("className", actionGeneratorInput.getClassName());
            props.put("paramPackage", ACTION_PARAM_PACKAGE);
            props.put("paramClassName", ACTION_PARAM_CLASS);
            props.put("description", actionGeneratorInput.getDescription());

            if (actionGeneratorInput.getParameterClassName() != null) {
                String paramPackage = packageName + ".parameters";

                props.put("paramPackage", paramPackage);
                props.put("paramClassName", actionGeneratorInput.getParameterClassName());
                String filePath = rootDirectory + SRC_MAIN_JAVA + packageDir + "parameters/" + actionGeneratorInput.getParameterClassName() + JAVA_EXT;
                addToArchiveFromTemplate(props, paramClassTemplate, filePath, outputStream);
            }

            String mainPath = rootDirectory + SRC_MAIN_JAVA + packageDir + "actions/" + actionGeneratorInput.getClassName() + JAVA_EXT;
            addToArchiveFromTemplate(props, actionTemplateMap.get(actionGeneratorInput.getActionType()), mainPath, outputStream);

            String testPath = rootDirectory + SRC_TEST_JAVA + packageDir + "actions/" + actionGeneratorInput.getClassName() + "Test" + JAVA_EXT;
            addToArchiveFromTemplate(props, testTemplate, testPath, outputStream);
        } catch (IOException ioException) {
            log.error("Failed to create the plugin", ioException);
            throw new RuntimeException(ioException);
        }
    }

    void addTopLevelFile(PluginGeneratorInput pluginGeneratorInput, String filename, String content, String appName, String packageName, ArchiveOutputStream archiveOutputStream) {
        try {
            Properties props = new Properties();
            props.put("packageName", packageName);
            props.put("projectName", appName);
            props.put("groupId", packageName);
            props.put("artifactId", appName);
            props.put("description", pluginGeneratorInput.getDescription());
            props.put("deltafiVersion", buildProperties.getVersion());

            if (filename.endsWith(".tpl")) {
                filename = filename.substring(0, filename.length() - 4);
                content = PLACEHOLDER_HELPER.replacePlaceholders(content, props);
            }

            addFileToArchive(appName + "/" + filename, content, archiveOutputStream);
        } catch (IOException ioException) {
            log.error("Failed to add entry to the archive", ioException);
            throw new RuntimeException("Failed to add entry to the archive", ioException);
        }
    }

    void addMainClass(String appName, String packageName, String packagePath, ArchiveOutputStream archiveOutputStream) throws IOException {
        String mainClassName = getMainClassName(appName);

        Properties props = new Properties();
        props.put("package", packageName);
        props.put("mainAppName", mainClassName);

        String fullPath = appName + SRC_MAIN_JAVA + packagePath + mainClassName + JAVA_EXT;
        addToArchiveFromTemplate(props, mainClassTemplate, fullPath, archiveOutputStream);
    }

    void addApplicationYaml(String appName, ArchiveOutputStream archiveOutputStream) throws IOException {
        Properties props = new Properties();
        props.put("applicationName", appName);
        addToArchiveFromTemplate(props, applicationYamlTemplate, appName + "/src/main/resources/application.yaml", archiveOutputStream);
    }

    void addGradleWrapper(String rootDir, ArchiveOutputStream archiveOutputStream) throws IOException {
        addFileToArchive(rootDir + "/gradle/wrapper/gradle-wrapper.jar", readClassPathResourceBytes("plugin-templates/java/gradleWrapper/gradle-wrapper.jar"), archiveOutputStream);
        addFileToArchive(rootDir + "/gradle/wrapper/gradle-wrapper.properties", readClassPathResourceBytes("plugin-templates/java/gradleWrapper/gradle-wrapper.properties"), archiveOutputStream);
    }

    void addSampleFlows(String rootDir, ArchiveOutputStream archiveOutputStream) throws IOException {
        String basePath = rootDir + "/src/main/resources/flows/";
        Resource[] topLevelFiles = applicationContext.getResources("classpath:plugin-templates/flows/*");
        for (Resource resource : topLevelFiles) {
            addFileToArchive(basePath + resource.getFilename(), resource.getInputStream().readAllBytes(), archiveOutputStream);
        }
    }

    void addToArchiveFromTemplate(Properties properties, String template, String path, ArchiveOutputStream archiveOutputStream) throws IOException {
        String content = PLACEHOLDER_HELPER.replacePlaceholders(template, properties);
        addFileToArchive(path, content, archiveOutputStream);
    }

    void addFileToArchive(String fullPath, String content, ArchiveOutputStream archiveOutputStream) throws IOException {
        addFileToArchive(fullPath, content.getBytes(StandardCharsets.UTF_8), archiveOutputStream);
    }

    void addFileToArchive(String fullPath, byte[] content, ArchiveOutputStream archiveOutputStream) throws IOException {
        ZipArchiveEntry zipArchiveEntry = new ZipArchiveEntry(fullPath);
        zipArchiveEntry.setUnixMode(UnixStat.FILE_FLAG | (fullPath.endsWith("gradlew") ? 0755 : UnixStat.DEFAULT_FILE_PERM));
        archiveOutputStream.putArchiveEntry(zipArchiveEntry);
        archiveOutputStream.write(content);
        archiveOutputStream.closeArchiveEntry();
    }

    private String readClassPathResource(String path) {
        return new String(readClassPathResourceBytes(path));
    }

    private byte[] readClassPathResourceBytes(String path) {
        Resource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return is.readAllBytes();
        } catch (IOException ioException) {
            log.error("Path: {} could not be found", path, ioException);
            throw new IllegalArgumentException("Path " + path + " could not be found");
        }
    }

    private String normalizedAppName(String appName, String replacement) {
        // treat all consecutive spaces and special characters as breakpoints between words, separate words with the replacement character
        return appName.replaceAll("\\s+|\\W+", replacement).toLowerCase();
    }

    private String getMainClassName(String appName) {
        return CaseUtils.toCamelCase(appName, true, '-');
    }

    private void populateClassTemplateMap() {
        actionTemplateMap.put(ActionType.TRANSFORM, readClassPathResource("plugin-templates/java/classTemplates/transform-action.tpl"));
        actionTemplateMap.put(ActionType.LOAD, readClassPathResource("plugin-templates/java/classTemplates/load-action.tpl"));
        actionTemplateMap.put(ActionType.DOMAIN, readClassPathResource("plugin-templates/java/classTemplates/domain-action.tpl"));
        actionTemplateMap.put(ActionType.ENRICH, readClassPathResource("plugin-templates/java/classTemplates/enrich-action.tpl"));
        actionTemplateMap.put(ActionType.FORMAT, readClassPathResource("plugin-templates/java/classTemplates/format-action.tpl"));
        actionTemplateMap.put(ActionType.VALIDATE, readClassPathResource("plugin-templates/java/classTemplates/validate-action.tpl"));
        actionTemplateMap.put(ActionType.EGRESS, readClassPathResource("plugin-templates/java/classTemplates/egress-action.tpl"));
    }

    private void populateTopLevelTemplateMap() {
        try {
            Resource[] topLevelFiles = applicationContext.getResources("classpath:plugin-templates/java/topLevel/*");
            for (Resource resource : topLevelFiles) {
                topLevelTemplateMap.put(resource.getFilename(), new String(resource.getInputStream().readAllBytes()));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the java plugin template files", e);
        }
    }
}
