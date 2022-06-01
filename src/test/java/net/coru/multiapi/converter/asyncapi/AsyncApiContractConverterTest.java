/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.multiapi.converter.asyncapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.coru.multiapi.converter.MultiApiContractConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Header;

class AsyncApiContractConverterTest {

  private final MultiApiContractConverter multiApiContractConverter = new MultiApiContractConverter();

  private final AsyncApiContractConverterTestFixtures asyncApiContractConverterTestFixtures = new AsyncApiContractConverterTestFixtures();

  @Test
  void fileIsAcceptedTrue() {
    File file = new File(asyncApiContractConverterTestFixtures.EVENT_API_FILE);
    Boolean isAccepted = multiApiContractConverter.isAccepted(file);
    assertThat(isAccepted).isTrue();
  }

  @Test
  @DisplayName("AsyncApi: Check if a contract is returned")
  void convertFromTest() {
    Collection<Contract> contracts;
    File file = new File(asyncApiContractConverterTestFixtures.EVENT_API_FILE);
    contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);

    assertThat(contractList).hasSize(2);

    Contract publishContract = contractList.get(0);
    assertThat(publishContract.getInput()).isNotNull();
    assertThat(publishContract.getOutputMessage()).isNotNull();
    assertThat(publishContract.getName()).isInstanceOf(String.class).isEqualTo(asyncApiContractConverterTestFixtures.PUBLISH_NAME);
    assertThat(publishContract.getLabel()).isEqualTo(asyncApiContractConverterTestFixtures.PUBLISH_NAME);

    Contract subscribeContract = contractList.get(1);
    assertThat(subscribeContract.getInput()).isNotNull();
    assertThat(subscribeContract.getOutputMessage()).isNotNull();
    assertThat(subscribeContract.getName()).isInstanceOf(String.class).isEqualTo(asyncApiContractConverterTestFixtures.SUBSCRIBE_NAME);
    assertThat(subscribeContract.getLabel()).isEqualTo(asyncApiContractConverterTestFixtures.SUBSCRIBE_NAME);
  }

  @Test
  @DisplayName("AsyncApi: Check if Input is being processed okay")
  void testInput() {
    File file = new File(asyncApiContractConverterTestFixtures.EVENT_API_FILE);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    ArrayList<Contract> contractList = new ArrayList<>(contracts);
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
    File file = new File(asyncApiContractConverterTestFixtures.EVENT_API_FILE);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    ArrayList<Contract> contractList = new ArrayList<>(contracts);
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
    assertThat(subscribeContract.getOutputMessage().getSentTo().getClientValue()).isEqualTo(asyncApiContractConverterTestFixtures.MESSAGE_FROM);
    Header outputHeader = subscribeContract.getOutputMessage().getHeaders().getEntries().stream().iterator().next();
    assertThat(outputHeader.getName()).isEqualTo(asyncApiContractConverterTestFixtures.HEADER_NAME);
    assertThat(outputHeader.getClientValue()).isEqualTo(asyncApiContractConverterTestFixtures.HEADER_VALUE);
    assertThat(subscribeContract.getOutputMessage().getBody().getClientValue()).isNotNull().isEqualTo(order);
  }

  @Test
  @DisplayName("AsyncApi: Check if the enum logic is being processed okay")
  void testEnums() {
    File file = new File(asyncApiContractConverterTestFixtures.TEST_ENUMS_FILE);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    ArrayList<Contract> contractList = new ArrayList<>(contracts);

    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);

      Map<String, Object> bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
      assertThat(bodyValue.get(asyncApiContractConverterTestFixtures.ORDER)).isNotNull();
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
    File file = new File(asyncApiContractConverterTestFixtures.TEST_COMPLEX_OBJECTS_FILE);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    ArrayList<Contract> contractList = new ArrayList<>(contracts);

    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);

      Map<String, Object> bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
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
        assertThat(addressValue).containsEntry(asyncApiContractConverterTestFixtures.STREET, "Calle Sor Joaquina");
      }
    }
  }

  @Test
  @DisplayName("AsyncApi: Check if arrays are being processed okay")
  void testArrays() {
    File file = new File(asyncApiContractConverterTestFixtures.TEST_ARRAYS_FILE);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    ArrayList<Contract> contractList = new ArrayList<>(contracts);

    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);

      Map<String, Object> bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
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
  @DisplayName("AsyncApi: Check if basic types are being processed okay")
  void testBasicTypes() {
    File file = new File(asyncApiContractConverterTestFixtures.TEST_BASIC_TYPES_FILE);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    ArrayList<Contract> contractList = new ArrayList<>(contracts);

    for (int i = 0; i < contractList.size(); i++) {
      Contract contract = contractList.get(i);

      Map<String, Object> bodyValue = (Map<String, Object>) contract.getOutputMessage().getBody().getClientValue();
      assertThat(bodyValue.get(asyncApiContractConverterTestFixtures.ORDER)).isNotNull();
      Map<String, Object> orderValue = (Map<String, Object>) bodyValue.get(asyncApiContractConverterTestFixtures.ORDER);

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
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.FLOAT_TYPE, 3.5);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.FLOAT_TYPE_2, 2.9);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.DOUBLE_TYPE, 100.55);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.STRING_TYPE, asyncApiContractConverterTestFixtures.CORUNET);
        assertThat(orderValue).containsEntry(asyncApiContractConverterTestFixtures.BOOLEAN_TYPE, true);
      }
    }

  }

}
