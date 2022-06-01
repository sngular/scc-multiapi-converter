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

  public final String EVENT_API_FILE = "src/test/resources/asyncapi/event-api.yml";

  public final String TEST_BASIC_TYPES_FILE = "src/test/resources/asyncapi/testBasicTypes.yml";

  public final String TEST_COMPLEX_OBJECTS_FILE = "src/test/resources/asyncapi/testComplexObjects.yml";

  public final String TEST_ARRAYS_FILE = "src/test/resources/asyncapi/testArrays.yml";

  public final String TEST_ENUMS_FILE = "src/test/resources/asyncapi/testEnums.yml";

  public final String PUBLISH_NAME = "publishOperation";

  public final String TRIGGERED_BY = "publishOperation()";

  public final String PUBLISH_SEND_TO = "orderCreated";

  public final String SUBSCRIBE_NAME = "subscribeOperation";

  public final String MESSAGE_FROM = "createOrder";

  public final String INT_ARRAY_BODY_MATCHER = "order.intArray[0]";

  public final String HEADER_NAME = "Accept";

  public final String HEADER_VALUE = "application/json";

  public final String ORDER = "order";

  public final String INT_ARRAY = "intArray";

  public final String NAME = "name";

  public final String CORUNET = "Corunet";

  public final String ADDRESS = "address";

  public final String COMPANY_NAME = "companyName";

  public final String REFERENCE_NAME = "referenceName";

  public final String STREET = "street";

  public final String AMOUNT = "amount";

  public final String INTEGER_TYPE = "integerType";

  public final String INTEGER_TYPE_2 = "integerType2";

  public final String FLOAT_TYPE = "floatType";

  public final String FLOAT_TYPE_2 = "floatType2";

  public final String DOUBLE_TYPE = "doubleType";

  public final String STRING_TYPE = "stringType";

  public final String BOOLEAN_TYPE = "booleanType";

  public final List<Integer> INT_ARRAY_VALUES = List.of(1, 2, 3);

  public final List<Double> DOUBLE_ARRAY_VALUES = List.of(1.25, 2.5, 3.75);

  public final String[] ENUM_VALUES = {"Corunet", "Sngular"};

  public final Map<String, Object> createOrder() {
    Map<String, Object> order = new HashMap<>();
    Map<String, Object> array = new HashMap<>();
    array.put(INT_ARRAY, INT_ARRAY_VALUES);
    order.put(ORDER, array);

    return order;
  }

}
