/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.multiapi.converter.asyncapi;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsyncApiContractConverterTestFixtures {

  protected final static String EVENT_API_FILE = "/asyncapi/event-api.yml";

  protected final static String TEST_BASIC_TYPES_FILE = "/asyncapi/testBasicTypes.yml";

  protected final static String TEST_COMPLEX_OBJECTS_FILE = "/asyncapi/testComplexObjects.yml";

  protected final static String TEST_ARRAYS_FILE = "/asyncapi/testArrays.yml";

  protected final static String TEST_ARRAYS_REF_FILE = "/asyncapi/testArraysWithRef.yml";

  protected final static String TEST_ENUMS_FILE = "/asyncapi/testEnums.yml";

  protected final static String TEST_EXTERNAL_FILE = "/asyncapi/testExternalFiles.yml";

  protected final static String TEST_EXTERNAL_FILE_MULTIPLE_SCHEMAS = "/asyncapi/testExternalFilesWithMultipleSchemas.yml";

  protected final static String PUBLISH_NAME = "publishOperation";

  protected final static String TRIGGERED_BY = "publishOperation()";

  protected final static String PUBLISH_SEND_TO = "orderCreated";

  protected final static String SUBSCRIBE_NAME = "subscribeOperation";

  protected final static String MESSAGE_FROM = "createOrder";

  protected final static String INT_ARRAY_BODY_MATCHER = "order.intArray[0]";

  protected final static String HEADER_NAME = "Accept";

  protected final static String HEADER_VALUE = "application/json";

  protected final static String ORDER = "order";

  protected final static String ORDERS = "orders";

  protected final static String ORDER_LINE = "orderLine";

  protected final static String EMPLOYEES = "employees";

  protected final static String INT_ARRAY = "intArray";

  protected final static String NAME = "name";

  protected final static String CORUNET = "Corunet";

  protected final static String PERSON_NAME = "Carlos";

  protected final static String ADDRESS = "address";

  protected final static String COMPANY_NAME = "companyName";

  protected final static String REFERENCE_NAME = "referenceName";

  protected final static String STREET = "street";

  protected final static String STREET_VALUE = "Calle Sor Joaquina";

  protected final static String AMOUNT = "amount";

  protected final static String INTEGER_TYPE = "integerType";

  protected final static String INTEGER_TYPE_2 = "integerType2";

  protected final static String FLOAT_TYPE = "floatType";

  protected final static String FLOAT_TYPE_2 = "floatType2";

  protected final static String DOUBLE_TYPE = "doubleType";

  protected final static String STRING_TYPE = "stringType";

  protected final static String BOOLEAN_TYPE = "booleanType";

  protected final static String IS_SENT = "isSent";

  protected final List<Integer> INT_ARRAY_VALUES = List.of(1, 2, 3);

  protected final List<Double> DOUBLE_ARRAY_VALUES = List.of(1.25, 2.5, 3.75);

  final static String[] ENUM_VALUES = {"Corunet", "Sngular"};

  protected final Map<String, Object> createOrder() {
    Map<String, Object> order = new HashMap<>();
    Map<String, Object> array = new HashMap<>();
    array.put(INT_ARRAY, INT_ARRAY_VALUES);
    order.put(ORDER, array);

    return order;
  }

}
