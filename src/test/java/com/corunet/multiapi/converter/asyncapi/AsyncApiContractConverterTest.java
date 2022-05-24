package com.corunet.multiapi.converter.asyncapi;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.corunet.multiapi.converter.MultiApiContractConverter;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.contract.spec.Contract;

class AsyncApiContractConverterTest {

  private final MultiApiContractConverter multiApiContractConverter = new MultiApiContractConverter();

  private final AsyncApiContractConverterTestFixtures asyncApiContractConverterTestFixtures = new AsyncApiContractConverterTestFixtures();

  @Test
  void fileIsAcceptedTrue() {
    File file = new File("src/test/resources/event-api.yml");
    Boolean isAccepted = multiApiContractConverter.isAccepted(file);
    assertThat(isAccepted).isTrue();
  }

  @Test
  void convertFromSubscribeTest() {
    Collection<Contract> contracts;
    File file = new File("src/test/resources/event-api.yml");
    contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);

    assertThat(contractList.size()).isEqualTo(2);

    assertThat(contractList.get(0).getName()).isEqualTo(asyncApiContractConverterTestFixtures.PUBLISH_NAME);
    assertThat(contractList.get(0).getInput().getTriggeredBy().getExecutionCommand()).isEqualTo(asyncApiContractConverterTestFixtures.TRIGGERED_BY);
    assertThat(contractList.get(0).getOutputMessage().getSentTo().getClientValue()).isEqualTo(asyncApiContractConverterTestFixtures.SEND_TO);
    assertThat(contractList.get(0).getOutputMessage().getBody().getClientValue()).isNotNull();

    assertThat(contractList.get(1).getName()).isEqualTo(asyncApiContractConverterTestFixtures.SUBSCRIBE_NAME);
    assertThat(contractList.get(1).getInput().getMessageFrom().getClientValue()).isEqualTo(asyncApiContractConverterTestFixtures.MESSAGE_FROM);
    assertThat(contractList.get(1).getInput().getMessageBody()).isNotNull();
  }

}
