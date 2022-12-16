/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.multiapi.converter.asyncapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.sngular.multiapi.converter.MultiApiContractConverter;
import com.sngular.multiapi.converter.util.FileHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Header;

class AsyncApiContractConverterTest {

  private final MultiApiContractConverter multiApiContractConverter = new MultiApiContractConverter();

  private final AsyncApiContractConverterTestFixtures asyncApiContractConverterTestFixtures = new AsyncApiContractConverterTestFixtures();

  @Test
  @DisplayName("AsyncApi: Testing the method that checks if the yaml is correct")
  void fileIsAcceptedTrue() {
    File file = FileHelper.getFile(asyncApiContractConverterTestFixtures.EVENT_API_FILE);
    Boolean isAccepted = multiApiContractConverter.isAccepted(file);
    assertThat(isAccepted).isTrue();
  }

  @Test
  @DisplayName("AsyncApi: Check if a contract is returned")
  void convertFromTest() {
    var contractList = getContracts(asyncApiContractConverterTestFixtures.EVENT_API_FILE);

    assertThat(contractList).hasSize(2);

    Contract publishContract = contractList.get(0);
    assertThat(publishContract.getInput()).isNotNull();
    assertThat(publishContract.getOutputMessage()).isNotNull();
    assertThat(publishContract.getName()).isInstanceOf(String.class).isEqualTo(asyncApiContractConverterTestFixtures.PUBLISH_NAME);
    assertThat(publishContract.getLabel()).isEqualTo(asyncApiContractConverterTestFixtures.PUBLISH_NAME);

    Contract subscribeContract = contractList.get(1);
    assertThat(subscribeContract.getInput()).isNotNull();
    assertThat(subscribeContract.getOutputMessage()).isNull();
    assertThat(subscribeContract.getName()).isInstanceOf(String.class).isEqualTo(asyncApiContractConverterTestFixtures.SUBSCRIBE_NAME);
    assertThat(subscribeContract.getLabel()).isEqualTo(asyncApiContractConverterTestFixtures.SUBSCRIBE_NAME);
  }

  @Test
  @DisplayName("AsyncApi: Check if Input is being processed okay")
  void testInput() {
    var contractList = getContracts(asyncApiContractConverterTestFixtures.EVENT_API_FILE);
    Map<String, Object> order = asyncApiContractConverterTestFixtures.createOrder();

    Contract publishContract = contractList.get(0);
    assertThat(publishContract.getInput().getTriggeredBy().getExecutionCommand()).isEqualTo(asyncApiContractConverterTestFixtures.TRIGGERED_BY);

    Contract subscribeContract = contractList.get(1);
    assertThat(subscribeContract.getInput().getMessageFrom().getClientValue()).isEqualTo(asyncApiContractConverterTestFixtures.MESSAGE_FROM);
    assertThat(subscribeContract.getInput().getMessageBody().getClientValue()).isNotNull().isEqualTo(order);
    Header inputHeader = subscribeContract.getInput().getMessageHeaders().getEntries().stream().iterator().next();
    assertThat(inputHeader.getName()).isEqualTo(asyncApiContractConverterTestFixtures.HEADER_NAME);
    assertThat(inputHeader.getClientValue()).isEqualTo(asyncApiContractConverterTestFixtures.HEADER_VALUE);
  }

  @Test
  @DisplayName("AsyncApi: Check if OutputMessage is being processed okay")
  void testOutputMessage() {
    var contractList = getContracts(asyncApiContractConverterTestFixtures.EVENT_API_FILE);
    Map<String, Object> order = asyncApiContractConverterTestFixtures.createOrder();

    Contract publishContract = contractList.get(0);
    assertThat(publishContract.getOutputMessage().getSentTo().getClientValue()).isEqualTo(asyncApiContractConverterTestFixtures.PUBLISH_SEND_TO);
    Map<String, Object> bodyValue = (Map<String, Object>) publishContract.getOutputMessage().getBody().getClientValue();
    assertThat(bodyValue.get(asyncApiContractConverterTestFixtures.ORDER)).isNotNull();
    Map<String, Object> orderMap = (Map<String, Object>) bodyValue.get(asyncApiContractConverterTestFixtures.ORDER);
    assertThat(orderMap.get(asyncApiContractConverterTestFixtures.INT_ARRAY)).isNotNull().isInstanceOf(ArrayList.class);
    List<Integer> intArray = (List<Integer>) orderMap.get(asyncApiContractConverterTestFixtures.INT_ARRAY);
    assertThat(intArray.get(0)).isNotNull().isInstanceOf(Integer.class);
    assertThat(publishContract.getOutputMessage().getBodyMatchers().matchers().get(0).path()).isEqualTo(asyncApiContractConverterTestFixtures.INT_ARRAY_BODY_MATCHER);

    Contract subscribeContract = contractList.get(1);
    assertThat(subscribeContract.getInput().getMessageFrom().getClientValue()).isEqualTo(asyncApiContractConverterTestFixtures.MESSAGE_FROM);
    Header outputHeader = subscribeContract.getInput().getMessageHeaders().getEntries().iterator().next();
    assertThat(outputHeader.getName()).isEqualTo(asyncApiContractConverterTestFixtures.HEADER_NAME);
    assertThat(outputHeader.getClientValue()).isEqualTo(asyncApiContractConverterTestFixtures.HEADER_VALUE);
    assertThat(subscribeContract.getInput().getMessageBody().getClientValue()).isNotNull().isEqualTo(order);
  }

  @Test
  @DisplayName("AsyncApi: Check if the enum logic is being processed okay")
  void testEnums() {
    var contractList = getContracts(asyncApiContractConverterTestFixtures.TEST_ENUMS_FILE);

    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);
      Map<String, Object> bodyValue;

      if (Objects.nonNull(contract.getOutputMessage())) {
        bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
      } else {
        bodyValue = (Map<String, Object>) contract.getInput().getMessageBody().getClientValue();
      }
      Map<String, Object> orderValue = (Map<String, Object>) bodyValue.get(asyncApiContractConverterTestFixtures.ORDER);
      String enumName = (String) orderValue.get(asyncApiContractConverterTestFixtures.NAME);

      if (i == 0) {
        assertThat(enumName).isInstanceOf(String.class).isIn(asyncApiContractConverterTestFixtures.ENUM_VALUES);
      } else {
        assertThat(enumName).isEqualTo(asyncApiContractConverterTestFixtures.CORUNET);
      }
    }
  }

  @Test
  @DisplayName("AsyncApi: Check if complex objects are being processed okay")
  void testComplexObjects() {
    var contractList = getContracts(asyncApiContractConverterTestFixtures.TEST_COMPLEX_OBJECTS_FILE);

    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);

      Map<String, Object> bodyValue;

      if (Objects.nonNull(contract.getOutputMessage())) {
        bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
      } else {
        bodyValue = (Map<String, Object>) contract.getInput().getMessageBody().getClientValue();
      }
      assertThat(bodyValue.get(asyncApiContractConverterTestFixtures.ORDER)).isNotNull();
      Map<String, Object> orderValue = (Map<String, Object>) bodyValue.get(asyncApiContractConverterTestFixtures.ORDER);
      assertThat(orderValue.get(asyncApiContractConverterTestFixtures.NAME)).isNotNull();
      assertThat(orderValue.get(asyncApiContractConverterTestFixtures.ADDRESS)).isNotNull();
      Map<String, Object> nameValue = (Map<String, Object>) orderValue.get(asyncApiContractConverterTestFixtures.NAME);
      Map<String, Object> addressValue = (Map<String, Object>) orderValue.get(asyncApiContractConverterTestFixtures.ADDRESS);

      if (i == 0) {
        assertThat(nameValue.get(asyncApiContractConverterTestFixtures.COMPANY_NAME)).isNotNull().isInstanceOf(String.class);
        assertThat(nameValue.get(asyncApiContractConverterTestFixtures.REFERENCE_NAME)).isNotNull().isInstanceOf(Integer.class);
        assertThat(addressValue.get(asyncApiContractConverterTestFixtures.STREET)).isNotNull().isInstanceOf(String.class);
      } else {
        assertThat(nameValue).containsEntry(asyncApiContractConverterTestFixtures.COMPANY_NAME, asyncApiContractConverterTestFixtures.CORUNET);
        assertThat(nameValue).containsEntry(asyncApiContractConverterTestFixtures.REFERENCE_NAME, 3324);
        assertThat(addressValue).containsEntry(asyncApiContractConverterTestFixtures.STREET, asyncApiContractConverterTestFixtures.STREET_VALUE);
      }
    }
  }

  @Test
  @DisplayName("AsyncApi: Check if arrays are being processed okay")
  void testArrays() {
    var contractList = getContracts(asyncApiContractConverterTestFixtures.TEST_ARRAYS_FILE);

    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);

      Map<String, Object> bodyValue;

      if (Objects.nonNull(contract.getOutputMessage())) {
        bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
      } else {
        bodyValue = (Map<String, Object>) contract.getInput().getMessageBody().getClientValue();
      }
      assertThat(bodyValue.get(asyncApiContractConverterTestFixtures.ORDER)).isNotNull();
      Map<String, Object> orderValue = (Map<String, Object>) bodyValue.get(asyncApiContractConverterTestFixtures.ORDER);
      assertThat(orderValue.get(asyncApiContractConverterTestFixtures.NAME)).isNotNull();
      assertThat(orderValue.get(asyncApiContractConverterTestFixtures.AMOUNT)).isNotNull();
      List<Object> amountListValue = (ArrayList<Object>) orderValue.get(asyncApiContractConverterTestFixtures.AMOUNT);
      List<Object> nameListValue = (ArrayList<Object>) orderValue.get(asyncApiContractConverterTestFixtures.NAME);
      assertThat(nameListValue.get(0)).isNotNull();
      Map<String, Object> nameValue = (Map<String, Object>) nameListValue.get(0);
      assertThat(nameValue.get(asyncApiContractConverterTestFixtures.COMPANY_NAME)).isNotNull();

      if (i == 0) {
        assertThat(amountListValue.get(0)).isNotNull().isInstanceOf(Double.class);
        assertThat(nameValue.get(asyncApiContractConverterTestFixtures.COMPANY_NAME)).isNotNull().isInstanceOf(String.class);
      } else {
        assertThat(amountListValue).isEqualTo(asyncApiContractConverterTestFixtures.DOUBLE_ARRAY_VALUES);
        assertThat(nameValue).containsEntry(asyncApiContractConverterTestFixtures.COMPANY_NAME, asyncApiContractConverterTestFixtures.CORUNET);
      }
    }
  }

  @Test
  @DisplayName("AsyncApi: Check if arrays with reference to an object are being processed okay")
  void testArraysWithRef() {
    var contractList = getContracts(asyncApiContractConverterTestFixtures.TEST_ARRAYS_REF_FILE);
    Map<String, Object> bodyValue;
    Map<String, Object> orderValue;
    List<Object> employeeListValue;
    Map<String, Object> nameValue;
    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);

      if (Objects.nonNull(contract.getOutputMessage())) {
        bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
      } else {
         bodyValue = (Map<String, Object>) contract.getInput().getMessageBody().getClientValue();
      }
      assertThat(bodyValue.get(asyncApiContractConverterTestFixtures.ORDER)).isNotNull();
      orderValue = (Map<String, Object>) bodyValue.get(asyncApiContractConverterTestFixtures.ORDER);
      assertThat(orderValue.get(asyncApiContractConverterTestFixtures.EMPLOYEES)).isNotNull();
      employeeListValue = (ArrayList<Object>) orderValue.get(asyncApiContractConverterTestFixtures.EMPLOYEES);
      assertThat(employeeListValue.get(0)).isNotNull();
      nameValue = (Map<String, Object>) employeeListValue.get(0);

      if (i == 0) {
        assertThat(nameValue.get(asyncApiContractConverterTestFixtures.NAME)).isNotNull().isInstanceOf(String.class);
      } else {
        assertThat(nameValue).containsEntry(asyncApiContractConverterTestFixtures.NAME, asyncApiContractConverterTestFixtures.PERSON_NAME);
      }
    }
  }


  @Test
  @DisplayName("AsyncApi: Check if basic types are being processed okay")
  void testBasicTypes() {
    var contractList = getContracts(asyncApiContractConverterTestFixtures.TEST_BASIC_TYPES_FILE);

    Map<String, Object> bodyValue;
    Map<String, Object> orderValue;

    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);

      if (Objects.nonNull(contract.getOutputMessage())) {
        bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
      } else {
        bodyValue = (Map<String, Object>) contract.getInput().getMessageBody().getClientValue();
      }
      assertThat(bodyValue.get(asyncApiContractConverterTestFixtures.ORDER)).isNotNull();
      orderValue = (Map<String, Object>) bodyValue.get(asyncApiContractConverterTestFixtures.ORDER);
      if (i == 0) {
        assertThat(orderValue.get(asyncApiContractConverterTestFixtures.INTEGER_TYPE)).isNotNull().isInstanceOf(Integer.class);
        assertThat(orderValue.get(asyncApiContractConverterTestFixtures.INTEGER_TYPE_2)).isNotNull().isInstanceOf(Integer.class);
        assertThat(orderValue.get(asyncApiContractConverterTestFixtures.FLOAT_TYPE)).isNotNull().isInstanceOf(Float.class);
        assertThat(orderValue.get(asyncApiContractConverterTestFixtures.FLOAT_TYPE_2)).isNotNull().isInstanceOf(Float.class);
        assertThat(orderValue.get(asyncApiContractConverterTestFixtures.DOUBLE_TYPE)).isNotNull().isInstanceOf(Double.class);
        assertThat(orderValue.get(asyncApiContractConverterTestFixtures.STRING_TYPE)).isNotNull().isInstanceOf(String.class);
        assertThat(orderValue.get(asyncApiContractConverterTestFixtures.BOOLEAN_TYPE)).isNotNull().isInstanceOf(Boolean.class);
      } else {
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.INTEGER_TYPE, 3);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.INTEGER_TYPE_2, 10);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.FLOAT_TYPE, 3.5f);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.FLOAT_TYPE_2, 2.9f);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.DOUBLE_TYPE, 100.55);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.STRING_TYPE, asyncApiContractConverterTestFixtures.CORUNET);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.BOOLEAN_TYPE, true);
      }
    }
  }

  @Test
  @DisplayName("AsyncApi: Check if references to external files work correctly")
  void testExternalFiles() {
    var contractList = getContracts(asyncApiContractConverterTestFixtures.TEST_EXTERNAL_FILE);

    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);

      Map<String, Object> bodyValue;
      Map<String, Object> orderValue;
      List<Integer> amount;
      if (Objects.nonNull(contract.getOutputMessage())) {
        bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
      } else {
        bodyValue = (Map<String, Object>) contract.getInput().getMessageBody().getClientValue();
      }
      assertThat(bodyValue.get(asyncApiContractConverterTestFixtures.ORDER)).isNotNull();
      orderValue = (Map<String, Object>) bodyValue.get(asyncApiContractConverterTestFixtures.ORDER);
      amount = (ArrayList<Integer>) orderValue.get(asyncApiContractConverterTestFixtures.AMOUNT);

      if (i == 0) {
        assertThat(orderValue.get(asyncApiContractConverterTestFixtures.COMPANY_NAME)).isNotNull().isInstanceOf(String.class);
        assertThat(orderValue.get(asyncApiContractConverterTestFixtures.IS_SENT)).isNotNull().isInstanceOf(Boolean.class);
        assertThat(amount).isNotNull().isInstanceOf(ArrayList.class);
      } else {
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.COMPANY_NAME, asyncApiContractConverterTestFixtures.CORUNET);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.IS_SENT, true);
        assertThat(amount).isEqualTo(asyncApiContractConverterTestFixtures.INT_ARRAY_VALUES);
      }
    }
  }

  @Test
  @DisplayName("AsyncApi: Check if references to external files work correctly with multiple schemas")
  void testExternalFilesWithMultipleSchemas() {
    var contractList = getContracts(asyncApiContractConverterTestFixtures.TEST_EXTERNAL_FILE_MULTIPLE_SCHEMAS);

    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);

      Map<String, Object> bodyValue;

      if (Objects.nonNull(contract.getOutputMessage())) {
        bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
      } else {
        bodyValue = (Map<String, Object>) contract.getInput().getMessageBody().getClientValue();
      }
      assertThat(bodyValue.get(asyncApiContractConverterTestFixtures.ORDERS)).isNotNull();
      Map<String, Object> ordersValue = (Map<String, Object>) bodyValue.get(asyncApiContractConverterTestFixtures.ORDERS);
      assertThat(ordersValue.get(asyncApiContractConverterTestFixtures.ORDER_LINE)).isNotNull();
      Map<String, Object> orderLineValue = (Map<String, Object>) ordersValue.get(asyncApiContractConverterTestFixtures.ORDER_LINE);
      assertThat(orderLineValue.get(asyncApiContractConverterTestFixtures.ORDER)).isNotNull();
      Map<String, Object> orderValue = (Map<String, Object>) orderLineValue.get(asyncApiContractConverterTestFixtures.ORDER);

      if (i == 0) {
        assertThat(orderValue.get(asyncApiContractConverterTestFixtures.COMPANY_NAME)).isNotNull().isInstanceOf(String.class);
      } else {
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.COMPANY_NAME, asyncApiContractConverterTestFixtures.CORUNET);
      }
    }
  }

  private List<Contract> getContracts(String filePath) {
    final File file = FileHelper.getFile(filePath);
    return new ArrayList<>(multiApiContractConverter.convertFrom(file));
  }

}
