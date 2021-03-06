package com.azure.autorest.azurefunctions.template;


// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.


import com.azure.autorest.azurefunctions.extension.base.plugin.JavaSettings;
import com.azure.autorest.azurefunctions.model.clientmodel.ClassType;
import com.azure.autorest.azurefunctions.model.clientmodel.ClientMethod;
import com.azure.autorest.azurefunctions.model.clientmodel.IType;
import com.azure.autorest.azurefunctions.model.clientmodel.MethodGroupClient;
import com.azure.autorest.azurefunctions.model.javamodel.JavaFile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes a MethodGroupClient to a JavaFile.
 */
public class MethodGroupTemplate implements IJavaTemplate<MethodGroupClient, JavaFile> {
    private static MethodGroupTemplate _instance = new MethodGroupTemplate();

    private MethodGroupTemplate() {
    }

    public static MethodGroupTemplate getInstance() {
        return _instance;
    }

    public final void write(MethodGroupClient methodGroupClient, JavaFile javaFile) {
        JavaSettings settings = JavaSettings.getInstance();
        Set<String> imports = new HashSet<String>();
        methodGroupClient.addImportsTo(imports, true, settings);
        javaFile.declareImport(imports);

        List<String> interfaces = methodGroupClient.getSupportedInterfaces().stream()
                .map(IType::toString).collect(Collectors.toList());
        interfaces.addAll(methodGroupClient.getImplementedInterfaces());
        String parentDeclaration = !interfaces.isEmpty() ? String.format(" implements %1$s", String.join(", ", interfaces)) : "";

        javaFile.javadocComment(settings.getMaximumJavadocCommentWidth(), comment ->
        {
            comment.description(String.format("An instance of this class provides access to all the operations defined in %1$s.", methodGroupClient.getInterfaceName()));
        });
        javaFile.publicFinalClass(String.format("%1$s%2$s", methodGroupClient.getClassName(), parentDeclaration), classBlock ->
        {
            classBlock.javadocComment(String.format("The proxy service used to perform REST calls."));
            classBlock.privateFinalMemberVariable(methodGroupClient.getProxy().getName(), "service");

            classBlock.javadocComment("The service client containing this operation class.");
            classBlock.privateFinalMemberVariable(methodGroupClient.getServiceClientName(), "client");

            classBlock.javadocComment(comment ->
            {
                comment.description(String.format("Initializes an instance of %1$s.", methodGroupClient.getClassName()));
                comment.param("client", "the instance of the service client containing this operation class.");
            });
            classBlock.packagePrivateConstructor(String.format("%1$s(%2$s client)", methodGroupClient.getClassName(), methodGroupClient.getServiceClientName()), constructor ->
            {
                if (methodGroupClient.getProxy() != null) {
                    ClassType proxyType = ClassType.RestProxy;
                    if (settings.isFluent()) {
                        constructor.line(String.format("this.service = %1$s.create(%2$s.class, client.getHttpPipeline(), client.getSerializerAdapter());", proxyType.getName(), methodGroupClient.getProxy().getName()));
                    } else {
                        constructor.line(String.format("this.service = %1$s.create(%2$s.class, client.getHttpPipeline());", proxyType.getName(), methodGroupClient.getProxy().getName()));
                    }
                }
                constructor.line("this.client = client;");
            });

            Templates.getProxyTemplate().write(methodGroupClient.getProxy(), classBlock);

            for (ClientMethod clientMethod : methodGroupClient.getClientMethods()) {
                Templates.getClientMethodTemplate().write(clientMethod, classBlock);
            }
        });
    }
}
