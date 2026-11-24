package io.casehub.connectors.lifecycle.deployment;

import io.casehub.connectors.lifecycle.ConnectorManifest;
import io.casehub.connectors.lifecycle.ConnectorManifestLoader;
import io.casehub.connectors.lifecycle.ConnectorRegistry;
import io.casehub.connectors.lifecycle.InMemoryCredentialFacade;
import io.casehub.connectors.lifecycle.ProviderCredentialFacade;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import org.jboss.jandex.AnnotationInstance;
import io.quarkus.deployment.annotations.BuildStep;
import jakarta.inject.Singleton;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;

import java.util.ArrayList;
import java.util.List;

public class ConnectorLifecycleProcessor {

    @BuildStep
    ManifestBuildItem discoverManifests() {
        var manifests = ConnectorManifestLoader.discoverFromClasspath();
        return new ManifestBuildItem(manifests);
    }

    @BuildStep
    SyntheticBeanBuildItem registerDefaultCredentialFacade() {
        return SyntheticBeanBuildItem.configure(ProviderCredentialFacade.class)
                .scope(Singleton.class)
                .defaultBean()
                .addType(ClassType.create(DotName.createSimple(ProviderCredentialFacade.class.getName())))
                .creator(DefaultFacadeCreator.class)
                .done();
    }

    @BuildStep
    List<SyntheticBeanBuildItem> registerSyntheticBeans(ManifestBuildItem manifestItem) {
        var result = new ArrayList<SyntheticBeanBuildItem>();
        for (var manifest : manifestItem.getManifests()) {
            var decl = manifest.connector();
            if (decl.produces() == null) continue;

            String[] fieldNames = extractFieldNames(decl);
            String[] fieldDefaults = extractFieldDefaults(decl);

            for (var bean : decl.produces()) {
                var configurator = SyntheticBeanBuildItem
                        .configure(DotName.createSimple(bean.className()))
                        .scope(Singleton.class)
                        .setRuntimeInit()
                        .creator(ConnectorBeanCreator.class)
                        .param("connectorId", decl.id())
                        .param("className", bean.className())
                        .param("fieldNames", fieldNames)
                        .param("fieldDefaults", fieldDefaults)
                        .addQualifier(AnnotationInstance.builder(
                                DotName.createSimple("jakarta.inject.Named"))
                                .add("value", decl.id()).build());

                configurator.addInjectionPoint(ClassType.create(
                        DotName.createSimple(ProviderCredentialFacade.class.getName())));

                if (bean.constructorArgs() != null && !bean.constructorArgs().isEmpty()) {
                    var kinds = new String[bean.constructorArgs().size()];
                    var names = new String[bean.constructorArgs().size()];

                    for (int i = 0; i < bean.constructorArgs().size(); i++) {
                        var arg = bean.constructorArgs().get(i);
                        if (arg.field() != null) {
                            kinds[i] = "field";
                            names[i] = arg.field();
                        } else {
                            kinds[i] = "bean";
                            names[i] = arg.bean();
                            configurator.addInjectionPoint(ClassType.create(
                                    DotName.createSimple(arg.bean())));
                        }
                    }

                    configurator.param("argKinds", kinds);
                    configurator.param("argNames", names);
                }

                result.add(configurator.done());
            }
        }
        return result;
    }

    @BuildStep
    SyntheticBeanBuildItem registerRegistry(ManifestBuildItem manifestItem) {
        return SyntheticBeanBuildItem.configure(ConnectorRegistry.class)
                .scope(Singleton.class)
                .setRuntimeInit()
                .creator(RegistryBeanCreator.class)
                .addInjectionPoint(ClassType.create(
                        DotName.createSimple(ProviderCredentialFacade.class.getName())))
                .done();
    }

    private static String[] extractFieldNames(ConnectorManifest.ConnectorDeclaration decl) {
        if (decl.auth() == null || decl.auth().fields() == null) return new String[0];
        return decl.auth().fields().stream()
                .map(ConnectorManifest.FieldDeclaration::name)
                .toArray(String[]::new);
    }

    private static String[] extractFieldDefaults(ConnectorManifest.ConnectorDeclaration decl) {
        if (decl.auth() == null || decl.auth().fields() == null) return new String[0];
        return decl.auth().fields().stream()
                .map(f -> f.defaultValue() != null ? f.defaultValue() : "")
                .toArray(String[]::new);
    }
}
