package com.azure.autorest.azurefunctions.mapper;

import com.azure.autorest.azurefunctions.extension.base.plugin.JavaSettings;
import com.azure.autorest.azurefunctions.util.CodeNamer;
import com.azure.autorest.azurefunctions.util.SchemaUtil;
import com.azure.autorest.azurefunctions.extension.base.model.codemodel.ConstantSchema;
import com.azure.autorest.azurefunctions.extension.base.model.codemodel.ObjectSchema;
import com.azure.autorest.azurefunctions.extension.base.model.codemodel.Operation;
import com.azure.autorest.azurefunctions.extension.base.model.codemodel.Parameter;
import com.azure.autorest.azurefunctions.extension.base.model.codemodel.Request;
import com.azure.autorest.azurefunctions.extension.base.model.codemodel.Response;
import com.azure.autorest.azurefunctions.extension.base.model.codemodel.Schema;
import com.azure.autorest.azurefunctions.model.clientmodel.ClassType;
import com.azure.autorest.azurefunctions.model.clientmodel.ClientMethod;
import com.azure.autorest.azurefunctions.model.clientmodel.ClientMethod.Builder;
import com.azure.autorest.azurefunctions.model.clientmodel.ClientMethodParameter;
import com.azure.autorest.azurefunctions.model.clientmodel.ClientMethodType;
import com.azure.autorest.azurefunctions.model.clientmodel.ClientModel;
import com.azure.autorest.azurefunctions.model.clientmodel.ClientModelProperty;
import com.azure.autorest.azurefunctions.model.clientmodel.GenericType;
import com.azure.autorest.azurefunctions.model.clientmodel.IType;
import com.azure.autorest.azurefunctions.model.clientmodel.ListType;
import com.azure.autorest.azurefunctions.model.clientmodel.MethodPageDetails;
import com.azure.autorest.azurefunctions.model.clientmodel.MethodTransformationDetail;
import com.azure.autorest.azurefunctions.model.clientmodel.ParameterMapping;
import com.azure.autorest.azurefunctions.model.clientmodel.PrimitiveType;
import com.azure.autorest.azurefunctions.model.clientmodel.ProxyMethod;
import com.azure.autorest.azurefunctions.model.clientmodel.ProxyMethodParameter;
import com.azure.autorest.azurefunctions.model.clientmodel.ReturnValue;
import com.azure.core.http.HttpMethod;
import com.azure.core.util.CoreUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ClientMethodMapper implements IMapper<Operation, List<ClientMethod>> {
    private static ClientMethodMapper instance = new ClientMethodMapper();
    private Map<Operation, List<ClientMethod>> parsed = new HashMap<>();

    private ClientMethodMapper() {
    }

    public static ClientMethodMapper getInstance() {
        return instance;
    }

    @Override
    public List<ClientMethod> map(Operation operation) {
        JavaSettings settings = JavaSettings.getInstance();
        if (parsed.containsKey(operation)) {
            return parsed.get(operation);
        }

        Map<Request, ProxyMethod> proxyMethods = Mappers.getProxyMethodMapper().map(operation);

        List<ClientMethod> methods = new ArrayList<>();

        ClientMethod.Builder builder = new ClientMethod.Builder()
                .description(operation.getLanguage().getJava().getDescription())
                .clientReference((operation.getOperationGroup() == null || operation.getOperationGroup().getLanguage().getJava().getName().isEmpty()) ? "this": "this.client");

        IType asyncRestResponseReturnType;
        IType asyncReturnType;
        IType syncReturnType;

        if (operation.getExtensions() != null && operation.getExtensions().getXmsPageable() != null) {
            // Mono<SimpleResponse<Page>>
            Schema responseBodySchema = SchemaUtil.getLowestCommonParent(
                    operation.getResponses().stream().map(Response::getSchema).filter(Objects::nonNull).collect(Collectors.toList()));
            ClientModel responseBodyModel = Mappers.getModelMapper().map((ObjectSchema) responseBodySchema);
            IType listType = responseBodyModel.getProperties().stream()
                    .filter(p -> p.getSerializedName().equals(operation.getExtensions().getXmsPageable().getItemName()))
                    .findFirst().get().getWireType();
            IType elementType = ((ListType) listType).getElementType();
            asyncRestResponseReturnType = GenericType.Mono(GenericType.PagedResponse(elementType));
            asyncReturnType = GenericType.PagedFlux(elementType);
            syncReturnType = GenericType.PagedIterable(elementType);
        } else {
            asyncRestResponseReturnType = null;
            IType responseBodyType = SchemaUtil.operationResponseType(operation);
            IType restAPIMethodReturnBodyClientType = responseBodyType.getClientType();
            if (operation.getResponses().stream().anyMatch(r -> Boolean.TRUE.equals(r.getBinary()))) {
                asyncReturnType = GenericType.Flux(ClassType.ByteBuffer);
            } else if (restAPIMethodReturnBodyClientType != PrimitiveType.Void) {
                asyncReturnType = GenericType.Mono(restAPIMethodReturnBodyClientType);
            } else {
                asyncReturnType = GenericType.Mono(ClassType.Void);
            }
            if (operation.getResponses().stream().anyMatch(r -> Boolean.TRUE.equals(r.getBinary()))) {
                syncReturnType = ClassType.InputStream;
            } else {
                syncReturnType = responseBodyType.getClientType();
            }
        }

        for (Request request : operation.getRequests()) {
            ProxyMethod proxyMethod = proxyMethods.get(request);
            builder.proxyMethod(proxyMethod);

            List<ClientMethodParameter> parameters = new ArrayList<>();
            List<String> requiredParameterExpressions = new ArrayList<>();
            Map<String, String> validateExpressions = new HashMap<>();
            List<MethodTransformationDetail> methodTransformationDetails = new ArrayList<>();

            for (Parameter parameter : request.getParameters().stream().filter(p -> !p.isFlattened()).collect(Collectors.toList())) {
                ClientMethodParameter clientMethodParameter = Mappers.getClientParameterMapper().map(parameter);
                if (request.getSignatureParameters().contains(parameter)) {
                    parameters.add(clientMethodParameter);
                }

                if (!(parameter.getSchema() instanceof ConstantSchema) && parameter.getGroupedBy() == null) {
                    if (parameter.getImplementation() != Parameter.ImplementationLocation.CLIENT) {
                        // Validations
                        if (clientMethodParameter.getIsRequired() && !(clientMethodParameter.getClientType() instanceof PrimitiveType)) {
                            requiredParameterExpressions.add(clientMethodParameter.getName());
                        }
                        String validation = clientMethodParameter.getClientType().validate(clientMethodParameter.getName());
                        if (validation != null) {
                            validateExpressions.put(clientMethodParameter.getName(), validation);
                        }
                    } else {
                        ProxyMethodParameter proxyParameter = Mappers.getProxyParameterMapper().map(parameter);
                        String exp = proxyParameter.getParameterReference();

                        if (proxyParameter.getIsRequired() && !(proxyParameter.getClientType() instanceof PrimitiveType)) {
                            requiredParameterExpressions.add(exp);
                        }

                        String validation = proxyParameter.getClientType().validate(exp);
                        if (validation != null) {
                            validateExpressions.put(exp, validation);
                        }
                    }
                }

                // Transformations
                if ((parameter.getOriginalParameter() != null || parameter.getGroupedBy() != null)
                        && !(parameter.getSchema() instanceof ConstantSchema)) {
                    ClientMethodParameter outParameter;
                    if (parameter.getOriginalParameter() != null) {
                        outParameter = Mappers.getClientParameterMapper().map(parameter.getOriginalParameter());
                    } else {
                        outParameter = clientMethodParameter;
                    }
                    MethodTransformationDetail detail = methodTransformationDetails.stream()
                            .filter(d -> outParameter.getName().equals(d.getOutParameter().getName()))
                            .findFirst().orElse(null);
                    if (detail == null) {
                        detail = new MethodTransformationDetail(outParameter, new ArrayList<>());
                        methodTransformationDetails.add(detail);
                    }
                    ParameterMapping mapping = new ParameterMapping();
                    if (parameter.getGroupedBy() != null) {
                        mapping.setInputParameter(Mappers.getClientParameterMapper().map(parameter.getGroupedBy()));
                        ClientModel groupModel = Mappers.getModelMapper().map((ObjectSchema) parameter.getGroupedBy().getSchema());
                        ClientModelProperty inputProperty = groupModel.getProperties().stream()
                                .filter(p -> parameter.getLanguage().getJava().getName().equals(p.getName()))
                                .findFirst().get();
                        mapping.setInputParameterProperty(inputProperty);
                    } else {
                        mapping.setInputParameter(clientMethodParameter);
                    }
                    if (parameter.getOriginalParameter() != null) {
                        mapping.setOutputParameterProperty(parameter.getTargetProperty().getLanguage().getJava().getName());
                    }
                    detail.getParameterMappings().add(mapping);
                }
            }

            final boolean generateClientMethodWithOnlyRequiredParameters = settings.getRequiredParameterClientMethods() && hasNonRequiredParameters(request);

            builder.parameters(parameters)
                    .requiredNullableParameterExpressions(requiredParameterExpressions)
                    .validateExpressions(validateExpressions)
                    .methodTransformationDetails(methodTransformationDetails)
                    .methodPageDetails(null);

            if (operation.getExtensions() != null && operation.getExtensions().getXmsPageable() != null && isPageable(operation)) {
                boolean isNextMethod = operation.getExtensions().getXmsPageable().getNextOperation() == operation;

                MethodPageDetails details = new MethodPageDetails(
                        CodeNamer.getPropertyName(operation.getExtensions().getXmsPageable().getNextLinkName()),
                        CodeNamer.getPropertyName(operation.getExtensions().getXmsPageable().getItemName()),
                        (isNextMethod || operation.getExtensions().getXmsPageable().getNextOperation() == null) ? null : Mappers.getClientMethodMapper().map(operation.getExtensions().getXmsPageable().getNextOperation())
                                .stream().findFirst().get());
                builder.methodPageDetails(details);

                methods.add(builder
                        .returnValue(new ReturnValue(returnTypeDescription(operation, asyncRestResponseReturnType, syncReturnType),
                                asyncRestResponseReturnType))
                        .name(proxyMethod.getPagingAsyncSinglePageMethodName())
                        .onlyRequiredParameters(false)
                        .type(ClientMethodType.PagingAsyncSinglePage)
                        .isGroupedParameterRequired(false)
                        .build());
                if (settings.isContextClientMethodParameter()) {
                    addClientMethodWithContext(methods, builder, proxyMethod, parameters,
                        ClientMethodType.PagingAsyncSinglePage, proxyMethod.getPagingAsyncSinglePageMethodName(),
                        new ReturnValue(returnTypeDescription(operation, asyncRestResponseReturnType, syncReturnType),
                            asyncRestResponseReturnType));
                }

                if (!isNextMethod) {
                    if (settings.getSyncMethods() != JavaSettings.SyncMethodsGeneration.NONE) {
                        methods.add(builder
                                .returnValue(new ReturnValue(returnTypeDescription(operation, asyncReturnType, syncReturnType),
                                        asyncReturnType))
                                .name(proxyMethod.getSimpleAsyncMethodName())
                                .onlyRequiredParameters(false)
                                .type(ClientMethodType.PagingAsync)
                                .isGroupedParameterRequired(false)
                                .build());
                        if (settings.isContextClientMethodParameter()) {
                            addClientMethodWithContext(methods, builder, proxyMethod, parameters,
                                ClientMethodType.PagingAsync, proxyMethod.getSimpleAsyncMethodName(),
                                new ReturnValue(returnTypeDescription(operation, asyncReturnType, syncReturnType),
                                    asyncReturnType));
                        }

                        if (generateClientMethodWithOnlyRequiredParameters) {
                            methods.add(builder
                                    .onlyRequiredParameters(true)
                                    .build());
                        }
                    }

                    if (settings.getSyncMethods() == JavaSettings.SyncMethodsGeneration.ALL) {
                        methods.add(builder
                                .returnValue(new ReturnValue(returnTypeDescription(operation, syncReturnType, syncReturnType),
                                        syncReturnType))
                                .name(proxyMethod.getName())
                                .onlyRequiredParameters(false)
                                .type(ClientMethodType.PagingSync)
                                .isGroupedParameterRequired(false)
                                .build());

                        if (generateClientMethodWithOnlyRequiredParameters) {
                            methods.add(builder
                                    .onlyRequiredParameters(true)
                                    .build());
                        }
                    }
                }
            } else if (operation.getExtensions() != null && operation.getExtensions().isXmsLongRunningOperation() && settings.isFluent()) {
                // WithResponseAsync, with required and optional parameters
                methods.add(builder
                        .returnValue(new ReturnValue(returnTypeDescription(operation, proxyMethod.getReturnType().getClientType(), syncReturnType),
                                proxyMethod.getReturnType().getClientType()))
                        .name(proxyMethod.getSimpleAsyncRestResponseMethodName())
                        .onlyRequiredParameters(false)
                        .type(ClientMethodType.SimpleAsyncRestResponse)
                        .isGroupedParameterRequired(false)
                        .build());

                if (settings.getSyncMethods() != JavaSettings.SyncMethodsGeneration.NONE) {
                    methods.add(builder
                            .returnValue(new ReturnValue(returnTypeDescription(operation, asyncReturnType, syncReturnType),
                                    asyncReturnType))
                            .name(proxyMethod.getSimpleAsyncMethodName())
                            .onlyRequiredParameters(false)
                            .type(ClientMethodType.LongRunningAsync)
                            .isGroupedParameterRequired(false)
                            .build());

                    if (generateClientMethodWithOnlyRequiredParameters) {
                        methods.add(builder
                                .onlyRequiredParameters(true)
                                .build());
                    }
                }

                if (settings.getSyncMethods() == JavaSettings.SyncMethodsGeneration.ALL) {
                    methods.add(builder
                            .returnValue(new ReturnValue(returnTypeDescription(operation, syncReturnType, syncReturnType),
                                    syncReturnType))
                            .name(proxyMethod.getName())
                            .onlyRequiredParameters(false)
                            .type(ClientMethodType.LongRunningSync)
                            .isGroupedParameterRequired(false)
                            .build());

                    if (generateClientMethodWithOnlyRequiredParameters) {
                        methods.add(builder
                                .onlyRequiredParameters(true)
                                .build());
                    }
                }
            } else {
                // WithResponseAsync, with required and optional parameters
                methods.add(builder
                    .parameters(parameters) // update builder parameters to include context
                    .returnValue(new ReturnValue(returnTypeDescription(operation, proxyMethod.getReturnType().getClientType(), syncReturnType),
                            proxyMethod.getReturnType().getClientType()))
                    .name(proxyMethod.getSimpleAsyncRestResponseMethodName())
                    .onlyRequiredParameters(false)
                    .type(ClientMethodType.SimpleAsyncRestResponse)
                    .isGroupedParameterRequired(false)
                    .build());

                if (settings.isContextClientMethodParameter()) {
                    addClientMethodWithContext(methods, builder, proxyMethod, parameters,
                        ClientMethodType.SimpleAsyncRestResponse, proxyMethod.getSimpleAsyncRestResponseMethodName(),
                        new ReturnValue(returnTypeDescription(operation, proxyMethod.getReturnType().getClientType(),
                            syncReturnType), proxyMethod.getReturnType().getClientType()));
                }

                if (settings.getSyncMethods() != JavaSettings.SyncMethodsGeneration.NONE) {
                    methods.add(builder
                            .returnValue(new ReturnValue(returnTypeDescription(operation, asyncReturnType, syncReturnType),
                                    asyncReturnType))
                            .name(proxyMethod.getSimpleAsyncMethodName())
                            .onlyRequiredParameters(false)
                            .type(ClientMethodType.SimpleAsync)
                            .isGroupedParameterRequired(false)
                            .build());

                    if (generateClientMethodWithOnlyRequiredParameters) {
                        methods.add(builder
                                .onlyRequiredParameters(true)
                                .build());
                    }
                }

                if (settings.getSyncMethods() == JavaSettings.SyncMethodsGeneration.ALL) {
                    methods.add(builder
                            .returnValue(new ReturnValue(returnTypeDescription(operation, syncReturnType, syncReturnType),
                                    syncReturnType))
                            .name(proxyMethod.getName())
                            .onlyRequiredParameters(false)
                            .type(ClientMethodType.SimpleSync)
                            .isGroupedParameterRequired(false)
                            .build());

                    if (generateClientMethodWithOnlyRequiredParameters) {
                        methods.add(builder
                                .onlyRequiredParameters(true)
                                .build());
                    }
                }
            }
        }

        parsed.put(operation, methods);
        return methods;
    }

    private void addClientMethodWithContext(List<ClientMethod> methods, Builder builder, ProxyMethod proxyMethod,
        List<ClientMethodParameter> parameters, ClientMethodType clientMethodType, String proxyMethodName,
        ReturnValue returnValue) {
        ClientMethodParameter contextParam = new ClientMethodParameter.Builder()
            .description("The context to associate with this operation.")
            .wireType(ClassType.Context)
            .name("context")
            .annotations(new ArrayList<>())
            .isConstant(false)
            .defaultValue(null)
            .fromClient(false)
            .isFinal(false)
            .isRequired(true)
            .build();

        List<ClientMethodParameter> withContextParameters = new ArrayList<>();
        withContextParameters.addAll(parameters);
        withContextParameters.add(contextParam);

        methods.add(builder
            .parameters(withContextParameters) // update builder parameters to include context
            .returnValue(returnValue)
            .name(proxyMethodName)
            .onlyRequiredParameters(false)
            .type(clientMethodType)
            .isGroupedParameterRequired(false)
            .build());
        // reset the parameters to original params
        builder.parameters(parameters);
    }

    private boolean isPageable(Operation operation) {
        Schema responseBodySchema = SchemaUtil.getLowestCommonParent(
            operation.getResponses().stream().map(Response::getSchema).filter(Objects::nonNull).collect(Collectors.toList()));
        ClientModel responseBodyModel = Mappers.getModelMapper().map((ObjectSchema) responseBodySchema);
        return responseBodyModel.getProperties().stream()
            .anyMatch(p -> p.getSerializedName().equals(operation.getExtensions().getXmsPageable().getItemName()));
    }

    private static boolean hasNonRequiredParameters(Request request) {
        return request.getParameters().stream().anyMatch(p -> p.getImplementation() == Parameter.ImplementationLocation.METHOD && !p.isRequired() && !(p.getSchema() instanceof ConstantSchema))
                && request.getParameters().stream().noneMatch(Parameter::isFlattened);   // for now, ignore operation with flattened parameters
    }

    private static String returnTypeDescription(Operation operation, IType returnType, IType baseType) {
        String description = null;
        if (returnType != PrimitiveType.Void) {
            if (operation.getLanguage() != null && operation.getLanguage().getDefault() != null) {
                String operationDescription = operation.getLanguage().getDefault().getDescription();
                if (!CoreUtils.isNullOrEmpty(operationDescription)) {
                    if (operationDescription.toLowerCase().startsWith("get ") || operationDescription.toLowerCase().startsWith("gets ")) {
                        int startIndex = operationDescription.indexOf(" ") + 1;
                        description = formatReturnTypeDescription(operationDescription.substring(startIndex));
                    }
                }
            }

            if (description == null && operation.getResponses() != null && !operation.getResponses().isEmpty()) {
                Schema responseSchema = operation.getResponses().get(0).getSchema();
                if (responseSchema != null && responseSchema.getLanguage() != null && responseSchema.getLanguage().getDefault() != null) {
                    String responseSchemaDescription = responseSchema.getLanguage().getDefault().getDescription();
                    if (!CoreUtils.isNullOrEmpty(responseSchemaDescription)) {
                        description = formatReturnTypeDescription(responseSchemaDescription);
                    }
                }
            }

            if (description == null) {
                if (baseType == PrimitiveType.Void) {
                    // Mono<Void>
                    description = "the completion";
                }
                if (baseType == PrimitiveType.Boolean
                        && operation.getRequests() != null && !operation.getRequests().isEmpty()
                        && operation.getRequests().get(0).getProtocol() != null
                        && operation.getRequests().get(0).getProtocol().getHttp() != null
                        && HttpMethod.HEAD.name().equalsIgnoreCase(operation.getRequests().get(0).getProtocol().getHttp().getMethod())) {
                    // Mono<Boolean> of HEAD method
                    description = "whether resource exists";
                }
            }

            if (description == null) {
                description = "the response";
            }
        }
        return description;
    }

    private static String formatReturnTypeDescription(String description) {
        description = description.trim();
        int endIndex = description.indexOf(".");
        if (endIndex != -1) {
            description = description.substring(0, endIndex);
        }
        if (description.length() > 0 && Character.isUpperCase(description.charAt(0))) {
            description = description.substring(0, 1).toLowerCase() + description.substring(1);
        }
        return description;
    }
}
