package net.minestom.codegen.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.squareup.javapoet.*;
import net.minestom.codegen.MinestomCodeGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collections;

public final class ParticleGenerator extends MinestomCodeGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParticleGenerator.class);
    private final File particlesFile;
    private final File outputFolder;

    public ParticleGenerator(@NotNull File particlesFile, @NotNull File outputFolder) {
        this.particlesFile = particlesFile;
        this.outputFolder = outputFolder;
    }

    @Override
    public void generate() {
        if (!particlesFile.exists()) {
            LOGGER.error("Failed to find particles.json.");
            LOGGER.error("Stopped code generation for particles.");
            return;
        }
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            LOGGER.error("Output folder for code generation does not exist and could not be created.");
            return;
        }
        // Important classes we use alot
        ClassName namespaceIDClassName = ClassName.get("net.minestom.server.utils", "NamespaceID");
        ClassName registriesClassName = ClassName.get("net.minestom.server.registry", "Registries");

        JsonArray particles;
        try {
            particles = GSON.fromJson(new JsonReader(new FileReader(particlesFile)), JsonArray.class);
        } catch (FileNotFoundException e) {
            LOGGER.error("Failed to find particles.json.");
            LOGGER.error("Stopped code generation for particles.");
            return;
        }
        ClassName particleClassName = ClassName.get("net.minestom.server.particle", "Particle");

        // Particle
        TypeSpec.Builder particleClass = TypeSpec.enumBuilder(particleClassName)
                .addSuperinterface(ClassName.get("net.kyori.adventure.key", "Keyed"))
                .addModifiers(Modifier.PUBLIC).addJavadoc("AUTOGENERATED by " + getClass().getSimpleName());

        particleClass.addField(
                FieldSpec.builder(namespaceIDClassName, "id")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL).addAnnotation(NotNull.class).build()
        );
        // static field
        particleClass.addField(
                FieldSpec.builder(ArrayTypeName.of(particleClassName), "VALUES")
                        .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                        .initializer("values()")
                        .build()
        );

        particleClass.addMethod(
                MethodSpec.constructorBuilder()
                        .addParameter(ParameterSpec.builder(namespaceIDClassName, "id").addAnnotation(NotNull.class).build())
                        .addStatement("this.id = id")
                        .addStatement("$T.particles.put(id, this)", registriesClassName)
                        .build()
        );
        // Override key method (adventure)
        particleClass.addMethod(
                MethodSpec.methodBuilder("key")
                        .returns(ClassName.get("net.kyori.adventure.key", "Key"))
                        .addAnnotation(Override.class)
                        .addAnnotation(NotNull.class)
                        .addStatement("return this.id")
                        .addModifiers(Modifier.PUBLIC)
                        .build()
        );
        // getId method
        particleClass.addMethod(
                MethodSpec.methodBuilder("getId")
                        .returns(TypeName.SHORT)
                        .addStatement("return (short) ordinal()")
                        .addModifiers(Modifier.PUBLIC)
                        .build()
        );
        // getNamespaceID method
        particleClass.addMethod(
                MethodSpec.methodBuilder("getNamespaceID")
                        .returns(namespaceIDClassName)
                        .addAnnotation(NotNull.class)
                        .addStatement("return this.id")
                        .addModifiers(Modifier.PUBLIC)
                        .build()
        );
        // fromId Method
        particleClass.addMethod(
                MethodSpec.methodBuilder("fromId")
                        .returns(particleClassName)
                        .addAnnotation(Nullable.class)
                        .addParameter(TypeName.SHORT, "id")
                        .beginControlFlow("if(id >= 0 && id < VALUES.length)")
                        .addStatement("return VALUES[id]")
                        .endControlFlow()
                        .addStatement("return null")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .build()
        );
        // toString method
        particleClass.addMethod(
                MethodSpec.methodBuilder("toString")
                        .addAnnotation(NotNull.class)
                        .addAnnotation(Override.class)
                        .returns(String.class)
                        // this resolves to [Namespace]
                        .addStatement("return \"[\" + this.id + \"]\"")
                        .addModifiers(Modifier.PUBLIC)
                        .build()
        );

        // Use data
        for (JsonElement p : particles) {
            JsonObject particle = p.getAsJsonObject();

            String particleName = particle.get("name").getAsString();

            particleClass.addEnumConstant(particleName, TypeSpec.anonymousClassBuilder(
                    "$T.from($S)",
                    namespaceIDClassName,
                    particle.get("id").getAsString()
            ).build());
        }

        // Write files to outputFolder
        writeFiles(
                Collections.singletonList(
                        JavaFile.builder("net.minestom.server.particle", particleClass.build())
                                .indent("    ")
                                .skipJavaLangImports(true)
                                .build()
                ),
                outputFolder
        );
    }
}