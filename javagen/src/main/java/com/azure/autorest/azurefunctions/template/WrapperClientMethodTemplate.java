package com.azure.autorest.azurefunctions.template;

import com.azure.autorest.azurefunctions.extension.base.plugin.JavaSettings;
import com.azure.autorest.azurefunctions.model.clientmodel.ClassType;
import com.azure.autorest.azurefunctions.model.clientmodel.ClientMethod;
import com.azure.autorest.azurefunctions.model.clientmodel.ClientMethodParameter;
import com.azure.autorest.azurefunctions.model.clientmodel.PrimitiveType;
import com.azure.autorest.azurefunctions.model.clientmodel.ProxyMethod;
import com.azure.autorest.azurefunctions.model.javamodel.JavaType;
import java.util.List;

/**
 * Template to generate client methods that are wrappers around the client methods generated by
 * {@link ClientMethodTemplate}.
 *
 */
public class WrapperClientMethodTemplate implements IJavaTemplate<ClientMethod, JavaType> {

  private static final WrapperClientMethodTemplate instance = new WrapperClientMethodTemplate();
  private WrapperClientMethodTemplate() {
  }

  public static WrapperClientMethodTemplate getInstance() {
    return instance;
  }

  @Override
  public void write(ClientMethod clientMethod, JavaType typeBlock) {
    JavaSettings settings = JavaSettings.getInstance();

    ProxyMethod restAPIMethod = clientMethod.getProxyMethod();
    generateJavadoc(clientMethod, typeBlock, restAPIMethod);

    switch (clientMethod.getType()) {
      case PagingSync:
      case PagingAsync:
      case PagingAsyncSinglePage:
        typeBlock.annotation("ServiceMethod(returns = ReturnType.COLLECTION)");
        break;
      case LongRunningAsync:
      case SimpleSync:
      case LongRunningSync:
      case SimpleAsyncRestResponse:
      case Resumable:
      case SimpleAsync:
        typeBlock.annotation("ServiceMethod(returns = ReturnType.SINGLE)");
        break;
    }
    typeBlock.publicMethod(clientMethod.getDeclaration(), function -> {

      boolean shouldReturn = true;
      if (clientMethod.getReturnValue() != null && clientMethod.getReturnValue().getType() instanceof PrimitiveType) {
        PrimitiveType type = (PrimitiveType) clientMethod.getReturnValue().getType();
        if (type.asNullable() == ClassType.Void) {
          shouldReturn = false;
        }
      }
      function
          .line((shouldReturn ? "return " : "" ) + "this.serviceClient.%1$s(%2$s);", clientMethod.getName(),
              clientMethod.getArgumentList());
    });
  }

  protected void generateJavadoc(ClientMethod clientMethod, JavaType typeBlock, ProxyMethod restAPIMethod) {
    typeBlock.javadocComment(comment -> {
      comment.description(clientMethod.getDescription());
      List<ClientMethodParameter> methodParameters = clientMethod.getOnlyRequiredParameters()
          ? clientMethod.getMethodRequiredParameters()
          : clientMethod.getMethodParameters();
      for (ClientMethodParameter parameter : methodParameters) {
        comment.param(parameter.getName(), parameter.getDescription());
      }
      if (clientMethod.getParametersDeclaration() != null && !clientMethod.getParametersDeclaration().isEmpty()) {
        comment.methodThrows("IllegalArgumentException", "thrown if parameters fail the validation");
      }
      if (restAPIMethod.getUnexpectedResponseExceptionType() != null) {
        comment.methodThrows(restAPIMethod.getUnexpectedResponseExceptionType().toString(),
            "thrown if the request is rejected by server");
      }
      comment.methodThrows("RuntimeException", "all other wrapped checked exceptions if the request fails to be sent");
      comment.methodReturns(clientMethod.getReturnValue().getDescription());
    });
  }


}
