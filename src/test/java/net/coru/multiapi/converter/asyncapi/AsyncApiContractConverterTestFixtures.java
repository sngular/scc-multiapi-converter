/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.multiapi.converter.asyncapi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsyncApiContractConverterTestFixtures {

  protected final String EVENT_API_FILE = "src/test/resources/asyncapi/event-api.yml";

  protected final String TEST_BASIC_TYPES_FILE = "src/test/resources/asyncapi/testBasicTypes.yml";

  protected final String TEST_COMPLEX_OBJECTS_FILE = "src/test/resources/asyncapi/testComplexObjects.yml";

  protected final String TEST_ARRAYS_FILE = "src/test/resources/asyncapi/testArrays.yml";

  protected final String TEST_ARRAYS_REF_FILE = "src/test/resources/asyncapi/testArraysWithRef.yml";

  protected final String TEST_ENUMS_FILE = "src/test/resources/asyncapi/testEnums.yml";

  protected final String TEST_EXTERNAL_FILE = "src/test/resources/asyncapi/testExternalFiles.yml";

  protected final String TEST_EXTERNAL_FILE_MULTIPLE_SCHEMAS = "src/test/resources/asyncapi/testExternalFilesWithMultipleSchemas.yml";

  protected final String PUBLISH_NAME = "publishOperation";

  protected final String TRIGGERED_BY = "publishOperation()";

  protected final String PUBLISH_SEND_TO = "orderCreated";

  protected final String SUBSCRIBE_NAME = "subscribeOperation";

  protected final String MESSAGE_FROM = "createOrder";

  protected final String INT_ARRAY_BODY_MATCHER = "order.intArray[0]";

  protected final String HEADER_NAME = "Accept";

  protected final String HEADER_VALUE = "application/json";

  protected final String ORDER = "order";

  protected final String ORDERS = "orders";

  protected final String ORDER_LINE = "orderLine";

  protected final String EMPLOYEES = "employees";

  protected final String INT_ARRAY = "intArray";

  protected final String NAME = "name";

  protected final String CORUNET = "Corunet";

  protected final String PERSON_NAME = "Carlos";

  protected final String ADDRESS = "address";

  protected final String COMPANY_NAME = "companyName";

  protected final String REFERENCE_NAME = "referenceName";

  protected final String STREET = "street";

  protected final String STREET_VALUE = "Calle Sor Joaquina";

  protected final String AMOUNT = "amount";

  protected final String INTEGER_TYPE = "integerType";

  protected final String INTEGER_TYPE_2 = "integerType2";

  protected final String FLOAT_TYPE = "floatType";

  protected final String FLOAT_TYPE_2 = "floatType2";

  protected final String DOUBLE_TYPE = "doubleType";

  protected final String STRING_TYPE = "stringType";

  protected final String BOOLEAN_TYPE = "booleanType";

  protected final String IS_SENT = "isSent";

  protected final List<Integer> INT_ARRAY_VALUES = List.of(1, 2, 3);

  protected final List<Double> DOUBLE_ARRAY_VALUES = List.of(1.25, 2.5, 3.75);

  protected final String[] ENUM_VALUES = {"Corunet", "Sngular"};

  protected final Map<String, Object> createOrder() {
    Map<String, Object> order = new HashMap<>();
    Map<String, Object> array = new HashMap<>();
    array.put(INT_ARRAY, INT_ARRAY_VALUES);
    order.put(ORDER, array);

    return order;
  }

}
