package com.sngular.multiapi.converter.openapi.model;

import io.swagger.v3.oas.models.Operation;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@Builder
@RequiredArgsConstructor
public class ConverterPathItem {

  OperationType operationType;

  Operation operation;

}
