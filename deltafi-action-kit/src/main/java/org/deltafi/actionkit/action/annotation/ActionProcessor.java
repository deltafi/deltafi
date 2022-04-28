package org.deltafi.actionkit.action.annotation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.deltafi.core.domain.generated.types.ActionDescriptor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Writes an actions.json file at the top level of the project build directory containing information for classes
 * annotated with {@link org.deltafi.actionkit.action.annotation.Action}.
 */
public class ActionProcessor extends AbstractProcessor {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Collection<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(Action.class);
        List<TypeElement> types = ElementFilter.typesIn(annotatedElements);

        List<ActionDescriptor> actionDescriptors = types.stream()
                .map(type -> {
                    Action actionAnnotation = type.getAnnotation(Action.class);
                    return ActionDescriptor.newBuilder()
                            .name(type.getQualifiedName().toString())
                            .consumes(actionAnnotation.consumes())
                            .produces(actionAnnotation.produces())
                            .requiresDomains(Arrays.asList(actionAnnotation.requiresDomains()))
                            .build();
                })
                .sorted(Comparator.comparing(ActionDescriptor::getName))
                .collect(Collectors.toList());

        try {
            OBJECT_MAPPER.writeValue(new File(getBuildDirectory(), "actions.json"), actionDescriptors);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "Unable to write actions.json: " + e.getMessage());
        }

        return true;
    }

    private String getBuildDirectory() throws IOException {
        FileObject fileObject = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "X");
        String buildDirectory = fileObject.toUri().getPath();
        return buildDirectory.substring(0, buildDirectory.length() - "/classes/java/main/X".length());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_11;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Action.class.getName());
    }
}
